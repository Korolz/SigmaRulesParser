package me.korolz.sigmarules.parsers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.korolz.sigmarules.exceptions.InvalidSigmaRuleException;
import me.korolz.sigmarules.exceptions.SigmaRuleParserException;
import me.korolz.sigmarules.models.SigmaRule;
import java.io.IOException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SigmaRuleParser {
    final static Logger logger = LogManager.getLogger(SigmaRuleParser.class);
    ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    private DetectionParser detectionParser;
    private ConditionParser conditionParser;

    public SigmaRuleParser() {
        detectionParser = new DetectionParser();
        conditionParser = new ConditionParser();
    }

    public SigmaRule parseRule(String rule)
        throws IOException, InvalidSigmaRuleException, SigmaRuleParserException {
        ParsedSigmaRule parsedSigmaRule = yamlMapper.readValue(rule, ParsedSigmaRule.class);

        return parseRule(parsedSigmaRule);
    }

    public SigmaRule parseRule(ParsedSigmaRule parsedSigmaRule)
        throws InvalidSigmaRuleException, SigmaRuleParserException {
        SigmaRule sigmaRule = new SigmaRule();
        sigmaRule.copyParsedSigmaRule(parsedSigmaRule);

        sigmaRule.setDetection(detectionParser.parseDetections(parsedSigmaRule));
        sigmaRule.setConditionsManager(conditionParser.parseCondition(parsedSigmaRule));

        return sigmaRule;
    }


}
