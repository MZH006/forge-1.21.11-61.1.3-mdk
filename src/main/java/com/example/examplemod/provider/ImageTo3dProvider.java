package com.example.examplemod.provider;

/**
 * Converts an image URL into a downloadable GLB (binary glTF) 3D model URL.
 * Implement this interface to support different image-to-3D APIs
 * (e.g. fal.ai SAM-3D, Tripo AI, Meshy, Replicate InstantMesh).
 */
public interface ImageTo3dProvider {

    /**
     * Takes an image URL and returns a URL to a GLB file.
     * This is a blocking call — run it off the main thread.
     *
     * @param imageUrl publicly accessible URL of the source image
     * @param prompt   original text prompt (some APIs use it as a hint)
     * @return a URL pointing to the generated GLB file
     * @throws Exception if generation fails (network error, bad API key, etc.)
     */
    String generateGlbUrl(String imageUrl, String prompt) throws Exception;
}
