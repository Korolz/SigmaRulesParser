package me.korolz.sigmarules;

import com.fasterxml.jackson.dataformat.yaml.UTF8Reader;
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
    public static String yaml = "";
    public static int failCount = 0;
    public static int allCount = 0;

    public static void main(String[] args) throws IOException, InvalidSigmaRuleException, SigmaRuleParserException, InterruptedException {
//        yaml = getSigmaRuleFromFile("ps.yml");
//        yaml = getSigmaRuleFromFile("notCondition.yml");
//        yaml = getSigmaRuleFromFile("simpleCondition.yml");
//        yaml = getSigmaRuleFromFile("simpleCondition2.yml");
        yaml = getSigmaRuleFromFile("hardRule.yml");


//        try (Stream<Path> paths = Files.walk(Paths.get("sigmaRulesForTest"))) {
//            List<String> filePaths = paths.map(Path::toString).collect(Collectors.toList());
//            filePaths = filePaths.stream().filter(s -> s.endsWith("yml")).collect(Collectors.toList());
//            allCount = filePaths.size();
//            Iterator<String> iterator = filePaths.iterator();
//            while (iterator.hasNext()) {
//                String path = iterator.next();
//                yaml = getSigmaRuleFromFile(path);
//                try {
//                    startParseYamlToOpenSearchQuery(yaml);
//                } catch (Exception e) {
//                    failCount++;
//                    System.out.println(path);
//                }
//
//            }
//        }
//        System.out.println(failCount);
//        System.out.println(allCount);

        System.out.println(startParseYamlToOpenSearchQuery(yaml));
        System.out.println(getOneQueryFromSigmaRuleWithSigConverter(yaml));
    }

    public static String startParseYamlToOpenSearchQuery(String yaml) throws IOException, InvalidSigmaRuleException, SigmaRuleParserException {
        SigmaRuleParser ruleParser = new SigmaRuleParser();
        SigmaRule sigmaRule = ruleParser.parseRule(yaml);

        // Получаем строку condition непосредственно из Yaml-файла
        String condition = getConditionLineFromYaml(yaml);
//        System.out.println(condition);

        // Получаем все Conditions и Detections
        List<SigmaCondition> sigmaConditions = sigmaRule.getConditionsManager().getConditions();
        DetectionsManager detectionsManager = sigmaRule.getDetectionsManager();

//      Вычленяем имена Detection из строки Condition
        List<String> conditionNames = new ArrayList<>();
        for (SigmaCondition sigmaCondition : sigmaConditions) {
            recursiveInspectConditionNames(sigmaCondition, conditionNames);
        }
//        System.out.println(conditionNames);
//        List<String> conditionNames = detectionsManager.getAllDetections().keySet().stream().collect(Collectors.toList());

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
                } else if (condition.contains("and")){
                    conditionResult.append(detectionsNames.get(i) + " OR ");
                }
            } else {
                conditionResult.append(detectionsNames.get(i));
            }
        }
