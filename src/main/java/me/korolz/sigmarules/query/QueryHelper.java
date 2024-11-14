package me.korolz.sigmarules.query;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

public class QueryHelper {

    private static final String uriString = "https://sigconverter.io/api/v1/1.0.4/convert";
    private static final String jsonBody =
            "{" +
                    "\"rule\":\"%s\"," +
                    "\"pipelineYml\":\"\"," +
                    "\"pipeline\":[]," +
                    "\"target\":\"opensearch_lucene\"," +
                    "\"format\":\"default\"" +
                    "}";



    public static String getQueryFromPost(String yamlBody) throws URISyntaxException, IOException, InterruptedException {
        String encodedYaml = Base64.getEncoder().encodeToString(yamlBody.getBytes());
        String finalizedJsonBody = String.format(jsonBody, encodedYaml);

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(uriString))
                .header("Content-Type", "Application/Json")
                .POST(HttpRequest.BodyPublishers.ofString(finalizedJsonBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        return response.body();
    }
}
