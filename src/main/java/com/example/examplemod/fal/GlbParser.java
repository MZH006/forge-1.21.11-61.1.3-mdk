package com.example.examplemod.fal;

import com.google.gson.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses a GLB (binary glTF) file and extracts triangles with vertex colors.
 * We implement this manually to avoid needing JglTF as a Jar-in-Jar dependency.
 *
 * GLB format:
 *   - 12-byte header (magic, version, length)
 *   - Chunk 0: JSON (glTF scene descriptor)
 *   - Chunk 1: BIN (raw buffer data - vertices, indices, colors)
 */
public class GlbParser {

    public static class Triangle {
        public float[] v0, v1, v2;       // xyz positions
        public float[] c0, c1, c2;       // rgb colors (0-1)

        public Triangle(float[] v0, float[] v1, float[] v2,
                        float[] c0, float[] c1, float[] c2) {
            this.v0 = v0; this.v1 = v1; this.v2 = v2;
            this.c0 = c0; this.c1 = c1; this.c2 = c2;
        }

        public float[] centerColor() {
            return new float[]{
                    (c0[0] + c1[0] + c2[0]) / 3f,
                    (c0[1] + c1[1] + c2[1]) / 3f,
                    (c0[2] + c1[2] + c2[2]) / 3f
            };
        }
    }

    public List<Triangle> parse(byte[] glbBytes) {
        ByteBuffer buf = ByteBuffer.wrap(glbBytes).order(ByteOrder.LITTLE_ENDIAN);

        // Validate magic: 0x46546C67 = "glTF"
        int magic = buf.getInt();
        if (magic != 0x46546C67) throw new RuntimeException("Not a valid GLB file");
        int version = buf.getInt();
        int totalLength = buf.getInt();

        // Read JSON chunk
        int jsonLength = buf.getInt();
        int jsonType = buf.getInt(); // should be 0x4E4F534A = "JSON"
        byte[] jsonBytes = new byte[jsonLength];
        buf.get(jsonBytes);
        String jsonStr = new String(jsonBytes, StandardCharsets.UTF_8);

        // Read BIN chunk
        int binLength = buf.getInt();
        int binType = buf.getInt(); // should be 0x004E4942 = "BIN\0"
        byte[] binData = new byte[binLength];
        buf.get(binData);

        // Parse glTF JSON
        JsonObject gltf = JsonParser.parseString(jsonStr).getAsJsonObject();
        return extractTriangles(gltf, binData);
    }

    private List<Triangle> extractTriangles(JsonObject gltf, byte[] bin) {
        List<Triangle> triangles = new ArrayList<>();

        JsonArray meshes = gltf.getAsJsonArray("meshes");
        if (meshes == null || meshes.size() == 0) return triangles;

        JsonArray accessors = gltf.getAsJsonArray("accessors");
        JsonArray bufferViews = gltf.getAsJsonArray("bufferViews");

        for (JsonElement meshEl : meshes) {
            JsonArray primitives = meshEl.getAsJsonObject().getAsJsonArray("primitives");
            for (JsonElement primEl : primitives) {
                JsonObject prim = primEl.getAsJsonObject();
                JsonObject attrs = prim.getAsJsonObject("attributes");

                // Get position accessor
                int posIdx = attrs.get("POSITION").getAsInt();
                float[][] positions = readVec3Array(accessors.get(posIdx).getAsJsonObject(), bufferViews, bin);

                // Get color accessor (COLOR_0)
                float[][] colors = null;
                if (attrs.has("COLOR_0")) {
                    int colIdx = attrs.get("COLOR_0").getAsInt();
                    JsonObject colAccessor = accessors.get(colIdx).getAsJsonObject();
                    String type = colAccessor.get("type").getAsString();
                    if (type.equals("VEC3") || type.equals("VEC4")) {
                        colors = readColorArray(colAccessor, bufferViews, bin);
                    }
                }

                // Default white if no color data
                if (colors == null) {
                    colors = new float[positions.length][3];
                    for (float[] c : colors) Arrays.fill(c, 1.0f);
                }

                // Get indices
                int[] indices;
                if (prim.has("indices")) {
                    int idxIdx = prim.get("indices").getAsInt();
                    indices = readIndices(accessors.get(idxIdx).getAsJsonObject(), bufferViews, bin);
                } else {
                    // No index buffer — sequential
                    indices = new int[positions.length];
                    for (int i = 0; i < positions.length; i++) indices[i] = i;
                }

                // Build triangles from index triples
                for (int i = 0; i + 2 < indices.length; i += 3) {
                    int i0 = indices[i], i1 = indices[i+1], i2 = indices[i+2];
                    triangles.add(new Triangle(
                            positions[i0], positions[i1], positions[i2],
                            colors[i0], colors[i1], colors[i2]
                    ));
                }
            }
        }
        return triangles;
    }