//        System.out.println(conditionResult);
        return conditionResult.toString();
    }

    public static String getHardConditionLine(String result, List<String> conditionNames, DetectionsManager detectionsManager) {
        List<String> detectionName = detectionsManager.getAllDetections().keySet().stream().collect(Collectors.toList());
//        System.out.println(detectionName);
        Map<String, String> keyValue = new HashMap<>();
        for (String currentDetectionName : detectionName) {
            for (String conditionName : conditionNames) {
                if (!conditionName.equals("of")) {
                    if (currentDetectionName.contains(conditionName.replace("_*", ""))) {
                        StringBuilder keyValueByDetectionName = new StringBuilder();
                        List<SigmaDetection> detections = detectionsManager.getDetectionsByName(currentDetectionName).getDetections();
                        for (SigmaDetection d : detections) {
                            String currentValue = d.getValues().toString();
                            if (isListForHardRules(yaml, currentDetectionName) && !d.getMatchAll()) {
                                currentValue = currentValue.replaceAll(", ", " OR ");
                            } else {
                                currentValue = currentValue.replaceAll(", ", " AND ");
                            }
//                            System.out.println(currentValue);
                            if (isListForHardRules(yaml, currentDetectionName)) {
                                if (d.getMatchAll()) {
                                    keyValueByDetectionName.append(d.getName() + ":" + currentValue + " AND ");
                                } else {
                                    keyValueByDetectionName.append(d.getName() + ":" + currentValue + " OR ");
                                }
                            } else {
                                keyValueByDetectionName.append(d.getName() + ":" + currentValue + " AND ");
                            }
                        }
                        keyValueByDetectionName = new StringBuilder(deleteEnding(keyValueByDetectionName.toString()));
                        keyValueByDetectionName = new StringBuilder(keyValueByDetectionName.toString().replaceAll("\\\\\\.\\*", "\\\\\\\\.*"));
                        if (!keyValue.keySet().contains(conditionName)) {
                            keyValue.put(conditionName, keyValueByDetectionName.toString());
                        } else {
                            keyValueByDetectionName.append(" OR " + keyValue.get(conditionName));
                            keyValue.put(conditionName, keyValueByDetectionName.toString());
                        }
                    }
                } else {
                    result = result.replaceAll("1 of ", "");
                    result = result.replaceAll("all of ", "");
                }
            }
        }
        for (String k : keyValue.keySet()) {
            result = result.replaceAll(k, "(" + keyValue.get(k) + ")");
        }
        result = result.replaceAll("\\\\\\.\\*", "\\\\\\\\.*");

        if (result.contains(", "))  result = result.replaceAll(", ", " OR ");
        if (result.contains("[")) result = result.replaceAll("\\[", "(");
        if (result.contains("]")) result = result.replaceAll("]", ")");
        result = result.replaceAll("\\)" + "\\*", "))");
        result = result.replace("not", "(NOT");

        return result;
    }


    public static String getQueryFromSimpleSigmaRule(String result, List<String> conditionNames, DetectionsManager detectionsManager) {
        final String defaultResult = result;
        Iterator<String> namesIterator = conditionNames.iterator();
        String valueResult = "";
        while (namesIterator.hasNext()) {
            String currentDetectionName = namesIterator.next();
            List<SigmaDetection> detections;
            try {
                detections = detectionsManager.getDetectionsByName(currentDetectionName).getDetections();
            } catch (NullPointerException e) {
//                throw new NullPointerException("A rule named \"" + currentDetectionName + "\" cannot be processed");
                result = defaultResult;
//                return result;
                return getHardConditionLine(result, conditionNames, detectionsManager);
            }
            StringBuilder keyValueByDetectionName = new StringBuilder();
            for (SigmaDetection d : detections) {
                if (isList(yaml, currentDetectionName)) {
                    keyValueByDetectionName.append(d.getName() + ":" + d.getValues() + " OR ");
                } else {
                    keyValueByDetectionName.append(d.getName() + ":" + d.getValues() + " AND ");
                }
//                valueResult = keyValueByDetectionName.toString().replaceAll("\\\\\\.", ".");
                valueResult = keyValueByDetectionName.toString().replaceAll("\\\\\\.\\*", "\\\\\\\\.*");



                if (d.getMatchAll()) {
                    valueResult = deleteEnding(valueResult);
                    if (valueResult.contains(", "))  valueResult = valueResult.replaceAll(", ", "\" AND " + d.getName() + ":");
                    if (valueResult.contains("[")) valueResult = valueResult.replaceAll("\\[", "\"");
                    if (valueResult.contains("]")) valueResult = valueResult.replaceAll("]", "\"");
                    if (valueResult.endsWith("\"")) valueResult = new StringBuilder(valueResult).delete(valueResult.length() - 1, valueResult.length()).toString();
                } else {
                    valueResult = deleteEnding(valueResult);
                    if (valueResult.contains(", "))  valueResult = valueResult.replaceAll(", ", " OR ");
                    if (valueResult.contains("[")) valueResult = valueResult.replaceAll("\\[", "(");
                    if (valueResult.contains("]")) valueResult = valueResult.replaceAll("]", ")");
                }
                if (valueResult.contains(":()")) valueResult = valueResult.replaceAll(":\\(\\)", "");
            }

            if (result.contains("not")) {
                result = replaceNot(result);
            }

//            System.out.println(valueResult);
            result = result.replaceAll(currentDetectionName, "(" + valueResult + ")");
        }

        return result;
    }

    public static String deleteEnding(String value) {
        if (value.endsWith(" AND ")) value = new StringBuilder(value).delete(value.length() - 5, value.length()).toString();
        if (value.endsWith(" OR ")) value = new StringBuilder(value).delete(value.length() - 4, value.length()).toString();
        return value;
    }

    public static String replaceNot(String value) {
        value = value.replace("not", "(NOT");
        return value + ")";
    }



    public static String replaceOneOf(String value) {
        value = value.replace("1 of", "(");
        return value + ")";
    }

    public static String getConditionLineFromYaml(String yaml) {
        String condition = "";
        try (BufferedReader br = new BufferedReader(new StringReader(yaml))) {
            Iterator<String> iterator = br.lines().iterator();
            while (iterator.hasNext()) {
                String currentLine = iterator.next();
                if (currentLine.contains("condition")) condition = currentLine.replace("condition: ", "").trim();
            }
            condition = condition.replaceAll(" and ", " AND ");
            condition = condition.replaceAll(" or ", " OR ");
            return condition;
        } catch (IOException e) {
//            System.out.println(e.getMessage());
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
//            System.out.println(e.getMessage());
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
//                System.out.println(response.body());

                try (BufferedWriter bw = new BufferedWriter(new FileWriter("result.txt", true))){
                    bw.append(id + ": " + response.body() + "\n");
                } catch (IOException e) {

                }
                Thread.sleep(1000);
            }
        }
    }

    private static boolean isList(String yaml, String detectionName) {

        try (BufferedReader br = (new BufferedReader(new StringReader(yaml)))) {
            Iterator<String> iterator = br.lines().iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.contains(detectionName)) {
                    String nextLine = iterator.next().trim();
                   if (nextLine.startsWith("- ")) return true;
                }
            }
        } catch (IOException e) {
//            System.out.println(e.getMessage());
        }
        return false;
    }

    private static boolean isListForHardRules(String yaml, String detectionName) {

        try (BufferedReader br = (new BufferedReader(new StringReader(yaml)))) {
            Iterator<String> iterator = br.lines().iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.contains(detectionName)) {
                    iterator.next();
                    String nextLine = iterator.next().trim();
//                    System.out.println(nextLine);
                    if (nextLine.startsWith("- ")) return true;
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return false;
    }
}