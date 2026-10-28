package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.MagicContent;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.spell.SpellEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.ARENA;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.CENTER;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.caster;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.expectError;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.arcane;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.castArcanes;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.castFrom;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.still;
import static com.kirbornu.gimpanum.magic.gametest.ModifierGameTests.GLASS;
import static com.kirbornu.gimpanum.magic.gametest.ModifierGameTests.GLOW;
import static com.kirbornu.gimpanum.magic.gametest.ModifierGameTests.glows;

/**
 * Процессы интерфикса. Заклинание в каждом тесте — «пустой корень,
 * интерфикс, стекло в блоке сущности»: по стеклу видно, где сущность
 * закончила интерфикс, а по её положению — как она туда шла.
 */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class ProcessGameTests {

    /** Корень без поведений — только чтобы был интерфикс. */
    static final Arcane EMPTY_ROOT = arcane("empty", 50, "{}");

    /** Префикс «блок, где сущность» и корень «стекло» — отметка конца интерфикса. */
    static final Arcane HERE = arcane("here", 30,
            "{\"prefix\": [{\"type\": \"shape_box\", \"x\": 1, \"y\": 1, \"z\": 1}]}");
    static final Arcane GLASS_ROOT = arcane("glass", 50, "{\"root\": [" + GLASS + "]}");

    static Arcane interfix(String... behaviors) {
        return arcane("interfix", 20, "{\"interfix\": [" + String.join(", ", behaviors) + "]}");
    }

    /** Корень, интерфикс, затем стекло там, где сущность его закончила. */
    static SpellEntity castThrough(ServerPlayer p, String... interfix) {
        return castArcanes(p, EMPTY_ROOT, interfix(interfix), HERE, GLASS_ROOT);
    }

    static Vec3 spellAt(GameTestHelper h) {
        List<SpellEntity> spells = h.getEntities(MagicContent.SPELL.get());
        h.assertTrue(spells.size() == 1, "сущностей заклинания: " + spells.size());
        return spells.get(0).position().subtract(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)));
    }

    // --- move_forward ------------------------------------------------------

    @GameTest(template = ARENA)
    public static void moveForwardTravelsAlongLook(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castThrough(p, "{\"type\": \"move_forward\", \"distance\": 4, \"speed\": 0.5}") != null, "каст");
        h.runAfterDelay(5, () -> {
            Vec3 at = spellAt(h);
            log("move_forward t5: " + at);
            h.assertTrue(at.x > 8.6 && at.x < 10.0, "x " + at.x);
        });
        h.runAfterDelay(20, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(11, 3, 7));
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(12, 3, 7));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void moveForwardStopsAtWall(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.setBlock(new BlockPos(9, 3, 7), Blocks.DIRT);
        h.assertTrue(castThrough(p, "{\"type\": \"move_forward\", \"distance\": 6, \"speed\": 0.5}") != null, "каст");
        h.runAfterDelay(20, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(8, 3, 7));
            h.assertBlockPresent(Blocks.DIRT, new BlockPos(9, 3, 7));
            h.succeed();
        });
    }

    // --- rise --------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void riseGoesUpUntilCeiling(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.setBlock(new BlockPos(7, 6, 7), Blocks.STONE);
        h.assertTrue(castThrough(p, "{\"type\": \"rise\", \"distance\": 5, \"speed\": 0.5}") != null, "каст");
        h.runAfterDelay(20, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(7, 5, 7));
            h.succeed();
        });
    }

    // --- teleport_forward --------------------------------------------------

    @GameTest(template = ARENA)
    public static void teleportForwardJumpsInstantly(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castThrough(p, "{\"type\": \"teleport_forward\", \"distance\": 3}") != null, "каст");
        h.runAfterDelay(4, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(10, 3, 7));
            h.succeed();
        });
    }

    // --- follow_caster -----------------------------------------------------

    @GameTest(template = ARENA)
    public static void followCasterKeepsUp(GameTestHelper h) {
        Zombie zombie = still(h, EntityType.ZOMBIE, CENTER);
        h.assertTrue(castFrom(zombie, EMPTY_ROOT, interfix("{\"type\": \"follow_caster\", \"seconds\": 2}"), HERE,
                GLASS_ROOT) != null, "каст");
        h.runAfterDelay(5, () -> {
            BlockPos to = h.absolutePos(new BlockPos(11, 2, 10));
            zombie.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        });
        h.runAfterDelay(10, () -> {
            Vec3 at = spellAt(h);
            log("follow: " + at);
            h.assertTrue(Math.abs(at.x - 11.5) < 0.01 && Math.abs(at.z - 10.5) < 0.01, "не следует: " + at);
        });
        h.runAfterDelay(50, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(11, 3, 10));
            h.succeed();
        });
    }

    // --- orbit -------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void orbitCirclesCaster(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castThrough(p, "{\"type\": \"orbit\", \"radius\": 2, \"seconds\": 3, \"period\": 1}") != null,
                "каст");
        Vec3 eye = p.getEyePosition().subtract(Vec3.atLowerCornerOf(h.absolutePos(BlockPos.ZERO)));
        Vec3[] seen = new Vec3[2];
        h.runAfterDelay(6, () -> seen[0] = spellAt(h));
        h.runAfterDelay(11, () -> seen[1] = spellAt(h));
        h.runAfterDelay(12, () -> {
            for (Vec3 at : seen) {
                double r = Math.sqrt((at.x - eye.x) * (at.x - eye.x) + (at.z - eye.z) * (at.z - eye.z));
                log("orbit: " + at + " r " + r);
                h.assertTrue(Math.abs(r - 2) < 0.01 && Math.abs(at.y - eye.y) < 0.01, "не на окружности: " + at);
            }
            // За 5 тиков при обороте за секунду — четверть круга.
            h.assertTrue(seen[0].distanceTo(seen[1]) > 2.5, "не движется: " + seen[0] + " → " + seen[1]);
            h.succeed();
        });
    }

    // --- wait_for_target ---------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void waitForTargetTriggersOnApproach(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castThrough(p, "{\"type\": \"wait_for_target\", \"radius\": 2, \"maxSeconds\": 30, "
                + "\"excludeCaster\": true}") != null, "каст");
        h.runAfterDelay(20, () -> {
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(7, 3, 7));
            still(h, EntityType.PIG, new BlockPos(8, 2, 7));
        });
        h.runAfterDelay(40, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(7, 3, 7));
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void waitForTargetGivesUpAfterMax(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castThrough(p, "{\"type\": \"wait_for_target\", \"radius\": 2, \"maxSeconds\": 1, "
                + "\"excludeCaster\": true}") != null, "каст");
        h.runAfterDelay(10, () -> h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(7, 3, 7)));
        h.runAfterDelay(30, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(7, 3, 7));
            h.succeed();
        });
    }

    // --- aura --------------------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void auraPulsesWhileInterfixLasts(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        Pig far = still(h, EntityType.PIG, new BlockPos(12, 2, 7));
        h.assertTrue(castThrough(p, "{\"type\": \"wait\", \"seconds\": 2}",
                "{\"type\": \"aura\", \"radius\": 3, \"every\": 0.5, \"excludeCaster\": true, "
                        + "\"action\": {\"type\": \"damage\", \"amount\": 1}}") != null, "каст");
        h.runAfterDelay(60, () -> {
            log("aura: pig " + pig.getHealth() + " far " + far.getHealth());
            // Импульсы на 0; 0,5; 1; 1,5 с — четыре удара за двухсекундный интерфикс.
            h.assertTrue(pig.getHealth() == 6F, "здоровье " + pig.getHealth());
            h.assertTrue(far.getHealth() == 10F, "задело дальнюю");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 100)
    public static void auraExcludesCaster(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        h.assertTrue(castThrough(p, "{\"type\": \"wait\", \"seconds\": 1}",
                "{\"type\": \"aura\", \"radius\": 3, \"every\": 0.5, \"excludeCaster\": true, \"action\": " + GLOW
                        + "}") != null, "каст");
        h.runAfterDelay(10, () -> {
            h.assertTrue(glows(pig) && !glows(p), "свинья " + glows(pig) + ", кастер " + glows(p));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void processValidation(GameTestHelper h) {
        expectError(h, Role.INTERFIX, "{\"type\": \"aura\", \"radius\": 3, \"every\": 1, \"excludeCaster\": true, "
                + "\"action\": {\"type\": \"echo_root\", \"interval\": 1, \"duration\": 1}}", "не echo_root");
        expectError(h, Role.INTERFIX, "{\"type\": \"aura\", \"radius\": 3, \"every\": 1, \"excludeCaster\": true, "
                + "\"action\": {\"type\": \"wait\", \"seconds\": 1}}", "здесь нужно действие");
        expectError(h, Role.INTERFIX, "{\"type\": \"aura\", \"radius\": 3, \"every\": 1, \"excludeCaster\": true, "
                + "\"action\": {\"type\": \"damage\"}}", "action.amount: нет обязательного числа");
        expectError(h, Role.INTERFIX, "{\"type\": \"aura\", \"radius\": 3, \"every\": 1, \"excludeCaster\": true}",
                "action: нет обязательного объекта");
        expectError(h, Role.ROOT, "{\"type\": \"move_forward\", \"distance\": 3, \"speed\": 1}", "процесс");
        expectError(h, Role.INTERFIX, "{\"type\": \"orbit\", \"radius\": 2, \"seconds\": 1}", "period");
        expectError(h, Role.INTERFIX, "{\"type\": \"rise\", \"distance\": 3, \"speed\": 0}", "должно быть от 0.01");
        h.succeed();
    }
}
