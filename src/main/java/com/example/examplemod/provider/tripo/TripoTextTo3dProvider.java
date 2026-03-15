package com.example.examplemod.provider.tripo;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Tripo AI text-to-3D provider.
 * Goes directly from a text prompt to a GLB, skipping the image generation step entirely.
 * Costs 10 credits per generation (free tier gives 300/month).
 *
 * This does NOT implement TextToImageProvider or ImageTo3dProvider — it owns the full
 * text→GLB pipeline in one call. Use {@link TripoGenerationPipeline} instead of
 * the standard {@link com.example.examplemod.pipeline.GenerationPipeline}.
 */
public class TripoTextTo3dProvider {

    private static final String TASK_URL = "https://api.tripo3d.ai/v2/openapi/task";

    private static final int POLL_INTERVAL_MS = 4000;
    private static final int MAX_POLLS        = 150; // 10 minutes max

    private final String apiKey;
    private final HttpClient http;

    public TripoTextTo3dProvider(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * Generates a GLB from a text prompt. Blocking — call off the main thread.
     *
     * @param prompt text description of the desired 3D model
     * @return URL to a downloadable GLB file
     */
    public String generateGlbUrl(String prompt) throws Exception {
        System.out.println("[Tripo] Submitting text-to-3D task: " + prompt);

        String taskId = submitTask(prompt);
        System.out.println("[Tripo] Task created: " + taskId + " — polling...");

        return pollUntilDone(taskId);
    }

    private String submitTask(String prompt) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("type", "text_to_model");
        body.addProperty("prompt", prompt);
        // model_version omitted — let Tripo use its default

        HttpResponse<String> response = post(TASK_URL, body.toString());
        checkStatus(response);

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        return json.getAsJsonObject("data").get("task_id").getAsString();
    }

    private String pollUntilDone(String taskId) throws Exception {
        String pollUrl = TASK_URL + "/" + taskId;

        for (int i = 0; i < MAX_POLLS; i++) {
            Thread.sleep(POLL_INTERVAL_MS);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(pollUrl))
                    .timeout(Duration.ofSeconds(30))
                    .header("Authorization", "Bearer " + apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            checkStatus(response);

            JsonObject data = JsonParser.parseString(response.body())
                    .getAsJsonObject().getAsJsonObject("data");

            String status = data.get("status").getAsString();
            System.out.println("[Tripo] Status: " + status + " (poll " + (i + 1) + "/" + MAX_POLLS + ")");

            switch (status) {
                case "success" -> {
                    String glbUrl = extractGlbUrl(data);
                    System.out.println("[Tripo] GLB ready: " + glbUrl);
                    return glbUrl;
                }
                case "failed" -> throw new RuntimeException("Tripo task failed. Full response: " + response.body());
                // "queued" / "running" / "processing" — keep polling
            }
        }

        throw new RuntimeException("Tripo task timed out after " + (MAX_POLLS * POLL_INTERVAL_MS / 1000) + "s.");
    }

    private HttpResponse<String> post(String url, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Extracts the GLB URL from the Tripo response.
     * Actual shape: data.result.pbr_model.url (with data.output.pbr_model as a flat string fallback)
     */
    private String extractGlbUrl(JsonObject data) {
        // Primary: data.result.pbr_model.url
        if (data.has("result") && !data.get("result").isJsonNull()) {
            JsonObject result = data.getAsJsonObject("result");
            if (result.has("pbr_model") && !result.get("pbr_model").isJsonNull()) {
                return result.getAsJsonObject("pbr_model").get("url").getAsString();
            }
        }
        // Fallback: data.output.pbr_model (flat string URL)
        if (data.has("output") && !data.get("output").isJsonNull()) {
            JsonObject output = data.getAsJsonObject("output");
            if (output.has("pbr_model") && output.get("pbr_model").isJsonPrimitive()) {
                return output.get("pbr_model").getAsString();
            }
        }
        throw new RuntimeException("Could not find GLB URL in Tripo response. Check logs for full response.");
    }

    private void checkStatus(HttpResponse<String> response) {
        switch (response.statusCode()) {
            case 200, 201 -> { /* ok */ }
            case 401 -> throw new RuntimeException("Invalid Tripo API key. Use /generate settripokey to update it.");
            case 429 -> throw new RuntimeException("Tripo rate limit hit — free tier allows 1 concurrent task and 300 credits/month.");
            default  -> throw new RuntimeException("Tripo API error " + response.statusCode() + ": " + response.body());
        }
    }
}
