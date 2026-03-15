package com.example.examplemod.fal;

import com.google.gson.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Parses a GLB (binary glTF) file and extracts triangles with vertex colors.
 *
 * Color extraction priority per primitive:
 *   1. Vertex colors (COLOR_0 attribute)
 *   2. UV + baseColorTexture sampling (PBR models from Tripo use this)
 *   3. baseColorFactor flat color
 *   4. White fallback
 */
public class GlbParser {

    public static class Triangle {
        public float[] v0, v1, v2;   // xyz positions
        public float[] c0, c1, c2;   // rgb colors (0-1)

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

        int magic = buf.getInt();
        if (magic != 0x46546C67) throw new RuntimeException("Not a valid GLB file");
        buf.getInt(); // version
        buf.getInt(); // totalLength

        // JSON chunk
        int jsonLength = buf.getInt();
        buf.getInt(); // chunk type
        byte[] jsonBytes = new byte[jsonLength];
        buf.get(jsonBytes);
        String jsonStr = new String(jsonBytes, StandardCharsets.UTF_8);

        // BIN chunk
        int binLength = buf.getInt();
        buf.getInt(); // chunk type
        byte[] binData = new byte[binLength];
        buf.get(binData);

        JsonObject gltf = JsonParser.parseString(jsonStr).getAsJsonObject();
        return extractTriangles(gltf, binData);
    }

