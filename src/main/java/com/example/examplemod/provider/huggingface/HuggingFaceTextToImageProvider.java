package com.example.examplemod.provider.huggingface;

import com.example.examplemod.provider.TextToImageProvider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Base64;

/**
 * Hugging Face Inference Endpoint implementation of {@link TextToImageProvider}.
 * Uses a dedicated Flux-1 endpoint that returns raw PNG bytes.
 *
 * The endpoint returns raw PNG bytes which are encoded as a base64 data URL
 * (data:image/png;base64,...) so downstream providers like Replicate can consume
 * the image without needing a publicly hosted URL.
 */
public class HuggingFaceTextToImageProvider implements TextToImageProvider {

    private static final String ENDPOINT = "https://jjx1c75qu4j1zt5s.us-east-1.aws.endpoints.huggingface.cloud";

    private final String apiKey;
    private final HttpClient http;

    /**
     * @param apiKey Hugging Face API token (Bearer token). Pass null or empty
     *               string if the endpoint is public (no auth required).
     */
    public HuggingFaceTextToImageProvider(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String generateImageUrl(String prompt) throws Exception {
        String body = "{\"inputs\":\"" + escapeJson(prompt +
                ", single isolated object, pure white background, centered, no shadows, studio product photography")
                + "\",\"parameters\":{}}";

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofMinutes(3))
                .header("Accept", "image/png")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body));

        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Hugging Face endpoint error " + response.statusCode()
                    + ": " + new String(response.body()));
        }

        // Encode as a base64 data URL — Replicate accepts this directly as the "image" field.
        String dataUrl = "data:image/png;base64," + Base64.getEncoder().encodeToString(response.body());
        System.out.println("[HF] Image generated, encoded as base64 data URL (" + response.body().length + " bytes)");
        return dataUrl;
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
    }
}