    private float[][] readVec3Array(JsonObject accessor, JsonArray bufferViews, byte[] bin) {
        int count = accessor.get("count").getAsInt();
        int bvIdx = accessor.get("bufferView").getAsInt();
        int byteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        JsonObject bv = bufferViews.get(bvIdx).getAsJsonObject();
        int bvOffset = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
        int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : 12;

        float[][] result = new float[count][3];
        ByteBuffer buf = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        int start = bvOffset + byteOffset;
        for (int i = 0; i < count; i++) {
            buf.position(start + i * stride);
            result[i][0] = buf.getFloat();
            result[i][1] = buf.getFloat();
            result[i][2] = buf.getFloat();
        }
        return result;
    }

    private float[][] readColorArray(JsonObject accessor, JsonArray bufferViews, byte[] bin) {
        int count = accessor.get("count").getAsInt();
        int componentType = accessor.get("componentType").getAsInt();
        int bvIdx = accessor.get("bufferView").getAsInt();
        int byteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        JsonObject bv = bufferViews.get(bvIdx).getAsJsonObject();
        int bvOffset = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
        boolean isVec4 = accessor.get("type").getAsString().equals("VEC4");
        int components = isVec4 ? 4 : 3;
        int bytesPerComponent = (componentType == 5121) ? 1 : (componentType == 5123) ? 2 : 4;
        int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : components * bytesPerComponent;

        float[][] result = new float[count][3];
        ByteBuffer buf = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        int start = bvOffset + byteOffset;

        for (int i = 0; i < count; i++) {
            buf.position(start + i * stride);
            for (int c = 0; c < 3; c++) {
                if (componentType == 5126) { // FLOAT
                    result[i][c] = buf.getFloat();
                } else if (componentType == 5121) { // UNSIGNED_BYTE
                    result[i][c] = (buf.get() & 0xFF) / 255f;
                } else if (componentType == 5123) { // UNSIGNED_SHORT
                    result[i][c] = (buf.getShort() & 0xFFFF) / 65535f;
                }
            }
        }
        return result;
    }

    private int[] readIndices(JsonObject accessor, JsonArray bufferViews, byte[] bin) {
        int count = accessor.get("count").getAsInt();
        int componentType = accessor.get("componentType").getAsInt();
        int bvIdx = accessor.get("bufferView").getAsInt();
        int byteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        JsonObject bv = bufferViews.get(bvIdx).getAsJsonObject();
        int bvOffset = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;

        int[] indices = new int[count];
        ByteBuffer buf = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        buf.position(bvOffset + byteOffset);

        for (int i = 0; i < count; i++) {
            if (componentType == 5123) { // UNSIGNED_SHORT
                indices[i] = buf.getShort() & 0xFFFF;
            } else if (componentType == 5125) { // UNSIGNED_INT
                indices[i] = buf.getInt();
            } else if (componentType == 5121) { // UNSIGNED_BYTE
                indices[i] = buf.get() & 0xFF;
            }
        }
        return indices;
    }
}