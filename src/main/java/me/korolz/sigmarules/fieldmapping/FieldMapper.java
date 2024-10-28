package me.korolz.sigmarules.fieldmapping;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import me.korolz.sigmarules.models.SigmaFields;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FieldMapper {
    private SigmaFields sigmaFields = new SigmaFields();

    public FieldMapper(String filename) throws IOException {
        this.loadSigmaFields(filename);
    }

    public void loadSigmaFields(String filename) throws IOException {
        String fields = Files.readString(Path.of(filename));

        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        this.sigmaFields = yamlMapper.readValue(fields, SigmaFields.class);
    }

    public SigmaFields getSigmaFields() {
        return this.sigmaFields;
    }
}
