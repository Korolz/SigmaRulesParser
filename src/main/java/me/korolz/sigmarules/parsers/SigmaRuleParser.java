/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */

package me.korolz.sigmarules.parsers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.korolz.sigmarules.exceptions.InvalidSigmaRuleException;
import me.korolz.sigmarules.exceptions.SigmaRuleParserException;
import me.korolz.sigmarules.fieldmapping.FieldMapper;
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

    public SigmaRuleParser(FieldMapper fieldMapperFile) {
        detectionParser = new DetectionParser(fieldMapperFile);
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
