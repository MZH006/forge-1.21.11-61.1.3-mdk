package com.example.examplemod.fal;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.*;

/**
 * Maps an RGB color to the nearest Minecraft block using CIE-LAB color space.
 * LAB is perceptually uniform — distances match how humans perceive color differences.
 *
 * Block palette covers the most visually distinct solid blocks available in vanilla.
 */
public class BlockMapper {

    private record BlockColor(Block block, double l, double a, double b) {}

    private final List<BlockColor> palette = new ArrayList<>();

    public BlockMapper() {
        // Concrete — full 16-color range (most saturated, best for solid color matching)
        addBlock(Blocks.WHITE_CONCRETE,       0xF9FFFE);
        addBlock(Blocks.LIGHT_GRAY_CONCRETE,  0x7D7D73);
        addBlock(Blocks.GRAY_CONCRETE,        0x36393D);
        addBlock(Blocks.BLACK_CONCRETE,       0x080A0F);
        addBlock(Blocks.BROWN_CONCRETE,       0x5C3A24);
        addBlock(Blocks.RED_CONCRETE,         0x8E2121);
        addBlock(Blocks.ORANGE_CONCRETE,      0xE06101);
        addBlock(Blocks.YELLOW_CONCRETE,      0xF1AF15);
        addBlock(Blocks.LIME_CONCRETE,        0x5EA818);
        addBlock(Blocks.GREEN_CONCRETE,       0x495B24);
        addBlock(Blocks.CYAN_CONCRETE,        0x157788);
        addBlock(Blocks.LIGHT_BLUE_CONCRETE,  0x2389C6);
        addBlock(Blocks.BLUE_CONCRETE,        0x2C2E8F);
        addBlock(Blocks.PURPLE_CONCRETE,      0x64209C);
        addBlock(Blocks.MAGENTA_CONCRETE,     0xA9309F);
        addBlock(Blocks.PINK_CONCRETE,        0xD5658F);

        // Stone blocks (gray/tan range)
        addBlock(Blocks.STONE,                0x808080);
        addBlock(Blocks.COBBLESTONE,          0x7F7F7F);
        addBlock(Blocks.STONE_BRICKS,         0x7A7A7A);
        addBlock(Blocks.ANDESITE,             0x878787);
        addBlock(Blocks.DIORITE,              0xE4E4E4);
        addBlock(Blocks.SMOOTH_STONE,         0xA0A0A0);
        addBlock(Blocks.POLISHED_ANDESITE,    0x848484);

        // Wood/stone earthy tones
        addBlock(Blocks.OAK_PLANKS,           0xB18962);
        addBlock(Blocks.BIRCH_PLANKS,         0xD2BC7C);
        addBlock(Blocks.DARK_OAK_PLANKS,      0x44331C);
        addBlock(Blocks.GRANITE,              0x9A6E53);
        addBlock(Blocks.BRICKS,               0x8F5D4B);

        // Terracotta — core muted/earthy tones
        addBlock(Blocks.WHITE_TERRACOTTA,     0xD1B1A1);
        addBlock(Blocks.LIGHT_GRAY_TERRACOTTA,0x876B62);
        addBlock(Blocks.GRAY_TERRACOTTA,      0x392A23);
        addBlock(Blocks.ORANGE_TERRACOTTA,    0xA05325);
        addBlock(Blocks.BROWN_TERRACOTTA,     0x4D3224);
        addBlock(Blocks.RED_TERRACOTTA,       0x8F3D2E);
        addBlock(Blocks.YELLOW_TERRACOTTA,    0xBA8523);
        addBlock(Blocks.CYAN_TERRACOTTA,      0x575B5B);
        addBlock(Blocks.BLUE_TERRACOTTA,      0x4A3B5B);
        addBlock(Blocks.PINK_TERRACOTTA,      0xA14E4E);
        addBlock(Blocks.MAGENTA_TERRACOTTA,   0x95576C);
        addBlock(Blocks.PURPLE_TERRACOTTA,    0x764556);
        addBlock(Blocks.GREEN_TERRACOTTA,     0x4C532A);
        addBlock(Blocks.LIME_TERRACOTTA,      0x677534);
        addBlock(Blocks.LIGHT_BLUE_TERRACOTTA,0x706C89);
        addBlock(Blocks.BLACK_TERRACOTTA,     0x251610);

        // Dark/black blocks with texture variety
        addBlock(Blocks.COAL_BLOCK,           0x1A1919);
        addBlock(Blocks.BLACKSTONE,           0x2A2333);
        addBlock(Blocks.POLISHED_BLACKSTONE,  0x36313D);
        addBlock(Blocks.DEEPSLATE,            0x4F4F51);
        addBlock(Blocks.DEEPSLATE_TILES,      0x353538);
        addBlock(Blocks.DEEPSLATE_BRICKS,     0x434343);
        addBlock(Blocks.COBBLED_DEEPSLATE,    0x555555);
        addBlock(Blocks.DARK_PRISMARINE,      0x395A4E);

        // White/light blocks with texture variety
        addBlock(Blocks.QUARTZ_BLOCK,         0xE8E5DD);
        addBlock(Blocks.CALCITE,              0xE3E4DC);
        addBlock(Blocks.BONE_BLOCK,           0xE3DCC6);
        addBlock(Blocks.WHITE_WOOL,           0xE9ECEC);
        addBlock(Blocks.MUSHROOM_STEM,        0xC9AE9D);

        // Warm tan/orange sandstone
        addBlock(Blocks.SANDSTONE,            0xE3DBB0);
        addBlock(Blocks.RED_SANDSTONE,        0xBF6330);

        // Copper oxidation stages
        addBlock(Blocks.COPPER_BLOCK,         0xC06A4D);
        addBlock(Blocks.EXPOSED_COPPER,       0xA07D5D);
        addBlock(Blocks.WEATHERED_COPPER,     0x6D9466);
        addBlock(Blocks.OXIDIZED_COPPER,      0x53A384);

        // More wood types
        addBlock(Blocks.SPRUCE_PLANKS,        0x73563A);
        addBlock(Blocks.JUNGLE_PLANKS,        0xB88856);
        addBlock(Blocks.ACACIA_PLANKS,        0xB05E3C);
        addBlock(Blocks.MANGROVE_PLANKS,      0x773636);
        addBlock(Blocks.CHERRY_PLANKS,        0xE4B4A8);
        addBlock(Blocks.CRIMSON_PLANKS,       0x6C3A4A);
        addBlock(Blocks.WARPED_PLANKS,        0x2B6D64);

        // Unique color blocks
        addBlock(Blocks.AMETHYST_BLOCK,       0x8B6AA6);
        addBlock(Blocks.PRISMARINE,           0x63A293);
        addBlock(Blocks.PRISMARINE_BRICKS,    0x5BA496);
        addBlock(Blocks.SEA_LANTERN,          0xACDBC5);
        addBlock(Blocks.MUD_BRICKS,           0x8B6B4D);
        addBlock(Blocks.PACKED_MUD,           0x8E7259);
        addBlock(Blocks.TUFF,                 0x6C6C66);
        addBlock(Blocks.MOSS_BLOCK,           0x4F6633);

        // Nether blocks
        addBlock(Blocks.NETHER_BRICKS,        0x2C151A);
        addBlock(Blocks.RED_NETHER_BRICKS,    0x45080A);
        addBlock(Blocks.SHROOMLIGHT,          0xF09B4E);
        addBlock(Blocks.NETHERRACK,           0x6D3636);
        addBlock(Blocks.WARPED_WART_BLOCK,    0x167879);
        addBlock(Blocks.CRIMSON_NYLIUM,       0x8B1F1F);

        // End blocks
        addBlock(Blocks.END_STONE,            0xDBDCA6);
        addBlock(Blocks.END_STONE_BRICKS,     0xDBDEA7);
        addBlock(Blocks.PURPUR_BLOCK,         0xA87AA4);
        addBlock(Blocks.PURPUR_PILLAR,        0xAB7FA7);

        // Wool — soft/muted tones
        addBlock(Blocks.BROWN_WOOL,           0x724728);
        addBlock(Blocks.GRAY_WOOL,            0x3E4447);
        addBlock(Blocks.LIGHT_GRAY_WOOL,      0x8E8E86);
        addBlock(Blocks.CYAN_WOOL,            0x158991);
        addBlock(Blocks.PURPLE_WOOL,          0x7B2BAD);
        addBlock(Blocks.BLUE_WOOL,            0x353A9E);
        addBlock(Blocks.GREEN_WOOL,           0x546D1B);
        addBlock(Blocks.RED_WOOL,             0xA12722);
        addBlock(Blocks.ORANGE_WOOL,          0xF07613);
        addBlock(Blocks.YELLOW_WOOL,          0xF8C627);
        addBlock(Blocks.LIME_WOOL,            0x70B919);
        addBlock(Blocks.PINK_WOOL,            0xED8DAC);
        addBlock(Blocks.MAGENTA_WOOL,         0xBD44B3);
        addBlock(Blocks.LIGHT_BLUE_WOOL,      0x3AAFD9);
        addBlock(Blocks.BLACK_WOOL,           0x141519);
    }

