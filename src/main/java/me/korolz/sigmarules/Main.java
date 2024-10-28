package me.korolz.sigmarules;

import me.korolz.sigmarules.exceptions.InvalidSigmaRuleException;
import me.korolz.sigmarules.exceptions.SigmaRuleParserException;
import me.korolz.sigmarules.models.SigmaCondition;
import me.korolz.sigmarules.models.SigmaRule;
import me.korolz.sigmarules.parsers.SigmaRuleParser;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception, InvalidSigmaRuleException, SigmaRuleParserException {
        StringBuilder newYaml = new StringBuilder();
        try (BufferedReader br = (new BufferedReader(new InputStreamReader(new FileInputStream("notCondition.yml"))))) {
            Iterator iterator = br.lines().iterator();
            while (iterator.hasNext()) {
                newYaml.append(iterator.next());
                newYaml.append("\n");
            }
        }
        String encoded = Base64.getEncoder().encodeToString(newYaml.toString().getBytes());
        String jsonInputString = String.format(
                "{\"rule\":\"%s\",\"pipelineYml\":\"\",\"pipeline\":[],\"target\":\"opensearch\",\"format\":\"default\"}",
                encoded
        );

        System.out.println(jsonInputString);
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://sigconverter.io/sigma"))
                .POST(HttpRequest.BodyPublishers.ofString(jsonInputString))
                .header("Content-Type", "application/json")
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        String responseBody = response.body().replaceAll("\\*",".*");
        System.out.println(responseBody);

        SigmaRuleParser ruleParser = new SigmaRuleParser();
        SigmaRule sigmaRule = ruleParser.parseRule(newYaml.toString());

        System.out.println(sigmaRule);
        sigmaRule.getDetectionsManager().getAllDetections().forEach((key, value) -> {
            System.out.println(key);
            System.out.println(value.getDetections().toString());
        }
        );
        List<SigmaCondition> cond = sigmaRule.getConditionsManager().getConditions();
        cond.forEach(System.out::println);
        cond.forEach(c -> System.out.println(recursiveInspect(c)));

    }

    public static String recursiveInspect(SigmaCondition condition){
        StringBuilder sb = new StringBuilder();
        if(condition.getPairedCondition() != null){
            return recursiveInspect(condition.getPairedCondition());
        }
        else if (condition.getAggregateCondition()){
            sb.append(condition.getAggregateValues().getGroupBy());
            sb.append("\n");
            sb.append(condition.getAggregateValues().getOperation());
            sb.append("\n");
            sb.append(condition.getAggregateValues().getOperationValue());
            sb.append("\n");
        } else {
            sb.append(condition.getConditionName());
            sb.append("\n");
            sb.append(condition.getOperator());
            sb.append("\n");
        }
        return sb.toString();
    }
}