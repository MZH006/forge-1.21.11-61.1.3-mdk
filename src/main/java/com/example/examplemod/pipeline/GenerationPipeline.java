package com.example.examplemod.pipeline;

import com.example.examplemod.fal.BlockMapper;
import com.example.examplemod.fal.GlbParser;
import com.example.examplemod.fal.Voxelizer;
import com.example.examplemod.provider.ImageTo3dProvider;
import com.example.examplemod.provider.ModelDownloader;
import com.example.examplemod.provider.TextToImageProvider;

import java.util.List;

/**
 * Orchestrates the full text-to-voxels pipeline.
 *
 * Steps:
 *   1. TextToImageProvider  — prompt -> image URL
 *   2. ImageTo3dProvider    — image URL -> GLB URL
 *   3. ModelDownloader      — GLB URL -> raw bytes
 *   4. GlbParser            — bytes -> triangles
 *   5. Voxelizer            — triangles -> voxels
 *
 * Swap any provider without touching this class or ModCommands.
 */
public class GenerationPipeline {

    private final TextToImageProvider imageProvider;
    private final ImageTo3dProvider modelProvider;
    private final ModelDownloader downloader;
    private final GlbParser glbParser;
    private final Voxelizer voxelizer;

    public GenerationPipeline(TextToImageProvider imageProvider, ImageTo3dProvider modelProvider) {
        this.imageProvider = imageProvider;
        this.modelProvider = modelProvider;
        this.downloader = new ModelDownloader();
        this.glbParser = new GlbParser();
        this.voxelizer = new Voxelizer();
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
    public List<Voxelizer.Voxel> run(String prompt, int gridSize, ProgressListener listener) throws Exception {
        listener.onProgress(1, 4, "Generating image from prompt...");
        String imageUrl = imageProvider.generateImageUrl(prompt);

        listener.onProgress(2, 4, "Converting image to 3D model...");
        String glbUrl = modelProvider.generateGlbUrl(imageUrl, prompt);

        listener.onProgress(3, 4, "Downloading and voxelizing model...");
        byte[] glbBytes = downloader.download(glbUrl);
        List<GlbParser.Triangle> triangles = glbParser.parse(glbBytes);
        List<Voxelizer.Voxel> voxels = voxelizer.voxelize(triangles, gridSize);

        if (voxels.isEmpty()) {
            throw new RuntimeException("Voxelization produced no blocks. Try a different prompt.");
        }

        listener.onProgress(4, 4, "Placing " + voxels.size() + " blocks...");
        return voxels;
    }

    /**
     * Receives progress updates during the pipeline run.
     */
    @FunctionalInterface
    public interface ProgressListener {
        void onProgress(int step, int totalSteps, String message);
    }
}
