package com.example.examplemod.provider;

/**
 * Generates an image URL from a text prompt.
 * Implement this interface to support different text-to-image APIs
 * (e.g. fal.ai Flux, Stability AI, Replicate, OpenAI DALL-E).
 */
public interface TextToImageProvider {

    /**
     * Generates an image from the given prompt and returns a publicly accessible URL.
     * This is a blocking call — run it off the main thread.
     *
     * @param prompt the text description of the desired image
     * @return a URL pointing to the generated image
     * @throws Exception if generation fails (network error, bad API key, etc.)
     */
    String generateImageUrl(String prompt) throws Exception;
}
