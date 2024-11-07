package me.korolz.sigmarules.query;

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
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class QueryBuilder {
    public static final String ONE_OF_SELECTION_1 = "1 of selection_*";
    public static final String ONE_OF_SELECTION_2 = "1 of selection*";
    public static final String ALL_OF_SELECTION_1 = "all of selection_*";
    public static final String ALL_OF_SELECTION_2 = "all of selection*";
    public static String yamlPath = "";

    public static SigmaRuleParser ruleParser;

    public QueryBuilder() {ruleParser = new SigmaRuleParser();}

    public String buildQuery(String yamlSource) throws IOException, InvalidSigmaRuleException, SigmaRuleParserException, InterruptedException, URISyntaxException {
        yamlPath = yamlSource;
        SigmaRule sigmaRule;
        try {
            sigmaRule = ruleParser.parseRule(yamlSource);
        } catch (RuntimeException e) {
            return this.getOneQueryFromSigmaRuleWithSigConverter(yamlPath);
        }

        // Получаем строку condition непосредственно из String Yaml
        String condition = getConditionLineFromYaml(yamlSource);


        // Получаем все Conditions и Detections
        List<SigmaCondition> sigmaConditions = sigmaRule.getConditionsManager().getConditions();
        DetectionsManager detectionsManager = sigmaRule.getDetectionsManager();

        //      Вычленяем имена Detection из строки Condition
        List<String> conditionNames = new ArrayList<>();
        for (SigmaCondition sigmaCondition : sigmaConditions) {
            recursiveInspectConditionNames(sigmaCondition, conditionNames);
        }

        if (condition.equals(ONE_OF_SELECTION_1) || condition.equals(ALL_OF_SELECTION_1) ||
                condition.equals(ONE_OF_SELECTION_2) || condition.equals(ALL_OF_SELECTION_2)) {
            conditionNames = List.copyOf(detectionsManager.getAllDetections().keySet());
            condition = getConditionLine(condition, conditionNames);
        }

        return getQueryFromSimpleSigmaRule(condition, conditionNames, detectionsManager);
    }

    public String getQueryFromSimpleSigmaRule(String result, List<String> conditionNames, DetectionsManager detectionsManager) {
        final String defaultResult = result;
        Iterator<String> namesIterator = conditionNames.iterator();
        String valueResult = "";
        while (namesIterator.hasNext()) {
            String currentDetectionName = namesIterator.next();
            List<SigmaDetection> detections;
            try {
                detections = detectionsManager.getDetectionsByName(currentDetectionName).getDetections();
            } catch (NullPointerException e) {
                result = defaultResult;
                return getHardConditionLine(result, conditionNames, detectionsManager);
            }
            StringBuilder keyValueByDetectionName = new StringBuilder();
            for (SigmaDetection d : detections) {
                if (isList(yamlPath, currentDetectionName)) {
                    keyValueByDetectionName.append(d.getName() + ":" + d.getValues() + " OR ");
                } else {
                    keyValueByDetectionName.append(d.getName() + ":" + d.getValues() + " AND ");
                }
                valueResult = keyValueByDetectionName.toString();
                valueResult = valueResult.replaceAll("\\\\\\.\\*", "\\\\\\\\.*");

                if (d.getMatchAll()) {
                    valueResult = valueFormat(valueResult);
                    if (valueResult.contains(", "))  valueResult = valueResult.replaceAll(", ", " AND " + d.getName() + ":");
                    if (valueResult.endsWith("\"")) valueResult = new StringBuilder(valueResult).delete(valueResult.length() - 1, valueResult.length()).toString();
                } else {
                    valueResult = valueFormat(valueResult);
                    if (valueResult.contains(", "))  valueResult = valueResult.replaceAll(", ", " OR ");
                }
                if (valueResult.contains(":()")) valueResult = valueResult.replaceAll(":\\(\\)", "");
            }

            if (result.contains("not")) {
                result = replaceNot(result);
            }
            result = result.replaceAll(currentDetectionName, "(" + valueResult + ")");
        }
        return result;
    }

    public String getHardConditionLine(String result, List<String> conditionNames, DetectionsManager detectionsManager) {
        List<String> detectionName = detectionsManager.getAllDetections().keySet().stream().collect(Collectors.toList());
        Map<String, String> keyValue = new HashMap<>();
        for (String currentDetectionName : detectionName) {
            for (String conditionName : conditionNames) {
                if (!conditionName.equals("of")) {
                    if (currentDetectionName.contains(conditionName.replace("_*", ""))) {
                        StringBuilder keyValueByDetectionName = new StringBuilder();
                        List<SigmaDetection> detections = detectionsManager.getDetectionsByName(currentDetectionName).getDetections();
                        for (SigmaDetection d : detections) {
                            String currentValue = d.getValues().toString();
                            if (isListForHardRules(yamlPath, currentDetectionName) && !d.getMatchAll()) {
                                currentValue = currentValue.replaceAll(", ", " OR ");
                            } else {
                                currentValue = currentValue.replaceAll(", ", " AND ");
                            }
                            if (isListForHardRules(yamlPath, currentDetectionName)) {
                                if (d.getMatchAll()) {
                                    keyValueByDetectionName.append(d.getName() + ":" + currentValue + " AND ");
                                } else {
                                    keyValueByDetectionName.append(d.getName() + ":" + currentValue + " OR ");
                                }
                            } else {
                                keyValueByDetectionName.append(d.getName() + ":" + currentValue + " AND ");
                            }
                        }
                        keyValueByDetectionName = new StringBuilder(valueFormat(keyValueByDetectionName.toString()));
                        keyValueByDetectionName = new StringBuilder(keyValueByDetectionName.toString().replaceAll("\\\\\\.\\*", ".*"));
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
        return resultFormat(result);
    }

    public String valueFormat(String value) {
        if (value.endsWith(" AND ")) value = new StringBuilder(value).delete(value.length() - 5, value.length()).toString();
        if (value.endsWith(" OR ")) value = new StringBuilder(value).delete(value.length() - 4, value.length()).toString();
        if (value.contains("[")) value = value.replaceAll("\\[", "(");
        if (value.contains("]")) value = value.replaceAll("]", ")");
        if (value.contains(":()")) value = value.replaceAll(":\\(\\)", "");
        return value;
    }

    public String resultFormat(String result) {
        result = result.replaceAll("\\\\\\.\\*", "\\\\\\\\.*");
        if (result.contains(", "))  result = result.replaceAll(", ", " OR ");
        result = result.replaceAll("\\)" + "\\*", "))");
        return result.replace("not", "(NOT");
    }

    public String replaceNot(String value) {
        value = value.replace("not", "(NOT");
        return value + ")";
    }

    public String getConditionLine(String condition, List<String> detectionsNames) {
        StringBuilder conditionResult = new StringBuilder();
        for (int i = 0; i < detectionsNames.size(); i++) {
            if (i + 1 < detectionsNames.size()) {
                if (condition.contains("all")) {
                    conditionResult
                            .append(detectionsNames.get(i))
                            .append(" AND ");
                } else if (condition.contains("and")){
                    conditionResult
                            .append(detectionsNames.get(i))
                            .append(" OR ");
                }
            } else {
                conditionResult.append(detectionsNames.get(i));
            }
        }
        return conditionResult.toString();
    }

    public String getConditionLineFromYaml(String yaml) {
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
        }
        return condition;
    }

    public List<String> recursiveInspectConditionNames(SigmaCondition condition, List<String> conditionNames){
        conditionNames.add(condition.getConditionName());
        if(condition.getPairedCondition() != null){
            return recursiveInspectConditionNames(condition.getPairedCondition(), conditionNames);
        } else {
            return conditionNames;
        }
    }

    public List<String> recursiveInspectOperatorsNames(SigmaCondition condition, List<String> operatorNames) {
        if (condition.getPairedCondition() != null) {
            operatorNames.add(condition.getOperator());
            return recursiveInspectOperatorsNames(condition.getPairedCondition(), operatorNames);
        } else if (condition.getNotCondition()) {
            operatorNames.add("(NOT");
        }
        return operatorNames;
    }

    public String getSigmaRuleFromFile(String file) throws IOException, InvalidSigmaRuleException, SigmaRuleParserException {
        StringBuilder newYaml = new StringBuilder();
        try (BufferedReader br = (new BufferedReader(new InputStreamReader(new FileInputStream(file))))) {
            Iterator<String> iterator = br.lines().iterator();
            while (iterator.hasNext()) {
                newYaml.append(iterator.next());
                newYaml.append("\n");
            }
        } catch (IOException e) {
        }
        return newYaml.toString();
    }

    public String getOneQueryFromSigmaRuleWithSigConverter(String yaml) throws IOException, InterruptedException, URISyntaxException {
        String response = QueryHelper.getQueryFromPost(yaml);
        return response.replaceAll("\\*",".*");
    }

    private boolean isList(String yaml, String detectionName) {

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

    private boolean isListForHardRules(String yaml, String detectionName) {

        try (BufferedReader br = (new BufferedReader(new StringReader(yaml)))) {
            Iterator<String> iterator = br.lines().iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.contains(detectionName)) {
                    iterator.next();
                    String nextLine = iterator.next().trim();
                    if (nextLine.startsWith("- ")) return true;
                }
            }
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
        return false;
    }

    private void writeAllQueryFromFile() throws IOException, InterruptedException, URISyntaxException {
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

                String response = QueryHelper.getQueryFromPost(newYaml.toString());

                try (BufferedWriter bw = new BufferedWriter(new FileWriter("result.txt", true))){
                    bw.append(id + ": " + response + "\n");
                } catch (IOException e) {

                }
                Thread.sleep(1000);
            }
        }
    }
}
