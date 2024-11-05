package me.korolz.sigmarules;

import me.korolz.sigmarules.exceptions.InvalidSigmaRuleException;
import me.korolz.sigmarules.exceptions.SigmaRuleParserException;
import me.korolz.sigmarules.query.QueryBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Main {
    public static String yaml = "";
    public static int failCount = 0;
    public static int allCount = 0;

    public static void main(String[] args) throws IOException, InvalidSigmaRuleException, SigmaRuleParserException, InterruptedException {
        QueryBuilder queryBuilder = new QueryBuilder();

//        yaml = queryBuilder.getSigmaRuleFromFile("ps.yml");
//        yaml = queryBuilder.getSigmaRuleFromFile("notCondition.yml");
//        yaml = queryBuilder.getSigmaRuleFromFile("simpleCondition.yml");
//        yaml = queryBuilder.getSigmaRuleFromFile("simpleCondition2.yml");
//        yaml = queryBuilder.getSigmaRuleFromFile("hardRule.yml");

//        System.out.println(queryBuilder.buildQuery(yaml));
//        System.out.println(queryBuilder.getOneQueryFromSigmaRuleWithSigConverter(yaml));


        try (Stream<Path> paths = Files.walk(Paths.get("sigmaRulesForTest"))) {
            List<String> filePaths = paths.map(Path::toString).collect(Collectors.toList());
            filePaths = filePaths.stream().filter(s -> s.endsWith("yml")).collect(Collectors.toList());
            allCount = filePaths.size();
            Iterator<String> iterator = filePaths.iterator();
            while (iterator.hasNext()) {
                String path = iterator.next();
                yaml = queryBuilder.getSigmaRuleFromFile(path);
                try {
                    queryBuilder.buildQuery(yaml);
                } catch (Exception e) {
                    failCount++;
                    System.out.println(path);
                }
            }
        }
        System.out.println(failCount);
        System.out.println(allCount);
    }
}