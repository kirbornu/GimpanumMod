package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.core.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.ARENA;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.CENTER;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.caster;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.expectError;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.radius;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.root;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.arcane;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.castArcanes;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.still;

/** Поведения-действия, вторая группа: огонь, взрыв, молния, перемещения, эффекты, вытягивание. */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class ActionGameTests2 {

    /** Префикс-форма: шар вокруг сущности-заклинания. */
    static com.kirbornu.gimpanum.magic.Arcane sphere(double r) {
        return arcane("sphere", 10, "{\"prefix\": [{\"type\": \"shape_sphere\", \"radius\": " + r + "}]}");
    }

    // --- set_fire_blocks ---------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void setFireBlocksBurnsFloorAndExpires(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, sphere(2.5), root("{\"type\": \"set_fire_blocks\", \"seconds\": 1}")) != null,
                "каст");
        h.runAfterDelay(2, () -> {
            h.assertBlockPresent(Blocks.FIRE, new BlockPos(8, 2, 7));
            // Над полом опоры нет — огня там быть не может.
            h.assertBlockNotPresent(Blocks.FIRE, new BlockPos(8, 3, 7));
        });
        h.runAfterDelay(50, () -> {
            h.assertBlockNotPresent(Blocks.FIRE, new BlockPos(8, 2, 7));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void setFireBlocksRespectsClaims(GameTestHelper h) {
        ServerPlayer p = caster(h);
        TestClaims.protect(h.absolutePos(new BlockPos(8, 2, 7)));
        h.assertTrue(castArcanes(p, sphere(2.5), root("{\"type\": \"set_fire_blocks\", \"seconds\": -1}")) != null,
                "каст");
        h.runAfterDelay(2, () -> {
            h.assertBlockNotPresent(Blocks.FIRE, new BlockPos(8, 2, 7));
            h.assertBlockPresent(Blocks.FIRE, new BlockPos(6, 2, 7));
            h.succeed();
        });
    }

    // --- explode -----------------------------------------------------------

    @GameTest(template = ARENA)
    public static void explodeWithoutBreakingBlocks(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(10, 2, 7));
        float health = pig.getHealth();
        h.assertTrue(castArcanes(p, radius(4), root("{\"type\": \"explode\", \"power\": 2, \"breakBlocks\": false}"))
                != null, "каст");
        h.runAfterDelay(3, () -> {
            log("explode keep: pig " + health + " → " + pig.getHealth());
            h.assertTrue(pig.getHealth() < health || pig.isDeadOrDying(), "взрыв не ранил");
            h.assertBlockPresent(Blocks.STONE, new BlockPos(10, 1, 7));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void explodeBreaksBlocksButNotClaimed(GameTestHelper h) {
        ServerPlayer p = caster(h);
        still(h, EntityType.PIG, new BlockPos(10, 2, 7));
        TestClaims.protect(h.absolutePos(new BlockPos(10, 1, 8)));
        h.assertTrue(castArcanes(p, radius(4), root("{\"type\": \"explode\", \"power\": 4, \"breakBlocks\": true}"))
                != null, "каст");
        h.runAfterDelay(3, () -> {
            h.assertBlockNotPresent(Blocks.STONE, new BlockPos(10, 1, 7));
            h.assertBlockPresent(Blocks.STONE, new BlockPos(10, 1, 8));
            h.succeed();
        });
    }

    // --- lightning ---------------------------------------------------------

    @GameTest(template = ARENA)
    public static void lightningStrikesTargets(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Cow cow = still(h, EntityType.COW, new BlockPos(10, 2, 7));
        float health = cow.getHealth();
        h.assertTrue(castArcanes(p, radius(4), root("{\"type\": \"lightning\"}")) != null, "каст");
        h.runAfterDelay(1, () -> log("lightning: bolts " + h.getEntities(EntityType.LIGHTNING_BOLT).size()
                + " at " + h.getEntities(EntityType.LIGHTNING_BOLT).stream().map(e -> e.position()).toList()
                + " cow " + cow.position()));
        h.runAfterDelay(6, () -> {
            log("lightning: cow " + health + " → " + cow.getHealth() + " fire " + cow.isOnFire());
            h.assertTrue(cow.getHealth() < health, "молния не ранила");
            h.succeed();
        });
    }

    // --- teleport_to_origin ------------------------------------------------

    @GameTest(template = ARENA)
    public static void teleportToOriginBringsTargets(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(11, 2, 10));
        h.assertTrue(castArcanes(p, radius(6), root("{\"type\": \"teleport_to_origin\"}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            log("teleport: pig " + pig.position() + " caster " + p.position());
            h.assertTrue(Math.abs(pig.getX() - p.getX()) < 0.01 && Math.abs(pig.getZ() - p.getZ()) < 0.01,
                    "свинья не у сущности: " + pig.position());
            h.succeed();
        });
    }

    // --- swap_caster -------------------------------------------------------

    @GameTest(template = ARENA)
    public static void swapCasterSwapsPlaces(GameTestHelper h) {
        // Кастер — зомби: поддельного игрока телепорт не двигает (см. MagicTestKit.castFrom).
        net.minecraft.world.entity.monster.Zombie caster = still(h, EntityType.ZOMBIE, CENTER);
        Pig pig = still(h, EntityType.PIG, new BlockPos(10, 2, 9));
        net.minecraft.world.phys.Vec3 a = caster.position();
        net.minecraft.world.phys.Vec3 b = pig.position();
        h.assertTrue(MagicTestKit.castFrom(caster, radius(4), root("{\"type\": \"swap_caster\"}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            log("swap: caster " + a + " → " + caster.position() + ", pig " + b + " → " + pig.position());
            h.assertTrue(caster.position().distanceTo(b) < 0.01, "кастер не на месте свиньи");
            h.assertTrue(pig.position().distanceTo(a) < 0.01, "свинья не на месте кастера");
            h.succeed();
        });
    }

    // --- remove_effect, steal_effects --------------------------------------

    @GameTest(template = ARENA)
    public static void removeEffectRemovesOnlyThatOne(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        pig.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600));
        pig.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
        h.assertTrue(castArcanes(p, radius(3),
                root("{\"type\": \"remove_effect\", \"effect\": \"minecraft:speed\"}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(!pig.hasEffect(MobEffects.MOVEMENT_SPEED), "скорость осталась");
            h.assertTrue(pig.hasEffect(MobEffects.POISON), "яд сняли");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void stealEffectsMovesThemToCaster(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        pig.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 600, 1));
        h.assertTrue(castArcanes(p, radius(3), root("{\"type\": \"steal_effects\"}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            MobEffectInstance stolen = p.getEffect(MobEffects.MOVEMENT_SPEED);
            log("steal: " + stolen);
            h.assertTrue(stolen != null && stolen.getAmplifier() == 1, "у кастера нет скорости II");
            h.assertTrue(!pig.hasEffect(MobEffects.MOVEMENT_SPEED), "у свиньи осталась");
            h.succeed();
        });
    }

    // --- drain -------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void drainHealsCasterByDamageDone(GameTestHelper h) {
        ServerPlayer p = caster(h);
        p.setHealth(10);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        Pig other = still(h, EntityType.PIG, new BlockPos(7, 2, 9));
        h.assertTrue(castArcanes(p, radius(3), root("{\"type\": \"drain\", \"amount\": 3}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            log("drain: pig " + pig.getHealth() + " other " + other.getHealth() + " caster " + p.getHealth());
            h.assertTrue(pig.getHealth() == 7F && other.getHealth() == 7F, "урон");
            h.assertTrue(p.getHealth() == 16F, "кастер вылечен на " + (p.getHealth() - 10));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void spellDamageIgnoresHitCooldown(GameTestHelper h) {
        // Второй удар через 3 тика — внутри ванильной неуязвимости после удара
        // (10 тиков). Урон заклинания обязан пройти оба раза.
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        h.assertTrue(castArcanes(p, radius(3), root("{\"type\": \"damage\", \"amount\": 2}")) != null, "каст");
        h.runAfterDelay(3, () -> {
            h.assertTrue(pig.getHealth() == 8F, "первый удар: " + pig.getHealth());
            h.assertTrue(castArcanes(p, radius(3), root("{\"type\": \"damage\", \"amount\": 2}")) != null, "каст");
        });
        h.runAfterDelay(6, () -> {
            h.assertTrue(pig.getHealth() == 6F, "второй удар срезан неуязвимостью: " + pig.getHealth());
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void group2Validation(GameTestHelper h) {
        expectError(h, Role.ROOT, "{\"type\": \"set_fire_blocks\", \"seconds\": 0}", "-1 — навсегда");
        expectError(h, Role.ROOT, "{\"type\": \"explode\", \"power\": 2}", "breakBlocks: нет обязательного true/false");
        expectError(h, Role.ROOT, "{\"type\": \"explode\", \"power\": 99, \"breakBlocks\": false}", "должно быть от 0.0 до 16.0");
        expectError(h, Role.ROOT, "{\"type\": \"remove_effect\", \"effect\": \"minecraft:nope\"}", "нет такого эффекта");
        expectError(h, Role.ROOT, "{\"type\": \"drain\"}", "нет обязательного числа");
        expectError(h, Role.ROOT, "{\"type\": \"lightning\", \"power\": 1}", "неизвестное поле");
        h.succeed();
    }
}
