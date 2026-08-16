package com.kirbornu.gimpanum.debug;

import com.kirbornu.gimpanum.Gimpanum;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Временная мерка: где именно отваливается попытка появления моба. */
public final class SpawnProbeCommand {

    private static final int CHUNK_RANGE = 6;

    private SpawnProbeCommand() {
    }

    public static void register(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("blockcount")
                .then(Commands.argument("block", ResourceLocationArgument.id())
                        .executes(ctx -> count(ctx.getSource(), ResourceLocationArgument.getId(ctx, "block")))));
        root.then(Commands.literal("cavecut")
                .then(Commands.argument("z", IntegerArgumentType.integer(-30000000, 30000000))
                        .executes(ctx -> cut(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "z")))));
        root.then(Commands.literal("spawnprobe")
                .then(Commands.argument("samples", IntegerArgumentType.integer(1, 500000))
                        .executes(ctx -> run(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "samples")))));
    }

    /** Сколько таких блоков в округе — проверка, что мироген их вообще ставит. */
    private static int count(CommandSourceStack source, ResourceLocation id) {
        ServerLevel level = source.getLevel();
        Block block = BuiltInRegistries.BLOCK.get(id);
        BlockPos centre = BlockPos.containing(source.getPosition());
        int found = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = centre.getX() - 128; x <= centre.getX() + 128; x++) {
            for (int z = centre.getZ() - 128; z <= centre.getZ() + 128; z++) {
                for (int y = level.getMinBuildHeight(); y < level.getMaxBuildHeight(); y++) {
                    if (level.getBlockState(cursor.set(x, y, z)).is(block)) {
                        found++;
                    }
                }
            }
        }
        int total = found;
        say(source, id + ": " + total + " шт в квадрате 257×257");
        return total;
    }

    /** Вертикальный срез вдоль X: видно, лабиринт это ещё или уже зал. */
    private static int cut(CommandSourceStack source, int z) {
        ServerLevel level = source.getLevel();
        int x0 = BlockPos.containing(source.getPosition()).getX() - 60;
        for (int y = 100; y >= 0; y -= 1) {
            StringBuilder row = new StringBuilder(String.format("%3d ", y));
            for (int x = x0; x < x0 + 120; x++) {
                BlockPos pos = new BlockPos(x, y, z);
                row.append(level.getBlockState(pos).isAir() ? '.' : '#');
            }
            Gimpanum.LOGGER.info("[cavecut] {}", row);
        }
        source.sendSuccess(() -> Component.literal("срез в журнале"), false);
        return 1;
    }

    private static int run(CommandSourceStack source, int samples) {
        ServerLevel level = source.getLevel();
        RandomSource random = level.random;
        BlockPos centre = BlockPos.containing(source.getPosition());
        int cx = centre.getX() >> 4;
        int cz = centre.getZ() >> 4;

        Map<EntityType<?>, int[]> tally = new LinkedHashMap<>();
        int solid = 0;
        int air = 0;
        int[] heights = new int[16];

        for (int i = 0; i < samples; i++) {
            LevelChunk chunk = level.getChunk(
                    cx + random.nextInt(CHUNK_RANGE * 2 + 1) - CHUNK_RANGE,
                    cz + random.nextInt(CHUNK_RANGE * 2 + 1) - CHUNK_RANGE);
            BlockPos pos = randomPosWithin(level, chunk, random);
            if (pos.getY() < level.getMinBuildHeight() + 1) {
                continue;
            }
            BlockState state = chunk.getBlockState(pos);
            if (state.isRedstoneConductor(chunk, pos)) {
                solid++;
                continue;
            }
            air++;
            int bucket = Mth.clamp((pos.getY() - level.getMinBuildHeight()) / 8, 0, heights.length - 1);
            heights[bucket]++;

            Optional<MobSpawnSettings.SpawnerData> pick = level.getBiome(pos).value()
                    .getMobSettings().getMobs(MobCategory.MONSTER).getRandom(random);
            if (pick.isEmpty()) {
                continue;
            }
            EntityType<?> type = pick.get().type;
            int[] gates = tally.computeIfAbsent(type, key -> new int[6]);
            gates[0]++;
            if (!SpawnPlacements.isSpawnPositionOk(type, level, pos)) {
                continue;
            }
            gates[1]++;
            if (!SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, random)) {
                continue;
            }
            gates[2]++;
            if (!level.noCollision(type.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
                continue;
            }
            gates[3]++;
            if (!(type.create(level) instanceof Mob mob)) {
                continue;
            }
            mob.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
            boolean ok = mob.checkSpawnRules(level, MobSpawnType.NATURAL) && mob.checkSpawnObstruction(level);
            mob.discard();
            if (!ok) {
                continue;
            }
            gates[4]++;
            gates[5] += pick.get().minCount;
        }

        report(source, samples, solid, air, heights, tally, level.getMinBuildHeight());
        return 1;
    }

    private static BlockPos randomPosWithin(ServerLevel level, LevelChunk chunk, RandomSource random) {
        ChunkPos pos = chunk.getPos();
        int x = pos.getMinBlockX() + random.nextInt(16);
        int z = pos.getMinBlockZ() + random.nextInt(16);
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
        return new BlockPos(x, Mth.randomBetweenInclusive(random, level.getMinBuildHeight(), top), z);
    }

    private static void report(CommandSourceStack source, int samples, int solid, int air, int[] heights,
                               Map<EntityType<?>, int[]> tally, int minY) {
        say(source, String.format("проб %d: в породе %d (%.1f%%), в пустоте %d (%.1f%%)",
                samples, solid, 100.0 * solid / samples, air, 100.0 * air / samples));
        StringBuilder profile = new StringBuilder("высоты пустоты:");
        for (int i = 0; i < heights.length; i++) {
            if (heights[i] > 0) {
                profile.append(String.format(" y%d..%d=%d", minY + i * 8, minY + i * 8 + 7, heights[i]));
            }
        }
        say(source, profile.toString());
        tally.forEach((type, gates) -> say(source, String.format(
                "%-16s выпал %5d → место %5d → правила %5d → габарит %5d → сам моб %5d ⇒ особей %5d",
                BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath(),
                gates[0], gates[1], gates[2], gates[3], gates[4], gates[5])));
    }

    private static void say(CommandSourceStack source, String line) {
        Gimpanum.LOGGER.info("[spawnprobe] {}", line);
        source.sendSuccess(() -> Component.literal(line), false);
    }
}
