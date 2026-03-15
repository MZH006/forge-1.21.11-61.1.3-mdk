package com.example.examplemod;

import com.example.examplemod.fal.BlockMapper;
import com.example.examplemod.fal.Voxelizer;
import com.example.examplemod.pipeline.GenerationPipeline;
import com.example.examplemod.pipeline.TripoGenerationPipeline;
import com.example.examplemod.provider.huggingface.HuggingFaceTextToImageProvider;
import com.example.examplemod.provider.replicate.ReplicateImageTo3dProvider;
import com.example.examplemod.provider.tripo.TripoTextTo3dProvider;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

@Mod.EventBusSubscriber
public class ModCommands {

    private static final Logger LOGGER = LogManager.getLogger();

    private static final Path HF_KEY_PATH       = Path.of("config", "aibuilder", "hf-api-key.txt");
    private static final Path REPLICATE_KEY_PATH = Path.of("config", "aibuilder", "replicate-api-key.txt");
    private static final Path TRIPO_KEY_PATH     = Path.of("config", "aibuilder", "tripo-api-key.txt");

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                literal("generate")

                        // /generate setkey <token> — HuggingFace Flux endpoint
                        .then(literal("setkey")
                                .then(argument("key", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            saveKey(HF_KEY_PATH,
                                                    StringArgumentType.getString(ctx, "key"),
                                                    "HuggingFace API key saved!",
                                                    ctx.getSource());
                                            return 1;
                                        })
                                )
                        )

                        // /generate setreplicatekey <token> — Replicate image-to-3D
                        .then(literal("setreplicatekey")
                                .then(argument("key", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            saveKey(REPLICATE_KEY_PATH,
                                                    StringArgumentType.getString(ctx, "key"),
                                                    "Replicate API key saved!",
                                                    ctx.getSource());
                                            return 1;
                                        })
                                )
                        )

                        // /generate settripokey <token> — Tripo AI text-to-3D
                        .then(literal("settripokey")
                                .then(argument("key", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            saveKey(TRIPO_KEY_PATH,
                                                    StringArgumentType.getString(ctx, "key"),
                                                    "Tripo AI API key saved!",
                                                    ctx.getSource());
                                            return 1;
                                        })
                                )
                        )

                        // /generate generate <size> <prompt>
                        .then(literal("generate")
                                .then(argument("size", IntegerArgumentType.integer(8, 128))
                                        .then(argument("prompt", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    CommandSourceStack source = ctx.getSource();
                                                    int size = IntegerArgumentType.getInteger(ctx, "size");
                                                    String prompt = StringArgumentType.getString(ctx, "prompt");

                                                    if (source.getEntity() == null) {
                                                        source.sendFailure(Component.literal("Must be run by a player."));
                                                        return 0;
                                                    }

                                                    String tripoKey     = loadKey(TRIPO_KEY_PATH);
                                                    String hfKey        = loadKey(HF_KEY_PATH);
                                                    String replicateKey = loadKey(REPLICATE_KEY_PATH);

                                                    if (tripoKey == null && replicateKey == null) {
                                                        source.sendFailure(Component.literal(
                                                                "§cNo API keys set. Use:\n" +
                                                                "  /generate settripokey <key>  (recommended, free tier)\n" +
                                                                "  /generate setreplicatekey <key>  (fallback)"));
                                                        return 0;
                                                    }

                                                    BlockPos origin = getPlacementPos(source);
                                                    ServerLevel world = source.getLevel();
                                                    BlockMapper mapper = new BlockMapper();

                                                    // Prefer Tripo (text-to-3D, no image step needed)
                                                    if (tripoKey != null) {
                                                        source.sendSuccess(() -> Component.literal(
                                                                "§eGenerating §f\"" + prompt + "\"§e at size " + size +
                                                                " via Tripo AI... (~2 min)"), false);

                                                        TripoGenerationPipeline pipeline = new TripoGenerationPipeline(
                                                                new TripoTextTo3dProvider(tripoKey));

                                                        CompletableFuture.runAsync(() -> runPipeline(
                                                                () -> pipeline.run(prompt, size,
                                                                        (step, total, msg) -> source.sendSuccess(
                                                                                () -> Component.literal("§7[" + step + "/" + total + "] " + msg), false)),
                                                                world, origin, mapper, source, prompt));

                                                    } else {
                                                        // Fallback: HuggingFace image → Replicate 3D
                                                        source.sendSuccess(() -> Component.literal(
                                                                "§eGenerating §f\"" + prompt + "\"§e at size " + size +
                                                                " via HuggingFace + Replicate... (~5 min)"), false);

                                                        GenerationPipeline pipeline = new GenerationPipeline(
                                                                new HuggingFaceTextToImageProvider(hfKey),
                                                                new ReplicateImageTo3dProvider(replicateKey));

                                                        CompletableFuture.runAsync(() -> runPipeline(
                                                                () -> pipeline.run(prompt, size,
                                                                        (step, total, msg) -> source.sendSuccess(
                                                                                () -> Component.literal("§7[" + step + "/" + total + "] " + msg), false)),
                                                                world, origin, mapper, source, prompt));
                                                    }

                                                    return 1;
                                                })
                                        )
                                )
                        )

                        // /generate status
                        .then(literal("status")
                                .executes(ctx -> {
                                    String tripoKey = loadKey(TRIPO_KEY_PATH);
                                    String hfKey    = loadKey(HF_KEY_PATH);
                                    String repKey   = loadKey(REPLICATE_KEY_PATH);

                                    String active = tripoKey != null ? "§aTripo AI (text-to-3D)" : "§eHuggingFace + Replicate";

                                    ctx.getSource().sendSuccess(() -> Component.literal(
                                            "Active pipeline: " + active + "\n" +
                                            "Tripo key:       " + keyStatus(tripoKey) + "\n" +
                                            "HuggingFace key: " + keyStatus(hfKey) + "\n" +
                                            "Replicate key:   " + keyStatus(repKey)), false);
                                    return 1;
                                })
                        )
        );
    }

    /** Runs the voxel pipeline and places blocks, handling errors. */
    private static void runPipeline(VoxelSupplier supplier, ServerLevel world, BlockPos origin,
                                    BlockMapper mapper, CommandSourceStack source, String prompt) {
        try {
            List<Voxelizer.Voxel> voxels = supplier.get();
            world.getServer().execute(() -> {
                placeBlocks(world, origin, voxels, mapper);
                source.sendSuccess(() -> Component.literal(
                        "§a✓ Build complete! Placed " + voxels.size() + " blocks."), false);
            });
        } catch (Exception e) {
            source.sendFailure(Component.literal("§cError: " + e.getMessage()));
            LOGGER.error("Generation pipeline failed for prompt '{}': ", prompt, e);
        }
    }

    @FunctionalInterface
    private interface VoxelSupplier {
        List<Voxelizer.Voxel> get() throws Exception;
    }

    private static void placeBlocks(ServerLevel world, BlockPos origin,
                                    List<Voxelizer.Voxel> voxels, BlockMapper mapper) {
        for (Voxelizer.Voxel voxel : voxels) {
            Block block = mapper.nearestBlock(voxel.r, voxel.g, voxel.b);
            BlockPos pos = origin.offset(voxel.x, voxel.y, voxel.z);
            world.setBlock(pos, block.defaultBlockState(), 3);
        }
    }

    private static BlockPos getPlacementPos(CommandSourceStack source) {
        Entity entity = source.getEntity();
        BlockPos base = BlockPos.containing(source.getPosition());
        if (entity != null) {
            return base.relative(entity.getDirection(), 5);
        }
        return base;
    }

    private static void saveKey(Path path, String key, String successMsg, CommandSourceStack source) {
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, key.trim());
            source.sendSuccess(() -> Component.literal("§a" + successMsg), false);
        } catch (IOException e) {
            source.sendFailure(Component.literal("Failed to save key: " + e.getMessage()));
        }
    }

    private static String loadKey(Path path) {
        try {
            if (Files.exists(path)) {
                String key = Files.readString(path).trim();
                return key.isEmpty() ? null : key;
            }
        } catch (IOException e) {
            // ignore — key simply not set
        }
        return null;
    }

    private static String keyStatus(String key) {
        return key != null ? "§a✓ set§r" : "§cnot set§r";
    }
}
