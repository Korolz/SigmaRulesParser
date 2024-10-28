package me.korolz.sigmarules.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class SigmaFields {
    private Map<String, Object> fieldmappings;

    public Map<String, Object> getFieldmappings() {
        return fieldmappings;
    }

    public void setFieldmappings(Map<String, Object> fieldmappings) {
        this.fieldmappings = fieldmappings;
    }

    public List<String> getSigmaField(String fieldName) {
        List<String> sigmaFields = new ArrayList<>();

        if (fieldmappings.containsKey(fieldName)) {
            Object fieldValue = fieldmappings.get(fieldName);
            if (fieldValue instanceof ArrayList) {
                List<Object> fieldList = (ArrayList)fieldValue;
                fieldList.forEach((f) -> {
                    sigmaFields.add(f.toString());
                });
            } else {
                sigmaFields.add(fieldValue.toString());
            }
        }

        return sigmaFields;
    }
}