    /** Registers a block from a packed 0xRRGGBB hex color. */
    private void addBlock(Block block, int hex) {
        int r = (hex >> 16) & 0xFF;
        int g = (hex >>  8) & 0xFF;
        int b =  hex        & 0xFF;
        double[] lab = rgbToLab(r / 255.0, g / 255.0, b / 255.0);
        palette.add(new BlockColor(block, lab[0], lab[1], lab[2]));
    }

    /**
     * Find the nearest block to the given RGB color using CIE-LAB delta-E distance.
     */
    public Block nearestBlock(float r, float g, float b) {
        double[] lab = rgbToLab(r, g, b);
        Block best = Blocks.STONE;
        double bestDist = Double.MAX_VALUE;

        for (BlockColor bc : palette) {
            double dl = lab[0] - bc.l();
            double da = lab[1] - bc.a();
            double db = lab[2] - bc.b();
            double dist = dl*dl + da*da + db*db;
            if (dist < bestDist) {
                bestDist = dist;
                best = bc.block();
            }
        }
        return best;
    }

    // --- CIE-LAB conversion ---

    private double[] rgbToLab(double r, double g, double b) {
        // sRGB to linear
        r = linearize(r);
        g = linearize(g);
        b = linearize(b);

        // Linear RGB to XYZ (D65)
        double x = r * 0.4124564 + g * 0.3575761 + b * 0.1804375;
        double y = r * 0.2126729 + g * 0.7151522 + b * 0.0721750;
        double z = r * 0.0193339 + g * 0.1191920 + b * 0.9503041;

        // XYZ to Lab (D65 white point)
        x /= 0.95047; y /= 1.00000; z /= 1.08883;
        x = f(x); y = f(y); z = f(z);

        return new double[]{
                116.0 * y - 16.0,
                500.0 * (x - y),
                200.0 * (y - z)
        };
    }

    private double linearize(double c) {
        return (c <= 0.04045) ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private double f(double t) {
        return (t > 0.008856) ? Math.cbrt(t) : (7.787 * t + 16.0 / 116.0);
    }
}
