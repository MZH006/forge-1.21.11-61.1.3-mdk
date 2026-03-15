package com.example.examplemod.provider.replicate;

import com.example.examplemod.provider.ImageTo3dProvider;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Replicate implementation of {@link ImageTo3dProvider}.
 * Uses Hunyuan3D-2.1 to convert an image into a GLB 3D model.
 *
 * Replicate uses async predictions — we submit a job, then poll until done.
 * Typical completion time: 3–5 minutes.
 */
public class ReplicateImageTo3dProvider implements ImageTo3dProvider {

    private static final String PREDICTIONS_URL = "https://api.replicate.com/v1/predictions";
    private static final String MODEL_VERSION = "895e514f953d39e8b5bfb859df9313481ad3fa3a8631e5c54c7e5c9c85a6aa9f";

    private static final int POLL_INTERVAL_MS = 3000;
    private static final int MAX_POLLS = 120; // 6 minutes max

    private final String apiKey;
    private final HttpClient http;

    public ReplicateImageTo3dProvider(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String generateGlbUrl(String imageUrl, String prompt) throws Exception {
        System.out.println("[Replicate] Submitting Hunyuan3D-2.1 job for image: " + imageUrl);

        String predictionId = submitPrediction(imageUrl);
        System.out.println("[Replicate] Prediction created: " + predictionId + " — polling for result...");

        return pollUntilDone(predictionId);
    }

    private String submitPrediction(String imageUrl) throws Exception {
        JsonObject input = new JsonObject();
        input.addProperty("image", imageUrl);
        input.addProperty("remove_background", true);
        input.addProperty("texture", false);
        input.addProperty("octree_resolution", 256);
        input.addProperty("num_inference_steps", 5);
        input.addProperty("guidance_scale", 5.0);

        JsonObject body = new JsonObject();
        body.addProperty("version", MODEL_VERSION);
        body.add("input", input);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(PREDICTIONS_URL))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Token " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            throw new RuntimeException("Invalid Replicate API key. Use /generate setkey to update it.");
        } else if (response.statusCode() != 201) {
            throw new RuntimeException("Replicate API error " + response.statusCode() + ": " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.get("id").getAsString();
    }

    private String pollUntilDone(String predictionId) throws Exception {
        String pollUrl = PREDICTIONS_URL + "/" + predictionId;

        for (int i = 0; i < MAX_POLLS; i++) {
            Thread.sleep(POLL_INTERVAL_MS);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pollUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Token " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Replicate poll error " + response.statusCode() + ": " + response.body());
            }

            JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
            String status = json.get("status").getAsString();
            System.out.println("[Replicate] Status: " + status + " (poll " + (i + 1) + "/" + MAX_POLLS + ")");

            switch (status) {
                case "succeeded" -> {
                    String glbUrl = json.getAsJsonArray("output").get(0).getAsString();
                    System.out.println("[Replicate] GLB ready: " + glbUrl);
                    return glbUrl;
                }
                case "failed", "canceled" -> {
                    String error = json.has("error") && !json.get("error").isJsonNull()
                            ? json.get("error").getAsString()
                            : "unknown error";
                    throw new RuntimeException("Replicate prediction " + status + ": " + error);
                }
                // "starting" / "processing" — keep polling
            }
        }

        throw new RuntimeException("Replicate prediction timed out after " + (MAX_POLLS * POLL_INTERVAL_MS / 1000) + "s.");
    }
}
