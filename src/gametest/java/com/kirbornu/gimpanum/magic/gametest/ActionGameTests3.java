package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.core.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.ARENA;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.CENTER;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.caster;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.expectError;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.root;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests2.sphere;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.castArcanes;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;

/** Поведения-действия, третья группа: блоки и порождения. */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class ActionGameTests3 {

    // --- place_block -------------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void placeBlockOnlyAirAndExpires(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.setBlock(new BlockPos(8, 2, 8), Blocks.SNOW);
        h.assertTrue(castArcanes(p, sphere(2.5),
                root("{\"type\": \"place_block\", \"block\": \"minecraft:glass\", \"seconds\": 1, \"onlyAir\": true}"))
                != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(8, 3, 7));
            h.assertBlockPresent(Blocks.SNOW, new BlockPos(8, 2, 8));
            h.assertBlockPresent(Blocks.STONE, new BlockPos(8, 1, 7));
        });
        h.runAfterDelay(50, () -> {
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(8, 3, 7));
            h.assertBlockPresent(Blocks.SNOW, new BlockPos(8, 2, 8));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void placeBlockReplacesReplaceableAndChecksSurvival(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.setBlock(new BlockPos(8, 2, 8), Blocks.SNOW);
        TestClaims.protect(h.absolutePos(new BlockPos(6, 2, 7)));
        h.assertTrue(castArcanes(p, sphere(2.5),
                root("{\"type\": \"place_block\", \"block\": \"minecraft:torch\", \"seconds\": -1, \"onlyAir\": false}"))
                != null, "каст");
        h.runAfterDelay(2, () -> {
            // На полу факел держится, в воздухе — нет; снег заменён; приват не тронут.
            h.assertBlockPresent(Blocks.TORCH, new BlockPos(8, 2, 7));
            h.assertBlockPresent(Blocks.TORCH, new BlockPos(8, 2, 8));
            h.assertBlockNotPresent(Blocks.TORCH, new BlockPos(8, 3, 7));
            h.assertBlockNotPresent(Blocks.TORCH, new BlockPos(6, 2, 7));
            List<?> items = h.getEntities(EntityType.ITEM);
            h.assertTrue(items.isEmpty(), "выпали предметы: " + items.size());
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void placeBlockParsesStateProperties(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, sphere(1.5),
                root("{\"type\": \"place_block\", \"block\": \"minecraft:oak_log[axis=x]\", \"seconds\": -1, "
                        + "\"onlyAir\": true}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            var state = h.getBlockState(new BlockPos(7, 3, 7));
            log("place_block state: " + state);
            h.assertTrue(state.is(Blocks.OAK_LOG)
                    && state.getValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS)
                    == net.minecraft.core.Direction.Axis.X, "не то состояние: " + state);
            h.succeed();
        });
    }

    // --- freeze_water ------------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void freezeWaterFreezesSourcesOnly(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.setBlock(new BlockPos(9, 2, 7), Blocks.WATER);
        h.runAfterDelay(10, () -> {
            // За 10 тиков вода растеклась: рядом текучая, она не замёрзнет.
            h.assertTrue(castArcanes(p, sphere(3),
                    root("{\"type\": \"freeze_water\", \"seconds\": 1}")) != null, "каст");
        });
        h.runAfterDelay(13, () -> {
            h.assertBlockPresent(Blocks.ICE, new BlockPos(9, 2, 7));
            h.assertBlockNotPresent(Blocks.ICE, new BlockPos(9, 2, 8));
        });
        h.runAfterDelay(60, () -> {
            h.assertBlockPresent(Blocks.WATER, new BlockPos(9, 2, 7));
            h.succeed();
        });
    }

    // --- grow --------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void growAgesCrops(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.setBlock(new BlockPos(9, 1, 7), Blocks.FARMLAND);
        h.setBlock(new BlockPos(9, 2, 7), Blocks.WHEAT);
        h.assertTrue(castArcanes(p, sphere(3), root("{\"type\": \"grow\"}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            var state = h.getBlockState(new BlockPos(9, 2, 7));
            log("grow: " + state);
            h.assertTrue(state.is(Blocks.WHEAT) && state.getValue(CropBlock.AGE) > 0, "пшеница не выросла: " + state);
            h.succeed();
        });
    }

    // --- break_blocks ------------------------------------------------------

    @GameTest(template = ARENA)
    public static void breakBlocksByHardnessAndClaims(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.setBlock(new BlockPos(8, 2, 7), Blocks.DIRT);
        h.setBlock(new BlockPos(6, 2, 7), Blocks.DIRT);
        h.setBlock(new BlockPos(7, 2, 8), Blocks.OBSIDIAN);
        TestClaims.protect(h.absolutePos(new BlockPos(6, 2, 7)));
        h.assertTrue(castArcanes(p, sphere(2.5),
                root("{\"type\": \"break_blocks\", \"maxHardness\": 1.0, \"drop\": true}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertBlockNotPresent(Blocks.DIRT, new BlockPos(8, 2, 7));
            h.assertBlockPresent(Blocks.DIRT, new BlockPos(6, 2, 7));
            h.assertBlockPresent(Blocks.OBSIDIAN, new BlockPos(7, 2, 8));
            h.assertBlockPresent(Blocks.STONE, new BlockPos(8, 1, 7));
            h.assertItemEntityPresent(net.minecraft.world.item.Items.DIRT, new BlockPos(8, 2, 7), 2.0);
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void breakBlocksWithoutDrops(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.setBlock(new BlockPos(8, 2, 7), Blocks.DIRT);
        h.assertTrue(castArcanes(p, sphere(2.5),
                root("{\"type\": \"break_blocks\", \"maxHardness\": 1.0, \"drop\": false}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertBlockNotPresent(Blocks.DIRT, new BlockPos(8, 2, 7));
            h.assertTrue(h.getEntities(EntityType.ITEM).isEmpty(), "выпали предметы");
            h.succeed();
        });
    }

    // --- summon ------------------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void summonForAWhile(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, root("{\"type\": \"summon\", \"entity\": \"minecraft:pig\", \"seconds\": 1}"))
                != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getEntities(EntityType.PIG).size() == 1, "свиней: " + h.getEntities(EntityType.PIG).size());
            h.assertEntityPresent(EntityType.PIG, CENTER);
        });
        h.runAfterDelay(50, () -> {
            h.assertTrue(h.getEntities(EntityType.PIG).isEmpty(), "свинья не исчезла");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void summonedDropNothing(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, root("{\"type\": \"summon\", \"entity\": \"minecraft:cow\", \"seconds\": -1}"))
                != null, "каст");
        h.runAfterDelay(2, () -> {
            List<Cow> cows = h.getEntities(EntityType.COW);
            h.assertTrue(cows.size() == 1, "коров: " + cows.size());
            cows.get(0).kill();
        });
        h.runAfterDelay(25, () -> {
            h.assertTrue(h.getEntities(EntityType.ITEM).isEmpty(), "корова что-то уронила");
            h.assertTrue(h.getEntities(EntityType.EXPERIENCE_ORB).isEmpty(), "корова дала опыт");
            h.succeed();
        });
    }

    // --- spawn_projectile --------------------------------------------------

    @GameTest(template = ARENA)
    public static void spawnProjectileShootsAlongLook(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, root("{\"type\": \"spawn_projectile\", \"entity\": \"minecraft:arrow\", "
                + "\"speed\": 1.5}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            List<net.minecraft.world.entity.projectile.Arrow> arrows = h.getEntities(EntityType.ARROW);
            h.assertTrue(arrows.size() == 1, "стрел: " + arrows.size());
            var arrow = arrows.get(0);
            log("arrow: v " + arrow.getDeltaMovement() + " owner " + arrow.getOwner());
            h.assertTrue(arrow.getDeltaMovement().x > 1.0, "летит не туда: " + arrow.getDeltaMovement());
            h.assertTrue(arrow.pickup == AbstractArrow.Pickup.DISALLOWED, "стрелу можно подобрать");
            h.assertTrue(arrow.getOwner() == p, "хозяин не кастер");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void spellFireballDoesNotBreakBlocks(GameTestHelper h) {
        ServerPlayer p = caster(h);
        for (int y = 2; y <= 5; y++) {
            for (int z = 5; z <= 9; z++) {
                h.setBlock(new BlockPos(11, y, z), Blocks.DIRT);
            }
        }
        h.assertTrue(castArcanes(p, root("{\"type\": \"spawn_projectile\", \"entity\": \"minecraft:fireball\", "
                + "\"speed\": 1.0}")) != null, "каст");
        h.runAfterDelay(40, () -> {
            h.assertTrue(h.getEntities(EntityType.FIREBALL).isEmpty(), "шар не долетел");
            for (int y = 2; y <= 5; y++) {
                for (int z = 5; z <= 9; z++) {
                    h.assertBlockPresent(Blocks.DIRT, new BlockPos(11, y, z));
                }
            }
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void nonProjectileIsRefusedAtRuntime(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, root("{\"type\": \"spawn_projectile\", \"entity\": \"minecraft:pig\", "
                + "\"speed\": 1.0}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getEntities(EntityType.PIG).isEmpty(), "свинью выпустило как снаряд");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void group3Validation(GameTestHelper h) {
        expectError(h, Role.ROOT, "{\"type\": \"place_block\", \"block\": \"minecraft:nope\", \"seconds\": -1, "
                + "\"onlyAir\": true}", "не разобрать блок");
        expectError(h, Role.ROOT, "{\"type\": \"place_block\", \"block\": \"minecraft:oak_log[axis=q]\", "
                + "\"seconds\": -1, \"onlyAir\": true}", "не разобрать блок");
        expectError(h, Role.ROOT, "{\"type\": \"freeze_water\", \"seconds\": 0}", "-1 — навсегда");
        expectError(h, Role.ROOT, "{\"type\": \"break_blocks\", \"maxHardness\": 2}", "drop: нет обязательного");
        expectError(h, Role.ROOT, "{\"type\": \"summon\", \"entity\": \"minecraft:player\", \"seconds\": 1}",
                "нельзя призвать");
        expectError(h, Role.ROOT, "{\"type\": \"summon\", \"entity\": \"minecraft:nope\", \"seconds\": 1}",
                "нет такого существа");
        expectError(h, Role.INTERFIX, "{\"type\": \"grow\"}", "не процесс");
        h.succeed();
    }
}
