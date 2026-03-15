package com.example.examplemod.provider.fal;

import com.example.examplemod.provider.ImageTo3dProvider;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * fal.ai implementation of {@link ImageTo3dProvider}.
 * Uses the SAM-3D model to convert an image into a GLB 3D model.
 *
 * To swap to a different provider (e.g. Tripo AI, Meshy, Replicate InstantMesh),
 * implement {@link ImageTo3dProvider} and pass it to
 * {@link com.example.examplemod.pipeline.GenerationPipeline}.
 */
public class FalImageTo3dProvider implements ImageTo3dProvider {

    private static final String SAM3D_API = "https://fal.run/fal-ai/sam-3/3d-objects";

    private final HttpClient http;
    private final String apiKey;

    public FalImageTo3dProvider(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String generateGlbUrl(String imageUrl, String prompt) throws Exception {
        System.out.println("[FAL] Converting image to 3D: " + imageUrl);

        JsonObject body = new JsonObject();
        body.addProperty("image_url", imageUrl);
        body.addProperty("prompt", prompt);
        body.addProperty("detection_threshold", 0.1);

        String response = post(SAM3D_API, body.toString());
        System.out.println("[FAL] SAM-3D response: " + response);

        JsonObject json = JsonParser.parseString(response).getAsJsonObject();

        if (json.has("model_glb") && !json.get("model_glb").isJsonNull()) {
            JsonElement glb = json.get("model_glb");
            if (glb.isJsonPrimitive()) {
                return glb.getAsString();
            } else {
                return glb.getAsJsonObject().get("url").getAsString();
            }
        }

        throw new RuntimeException("fal.ai SAM-3D returned no GLB URL. Response: " + response);
    }

    private String post(String url, String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMinutes(3))
                .header("Content-Type", "application/json")
                .header("Authorization", "Key " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        return switch (response.statusCode()) {
            case 200 -> response.body();
            case 401 -> throw new RuntimeException("Invalid fal.ai API key. Use /generate setkey to update it.");
            case 402 -> throw new RuntimeException("fal.ai account has insufficient credits. Visit fal.ai/dashboard/billing");
            default  -> throw new RuntimeException("fal.ai API error " + response.statusCode() + ": " + response.body());
        };
    }
}
