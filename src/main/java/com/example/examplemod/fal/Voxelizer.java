package com.example.examplemod.fal;

import java.util.*;

/**
 * Converts a list of triangles into a 3D voxel grid.
 * Each voxel stores the average color of triangles that intersect it.
 *
 * Uses a simple triangle AABB overlap test per voxel for speed.
 * For a hackathon this is fast enough for sizes up to ~48^3.
 */
public class Voxelizer {

    public static class Voxel {
        public final int x, y, z;
        public final float r, g, b;

        public Voxel(int x, int y, int z, float r, float g, float b) {
            this.x = x; this.y = y; this.z = z;
            this.r = r; this.g = g; this.b = b;
        }
    }

    /**
     * @param triangles  List of triangles from GlbParser
     * @param gridSize   Target voxel grid size (e.g. 32 = 32x32x32)
     * @return           List of filled voxels with colors
     */
    public List<Voxel> voxelize(List<GlbParser.Triangle> triangles, int gridSize) {
        if (triangles.isEmpty()) return Collections.emptyList();


        // Find bounding box of all vertices
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE, minZ = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE, maxZ = -Float.MAX_VALUE;

        for (GlbParser.Triangle t : triangles) {
            for (float[] v : new float[][]{t.v0, t.v1, t.v2}) {
                minX = Math.min(minX, v[0]); maxX = Math.max(maxX, v[0]);
                minY = Math.min(minY, v[1]); maxY = Math.max(maxY, v[1]);
                minZ = Math.min(minZ, v[2]); maxZ = Math.max(maxZ, v[2]);
            }
        }

        float rangeX = maxX - minX;
        float rangeY = maxY - minY;
        float rangeZ = maxZ - minZ;
        float maxRange = Math.max(rangeX, Math.max(rangeY, rangeZ));
        if (maxRange == 0) return Collections.emptyList();

        float voxelSize = maxRange / gridSize;

        // Map: voxel key -> accumulated color + count
        Map<Long, float[]> voxelMap = new HashMap<>();

        for (GlbParser.Triangle tri : triangles) {
            // Get triangle AABB in voxel space
            int x0 = toVoxel(Math.min(tri.v0[0], Math.min(tri.v1[0], tri.v2[0])) - minX, voxelSize);
            int x1 = toVoxel(Math.max(tri.v0[0], Math.max(tri.v1[0], tri.v2[0])) - minX, voxelSize);
            int y0 = toVoxel(Math.min(tri.v0[1], Math.min(tri.v1[1], tri.v2[1])) - minY, voxelSize);
            int y1 = toVoxel(Math.max(tri.v0[1], Math.max(tri.v1[1], tri.v2[1])) - minY, voxelSize);
            int z0 = toVoxel(Math.min(tri.v0[2], Math.min(tri.v1[2], tri.v2[2])) - minZ, voxelSize);
            int z1 = toVoxel(Math.max(tri.v0[2], Math.max(tri.v1[2], tri.v2[2])) - minZ, voxelSize);

            // Clamp to grid
            x0 = clamp(x0, 0, gridSize - 1);
            x1 = clamp(x1, 0, gridSize - 1);
            y0 = clamp(y0, 0, gridSize - 1);
            y1 = clamp(y1, 0, gridSize - 1);
            z0 = clamp(z0, 0, gridSize - 1);
            z1 = clamp(z1, 0, gridSize - 1);

            float[] color = tri.centerColor();

            // Fill all voxels in AABB (fast but slightly overestimates - fine for hackathon)
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        long key = packKey(x, y, z);
                        float[] acc = voxelMap.computeIfAbsent(key, k -> new float[4]);
                        acc[0] += color[0];
                        acc[1] += color[1];
                        acc[2] += color[2];
                        acc[3] += 1;
                    }
                }
            }
        }

        // Convert map to voxel list with averaged colors
        List<Voxel> result = new ArrayList<>(voxelMap.size());
        for (Map.Entry<Long, float[]> entry : voxelMap.entrySet()) {
            long key = entry.getKey();
            float[] acc = entry.getValue();
            int x = (int)((key >> 40) & 0xFFFFF);
            int y = (int)((key >> 20) & 0xFFFFF);
            int z = (int)(key & 0xFFFFF);
            float count = acc[3];
            result.add(new Voxel(x, y, z, acc[0]/count, acc[1]/count, acc[2]/count));
        }

        return result;
    }

    private int toVoxel(float worldCoord, float voxelSize) {
        return (int)(worldCoord / voxelSize);
    }

    private int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }

    private long packKey(int x, int y, int z) {
        return ((long)(x & 0xFFFFF) << 40) | ((long)(y & 0xFFFFF) << 20) | (z & 0xFFFFF);
    }
}