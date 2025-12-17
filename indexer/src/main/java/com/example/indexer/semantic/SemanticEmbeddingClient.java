package com.example.indexer.semantic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.util.List;

public class SemanticEmbeddingClient {

    private static final String OPENAI_URL =
            "https://api.openai.com/v1/embeddings";

    private final OkHttpClient client = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final String apiKey;

    public SemanticEmbeddingClient(String apiKey) {
        this.apiKey = apiKey;
    }

    public float[] embed(String text) throws Exception {
        String body = """
            {
              "model": "text-embedding-3-small",
              "input": %s
            }
            """.formatted(mapper.writeValueAsString(text));

        Request request = new Request.Builder()
                .url(OPENAI_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .post(RequestBody.create(body, MediaType.parse("application/json")))
                .build();

        try (Response response = client.newCall(request).execute()) {
            JsonNode root = mapper.readTree(response.body().string());
            JsonNode arr = root.get("data").get(0).get("embedding");

            float[] vec = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                vec[i] = arr.get(i).floatValue();
            }
            return vec;
        }
    }
}

