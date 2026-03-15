package com.example.examplemod.pipeline;

import com.example.examplemod.cache.PromptCache;
import com.example.examplemod.fal.GlbParser;
import com.example.examplemod.fal.Voxelizer;
import com.example.examplemod.provider.ModelDownloader;
import com.example.examplemod.provider.tripo.TripoTextTo3dProvider;

import java.util.List;

/**
 * Simplified pipeline using Tripo AI's text-to-3D endpoint.
 * Skips the image generation step entirely:
 *
 *   prompt → [cache check] → TripoTextTo3dProvider → GLB URL → download → [cache save] → parse → voxelize
 *
 * Cache lives at config/aibuilder/cache/ and matches on exact and fuzzy prompt similarity.
 * Use this instead of {@link GenerationPipeline} when a Tripo API key is available.
 */
public class TripoGenerationPipeline {

    private final TripoTextTo3dProvider tripoProvider;
    private final ModelDownloader downloader;
    private final GlbParser glbParser;
    private final Voxelizer voxelizer;
    private final PromptCache cache;

    public TripoGenerationPipeline(TripoTextTo3dProvider tripoProvider) {
        this.tripoProvider = tripoProvider;
        this.downloader = new ModelDownloader();
        this.glbParser = new GlbParser();
        this.voxelizer = new Voxelizer();
        this.cache = new PromptCache();
    }

    /**
     * Runs the full pipeline. Blocking — call off the main server thread.
     *
     * @param prompt   user's text description
     * @param gridSize target voxel grid size (8–64)
     * @param listener receives progress messages during the pipeline
     * @return list of colored voxels ready to be placed in the world
     * @throws Exception on any pipeline failure
     */
    public List<Voxelizer.Voxel> run(String prompt, int gridSize, GenerationPipeline.ProgressListener listener) throws Exception {
        byte[] glbBytes = cache.get(prompt);
        boolean fromCache = glbBytes != null;

        if (fromCache) {
            listener.onProgress(1, 2, "Cache hit! Reusing model for \"" + prompt + "\"...");
        } else {
            listener.onProgress(1, 3, "Generating 3D model from prompt (via Tripo AI)...");
            String glbUrl = tripoProvider.generateGlbUrl(prompt);

            listener.onProgress(2, 3, "Downloading model...");
            glbBytes = downloader.download(glbUrl);
            cache.put(prompt, glbBytes);
        }

        listener.onProgress(fromCache ? 2 : 3, fromCache ? 2 : 3, "Voxelizing...");

        List<GlbParser.Triangle> triangles = glbParser.parse(glbBytes);
        List<Voxelizer.Voxel> voxels = voxelizer.voxelize(triangles, gridSize);

        if (voxels.isEmpty()) {
            throw new RuntimeException("Voxelization produced no blocks. Try a different prompt.");
        }

        return voxels;
    }
}
