package com.example.examplemod;

import com.example.examplemod.fal.BlockMapper;
import com.example.examplemod.fal.Voxelizer;
import com.example.examplemod.pipeline.GenerationPipeline;
import com.example.examplemod.provider.huggingface.HuggingFaceTextToImageProvider;
import com.example.examplemod.provider.replicate.ReplicateImageTo3dProvider;
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

    private static final Path API_KEY_PATH = Path.of("config", "aibuilder", "api-key.txt");

    @SubscribeEvent
    public static void registerCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                literal("generate")
                        .then(literal("setkey")
                                .then(argument("key", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            String key = StringArgumentType.getString(ctx, "key");
                                            try {
                                                Files.createDirectories(API_KEY_PATH.getParent());
                                                Files.writeString(API_KEY_PATH, key.trim());
                                                ctx.getSource().sendSuccess(() -> Component.literal("§aAPI key saved!"), false);
                                            } catch (IOException e) {
                                                ctx.getSource().sendFailure(Component.literal("Failed to save key: " + e.getMessage()));
                                            }
                                            return 1;
                                        })
                                )
                        )

                        .then(literal("generate")
                                .then(argument("size", IntegerArgumentType.integer(8, 64))
                                        .then(argument("prompt", StringArgumentType.greedyString())
                                                .executes(ctx -> {
                                                    CommandSourceStack source = ctx.getSource();
                                                    int size = IntegerArgumentType.getInteger(ctx, "size");
                                                    String prompt = StringArgumentType.getString(ctx, "prompt");

                                                    if (source.getEntity() == null) {
                                                        source.sendFailure(Component.literal("Must be run by a player."));
                                                        return 0;
                                                    }

                                                    String apiKey = loadApiKey();
                                                    if (apiKey == null) {
                                                        source.sendFailure(Component.literal(
                                                                "No API key set. Use: /generate setkey YOUR_KEY"));
                                                        return 0;
                                                    }

                                                    source.sendSuccess(() -> Component.literal(
                                                            "§eGenerating §f\"" + prompt + "\"§e at size " + size + "... (~30s)"), false);

                                                    BlockPos origin = getPlacementPos(source);
                                                    ServerLevel world = source.getLevel();

                                                    GenerationPipeline pipeline = buildPipeline(apiKey);
                                                    BlockMapper mapper = new BlockMapper();

                                                    CompletableFuture.runAsync(() -> {
                                                        try {
                                                            List<Voxelizer.Voxel> voxels = pipeline.run(
                                                                    prompt, size,
                                                                    (step, total, msg) -> source.sendSuccess(
                                                                            () -> Component.literal("§7[" + step + "/" + total + "] " + msg), false)
                                                            );

                                                            world.getServer().execute(() -> {
                                                                placeBlocks(world, origin, voxels, mapper);
                                                                source.sendSuccess(() -> Component.literal(
                                                                        "§a✓ Build complete! Placed " + voxels.size() + " blocks."), false);
                                                            });

                                                        } catch (Exception e) {
                                                            source.sendFailure(Component.literal("§cError: " + e.getMessage()));
                                                            LOGGER.error("Generation pipeline failed for prompt '{}': ", prompt, e);
                                                        }
                                                    });

                                                    return 1;
                                                })
                                        )
                                )
                        )

                        .then(literal("status")
                                .executes(ctx -> {
                                    String key = loadApiKey();
                                    if (key == null) {
                                        ctx.getSource().sendSuccess(() -> Component.literal("§cNo API key configured."), false);
                                    } else {
                                        ctx.getSource().sendSuccess(() -> Component.literal(
                                                "§aAPI key configured. (" + key.substring(0, Math.min(8, key.length())) + "...)"), false);
                                    }
                                    return 1;
                                })
                        )
        );
    }

    /**
     * Builds the generation pipeline with the current providers.
     * To switch APIs, change the providers here — no other code needs to change.
     */
    private static GenerationPipeline buildPipeline(String apiKey) {
        return new GenerationPipeline(
                new HuggingFaceTextToImageProvider(apiKey),
                new ReplicateImageTo3dProvider(apiKey)
        );
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

    private static String loadApiKey() {
        try {
            if (Files.exists(API_KEY_PATH)) {
                String key = Files.readString(API_KEY_PATH).trim();
                return key.isEmpty() ? null : key;
            }
        } catch (IOException e) {
            // ignore — key simply not set
        }
        return null;
    }
}
