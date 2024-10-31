package me.korolz.sigmarules;

import me.korolz.sigmarules.exceptions.InvalidSigmaRuleException;
import me.korolz.sigmarules.exceptions.SigmaRuleParserException;
import me.korolz.sigmarules.models.*;
import me.korolz.sigmarules.parsers.SigmaRuleParser;

import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static final String ONE_OF_SELECTION = "1 of selection_*";
    public static final String ALL_OF_SELECTION = "all of selection_*";
    public static void main(String[] args) throws IOException, InvalidSigmaRuleException, SigmaRuleParserException, InterruptedException {
//        String yaml = getSigmaRuleFromFile("ps.yml");
        String yaml = getSigmaRuleFromFile("notCondition.yml");
//        String yaml = getSigmaRuleFromFile("simpleCondition.yml");
//        String yaml = getSigmaRuleFromFile("simpleCondition2.yml");
        System.out.println(yaml);

        System.out.println(startParseYamlToOpenSearchQuery(yaml));
        System.out.println(getOneQueryFromSigmaRuleWithSigConverter(yaml));
    }

    public static String startParseYamlToOpenSearchQuery(String yaml) throws IOException, InvalidSigmaRuleException, SigmaRuleParserException {
        SigmaRuleParser ruleParser = new SigmaRuleParser();
        SigmaRule sigmaRule = ruleParser.parseRule(yaml);

        // Получаем строку condition непосредственно из Yaml-файла
        String condition = getConditionLineFromYaml(yaml);
        System.out.println(condition);

        // Получаем все Conditions и Detections
        List<SigmaCondition> sigmaConditions = sigmaRule.getConditionsManager().getConditions();
        DetectionsManager detectionsManager = sigmaRule.getDetectionsManager();

        // Вычленяем имена Detection из строки Condition
        List<String> conditionNames = recursiveInspectConditionNames(sigmaConditions.get(0), new ArrayList<>());

        if (condition.equals(ONE_OF_SELECTION) || condition.equals(ALL_OF_SELECTION)) {
            conditionNames = List.copyOf(detectionsManager.getAllDetections().keySet());
            condition = getConditionLine(condition, conditionNames);
        }
        return getQueryFromSimpleSigmaRule(condition, conditionNames, detectionsManager);
    }

    public static String getConditionLine(String condition, List<String> detectionsNames) {
        StringBuilder conditionResult = new StringBuilder();
        for (int i = 0; i < detectionsNames.size(); i++) {
            if (i + 1 < detectionsNames.size()) {
                if (condition.contains("all")) {
                    conditionResult.append(detectionsNames.get(i) + " AND ");
                } else {
                    conditionResult.append(detectionsNames.get(i) + " OR ");
                }
            } else {
                conditionResult.append(detectionsNames.get(i));
            }
        }
        System.out.println(conditionResult);
        return conditionResult.toString();
    }

    public static String getQueryFromSimpleSigmaRule(String result, List<String> conditionNames, DetectionsManager detectionsManager) {
        Iterator<String> namesIterator = conditionNames.iterator();
        result = result.replaceAll(" and ", " AND ");
        result = result.replaceAll(" or ", " OR ");
        String valueResult = new String();
        while (namesIterator.hasNext()) {
            StringBuilder valueByDetectionName = new StringBuilder();
            String currentDetectionName = namesIterator.next();
            List<SigmaDetection> detections = detectionsManager.getDetectionsByName(currentDetectionName).getDetections();
            System.out.println(currentDetectionName);
            for (SigmaDetection d : detections) {
                System.out.println(d.getName() + ": " + d.getValues());

                valueByDetectionName.append(d.getName() + ":" + d.getValues() + " AND ");
                valueResult = valueByDetectionName.toString().replaceAll("\\\\\\.", ".");
                valueResult = valueResult.replaceAll("\\\\\\.\\*", "\\\\\\\\.*");

                if (valueResult.contains("\\")) valueResult = valueResult.replaceAll("\\\\", Matcher.quoteReplacement("\\\\"));
                if (d.getMatchAll()) {
                    if (valueResult.endsWith(" AND ")) valueResult = new StringBuilder(valueResult).delete(valueResult.length() - 5, valueResult.length()).toString();
                    if (valueResult.contains(", "))  valueResult = valueResult.replaceAll(", ", "\" AND " + d.getName() + ":");
                    if (valueResult.contains("[")) valueResult = valueResult.replaceAll("\\[", "\"");
                    if (valueResult.contains("]")) valueResult = valueResult.replaceAll("]", "\"");
                    if (valueResult.endsWith("\"")) valueResult = new StringBuilder(valueResult).delete(valueResult.length() - 1, valueResult.length()).toString();
                } else {
                    if (valueResult.endsWith(" AND ")) valueResult = new StringBuilder(valueResult).delete(valueResult.length() - 5, valueResult.length()).toString();
                    if (valueResult.contains(", "))  valueResult = valueResult.replaceAll(", ", " OR ");
                    if (valueResult.contains("[")) valueResult = valueResult.replaceAll("\\[", "(");
                    if (valueResult.contains("]")) valueResult = valueResult.replaceAll("]", ")");
                }
            }
            result = result.replaceAll(currentDetectionName, "(" + valueResult + ")");
        }

        if (result.contains("not")) {
            result = result.replaceAll("not", "(NOT");
            result = result + ")";
        }
        if (result.contains("-")) result = result.replaceAll("-", Matcher.quoteReplacement("\\") + "-");
        if (result.contains("/")) result = result.replaceAll("/", Matcher.quoteReplacement("\\") + "/");

        return result;
    }

    public static String getConditionLineFromYaml(String yaml) {
        String condition = "";
        try (BufferedReader br = new BufferedReader(new StringReader(yaml))) {
            Iterator<String> iterator = br.lines().iterator();
            while (iterator.hasNext()) {
                String currentLine = iterator.next();
                if (currentLine.contains("condition")) condition = currentLine.replace("  condition: ", "");
            }
            return condition;
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return condition;
    }

    public static List<String> recursiveInspectConditionNames(SigmaCondition condition, List<String> conditionNames){
        conditionNames.add(condition.getConditionName());
        if(condition.getPairedCondition() != null){
            return recursiveInspectConditionNames(condition.getPairedCondition(), conditionNames);
        } else {
            return conditionNames;
        }
    }

    public static List<String> recursiveInspectOperatorsNames(SigmaCondition condition, List<String> operatorNames) {
        if (condition.getPairedCondition() != null) {
            operatorNames.add(condition.getOperator());
            return recursiveInspectOperatorsNames(condition.getPairedCondition(), operatorNames);
        } else if (condition.getNotCondition()) {
            operatorNames.add("(NOT");
        }
        return operatorNames;
    }

    private static String getSigmaRuleFromFile(String file) throws IOException, InvalidSigmaRuleException, SigmaRuleParserException {
        StringBuilder newYaml = new StringBuilder();
        try (BufferedReader br = (new BufferedReader(new InputStreamReader(new FileInputStream(file))))) {
            Iterator iterator = br.lines().iterator();
            while (iterator.hasNext()) {
                newYaml.append(iterator.next());
                newYaml.append("\n");
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return newYaml.toString();
    }

    private static String getOneQueryFromSigmaRuleWithSigConverter(String yaml) throws IOException, InterruptedException {
        String encoded = Base64.getEncoder().encodeToString(yaml.getBytes());
        String jsonInputString = String.format(
                "{\"rule\":\"%s\",\"pipelineYml\":\"\",\"pipeline\":[],\"target\":\"opensearch\",\"format\":\"default\"}",
                encoded
        );
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://sigconverter.io/sigma"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonInputString))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body().replaceAll("\\*",".*");
    }

    private static void writeAllQueryFromFile() throws IOException, InterruptedException, URISyntaxException {
        try (Stream<Path> paths = Files.walk(Paths.get("sigma_all_rules"))) {
            StringBuilder newYaml = new StringBuilder();
            List<String> filePaths = paths.map(Path::toString).collect(Collectors.toList());
            Iterator<String> iterator = filePaths.iterator();
            while (iterator.hasNext()) {
                String id = "";
                try (BufferedReader br = (new BufferedReader(new InputStreamReader(new FileInputStream(iterator.next()))))) {

                    Iterator<String> iterator2 = br.lines().iterator();
                    while (iterator2.hasNext()) {
                        String currentLine = iterator2.next();
                        if (currentLine.startsWith("id")) {
                            id = currentLine.replace("id: ", "");
                        }
                        newYaml.append(currentLine);
                        newYaml.append("\n");
                    }
                } catch (IOException e) {

                }
                String yaml = Base64.getEncoder().encodeToString(newYaml.toString().getBytes());

                String jsonInputString = "{\"rule\":\"" +  yaml + "\",\"pipelineYml\":\"\",\"pipeline\":[],\"target\":\"opensearch\",\"format\":\"default\"}";

                HttpClient httpClient = HttpClient.newHttpClient() ;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(new URI("https://sigconverter.io/sigma"))
                        .header("Content-Type", "Application/Json")
                        .POST(HttpRequest.BodyPublishers.ofString(jsonInputString))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                System.out.println(response.body());

                try (BufferedWriter bw = new BufferedWriter(new FileWriter("result.txt", true))){
                    bw.append(id + ": " + response.body() + "\n");
                } catch (IOException e) {

                }
                Thread.sleep(1000);
            }
        }
    }
}