    private List<Triangle> extractTriangles(JsonObject gltf, byte[] bin) {
        List<Triangle> triangles = new ArrayList<>();

        JsonArray meshes      = gltf.getAsJsonArray("meshes");
        if (meshes == null || meshes.size() == 0) return triangles;

        JsonArray accessors   = gltf.getAsJsonArray("accessors");
        JsonArray bufferViews = gltf.getAsJsonArray("bufferViews");
        JsonArray materials   = gltf.getAsJsonArray("materials");
        JsonArray textures    = gltf.getAsJsonArray("textures");
        JsonArray images      = gltf.getAsJsonArray("images");

        // Pre-load all texture images referenced in the GLB
        Map<Integer, BufferedImage> imageCache = loadImages(images, bufferViews, bin);

        for (JsonElement meshEl : meshes) {
            for (JsonElement primEl : meshEl.getAsJsonObject().getAsJsonArray("primitives")) {
                JsonObject prim  = primEl.getAsJsonObject();
                JsonObject attrs = prim.getAsJsonObject("attributes");

                int posIdx = attrs.get("POSITION").getAsInt();
                float[][] positions = readVec3Array(accessors.get(posIdx).getAsJsonObject(), bufferViews, bin);

                float[][] colors = null;

                // 1. Vertex colors
                if (attrs.has("COLOR_0")) {
                    int colIdx = attrs.get("COLOR_0").getAsInt();
                    JsonObject colAcc = accessors.get(colIdx).getAsJsonObject();
                    String type = colAcc.get("type").getAsString();
                    if (type.equals("VEC3") || type.equals("VEC4")) {
                        colors = readColorArray(colAcc, bufferViews, bin);
                    }
                }

                // 2. UV + baseColorTexture
                if (colors == null && attrs.has("TEXCOORD_0") && materials != null && prim.has("material")) {
                    int matIdx = prim.get("material").getAsInt();
                    JsonObject mat = materials.get(matIdx).getAsJsonObject();
                    BufferedImage tex = resolveBaseColorTexture(mat, textures, imageCache);
                    if (tex != null) {
                        int uvIdx = attrs.get("TEXCOORD_0").getAsInt();
                        float[][] uvs = readVec2Array(accessors.get(uvIdx).getAsJsonObject(), bufferViews, bin);
                        colors = sampleTexture(tex, uvs);
                    }
                }

                // 3. baseColorFactor flat color
                if (colors == null && materials != null && prim.has("material")) {
                    int matIdx = prim.get("material").getAsInt();
                    float[] baseColor = readMaterialBaseColor(materials.get(matIdx).getAsJsonObject());
                    colors = new float[positions.length][3];
                    for (float[] c : colors) { c[0] = baseColor[0]; c[1] = baseColor[1]; c[2] = baseColor[2]; }
                }

                // 4. White fallback
                if (colors == null) {
                    colors = new float[positions.length][3];
                    for (float[] c : colors) Arrays.fill(c, 1.0f);
                }

                // Build index list
                int[] indices;
                if (prim.has("indices")) {
                    indices = readIndices(accessors.get(prim.get("indices").getAsInt()).getAsJsonObject(), bufferViews, bin);
                } else {
                    indices = new int[positions.length];
                    for (int i = 0; i < positions.length; i++) indices[i] = i;
                }

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

    // --- Texture loading ---

    private Map<Integer, BufferedImage> loadImages(JsonArray images, JsonArray bufferViews, byte[] bin) {
        Map<Integer, BufferedImage> result = new HashMap<>();
        if (images == null) return result;
        for (int i = 0; i < images.size(); i++) {
            JsonObject img = images.get(i).getAsJsonObject();
            if (!img.has("bufferView")) continue;
            try {
                int bvIdx = img.get("bufferView").getAsInt();
                JsonObject bv = bufferViews.get(bvIdx).getAsJsonObject();
                int offset = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
                int length = bv.get("byteLength").getAsInt();
                byte[] imgBytes = Arrays.copyOfRange(bin, offset, offset + length);
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(imgBytes));
                if (image != null) result.put(i, image);
            } catch (Exception e) {
                System.out.println("[GlbParser] Failed to load image " + i + ": " + e.getMessage());
            }
        }
        System.out.println("[GlbParser] Loaded " + result.size() + " texture image(s) from GLB");
        return result;
    }

    private BufferedImage resolveBaseColorTexture(JsonObject material, JsonArray textures,
                                                   Map<Integer, BufferedImage> imageCache) {
        try {
            JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
            if (pbr == null || !pbr.has("baseColorTexture")) return null;
            int texIdx = pbr.getAsJsonObject("baseColorTexture").get("index").getAsInt();
            if (textures == null || texIdx >= textures.size()) return null;
            int imgIdx = textures.get(texIdx).getAsJsonObject().get("source").getAsInt();
            return imageCache.get(imgIdx);
        } catch (Exception e) {
            return null;
        }
    }

    /** Samples the texture at each UV coordinate, returning RGB per vertex. */
    private float[][] sampleTexture(BufferedImage tex, float[][] uvs) {
        int w = tex.getWidth();
        int h = tex.getHeight();
        float[][] colors = new float[uvs.length][3];
        for (int i = 0; i < uvs.length; i++) {
            // Wrap UVs (glTF uses repeat by default)
            float u = uvs[i][0] % 1.0f; if (u < 0) u += 1.0f;
            float v = uvs[i][1] % 1.0f; if (v < 0) v += 1.0f;
            int px = Math.min((int)(u * w), w - 1);
            int py = Math.min((int)(v * h), h - 1);
            int rgb = tex.getRGB(px, py);
            colors[i][0] = ((rgb >> 16) & 0xFF) / 255f;
            colors[i][1] = ((rgb >>  8) & 0xFF) / 255f;
            colors[i][2] = ( rgb        & 0xFF) / 255f;
        }
        return colors;
    }

    // --- glTF accessors ---

    private float[] readMaterialBaseColor(JsonObject material) {
        try {
            JsonObject pbr = material.getAsJsonObject("pbrMetallicRoughness");
            if (pbr != null && pbr.has("baseColorFactor")) {
                JsonArray f = pbr.getAsJsonArray("baseColorFactor");
                return new float[]{ f.get(0).getAsFloat(), f.get(1).getAsFloat(), f.get(2).getAsFloat() };
            }
        } catch (Exception ignored) {}
        return new float[]{ 1f, 1f, 1f };
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

    private float[][] readVec2Array(JsonObject accessor, JsonArray bufferViews, byte[] bin) {
        int count = accessor.get("count").getAsInt();
        int bvIdx = accessor.get("bufferView").getAsInt();
        int byteOffset = accessor.has("byteOffset") ? accessor.get("byteOffset").getAsInt() : 0;
        JsonObject bv = bufferViews.get(bvIdx).getAsJsonObject();
        int bvOffset = bv.has("byteOffset") ? bv.get("byteOffset").getAsInt() : 0;
        int stride = bv.has("byteStride") ? bv.get("byteStride").getAsInt() : 8;

        float[][] result = new float[count][2];
        ByteBuffer buf = ByteBuffer.wrap(bin).order(ByteOrder.LITTLE_ENDIAN);
        int start = bvOffset + byteOffset;
        for (int i = 0; i < count; i++) {
            buf.position(start + i * stride);
            result[i][0] = buf.getFloat();
            result[i][1] = buf.getFloat();
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
                if (componentType == 5126)      result[i][c] = buf.getFloat();
                else if (componentType == 5121) result[i][c] = (buf.get() & 0xFF) / 255f;
                else if (componentType == 5123) result[i][c] = (buf.getShort() & 0xFFFF) / 65535f;
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
            if (componentType == 5123)      indices[i] = buf.getShort() & 0xFFFF;
            else if (componentType == 5125) indices[i] = buf.getInt();
            else if (componentType == 5121) indices[i] = buf.get() & 0xFF;
        }
        return indices;
    }
}
