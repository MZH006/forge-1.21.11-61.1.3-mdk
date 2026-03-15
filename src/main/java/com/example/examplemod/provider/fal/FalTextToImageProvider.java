package com.example.examplemod.provider.fal;

import com.example.examplemod.provider.TextToImageProvider;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * fal.ai implementation of {@link TextToImageProvider}.
 * Uses the Flux/schnell model to generate images from text prompts.
 *
 * To swap to a different provider, implement {@link TextToImageProvider}
 * and pass it to {@link com.example.examplemod.pipeline.GenerationPipeline}.
 */
public class FalTextToImageProvider implements TextToImageProvider {

    private static final String IMAGE_API = "https://fal.run/fal-ai/flux/schnell";

    private final HttpClient http;
    private final String apiKey;

    public FalTextToImageProvider(String apiKey) {
        this.apiKey = apiKey;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    @Override
    public String generateImageUrl(String prompt) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("prompt", prompt + ", single isolated object, pure white background, centered, no shadows, studio product photography");
        body.addProperty("num_images", 1);
        body.addProperty("image_size", "square_hd");
        body.addProperty("num_inference_steps", 4);

        String response = post(IMAGE_API, body.toString());
        JsonObject json = JsonParser.parseString(response).getAsJsonObject();
        JsonArray images = json.getAsJsonArray("images");

        if (images == null || images.size() == 0) {
            throw new RuntimeException("fal.ai Flux returned no images. Response: " + response);
        }

        String imageUrl = images.get(0).getAsJsonObject().get("url").getAsString();
        System.out.println("[FAL] Generated image URL: " + imageUrl);
        return imageUrl;
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
