package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.MagicContent;
import com.kirbornu.gimpanum.magic.core.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.ARENA;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.caster;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.expectError;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.root;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.arcane;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.castArcanes;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.still;

/** Изменения контекста: выбор целей, формы, повтор и задержка. */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class ModifierGameTests {

    static final String GLOW = "{\"type\": \"effect\", \"effect\": \"minecraft:glowing\", \"seconds\": 30}";
    static final String GLASS = "{\"type\": \"place_block\", \"block\": \"minecraft:glass\", \"seconds\": -1, "
            + "\"onlyAir\": true}";

    /** Префикс из перечисленных поведений, по порядку. */
    static Arcane prefix(String... behaviors) {
        return arcane("prefix", 10, "{\"prefix\": [" + String.join(", ", behaviors) + "]}");
    }

    static String radius(double r, boolean includeCaster) {
        return "{\"type\": \"target_radius\", \"radius\": " + r + ", \"includeCaster\": " + includeCaster + "}";
    }

    static boolean glows(LivingEntity e) {
        return e.hasEffect(MobEffects.GLOWING);
    }

    // --- target_caster -----------------------------------------------------

    @GameTest(template = ARENA)
    public static void targetCasterOverridesSelection(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        h.assertTrue(castArcanes(p, prefix(radius(3, false), "{\"type\": \"target_caster\"}"), root(GLOW)) != null,
                "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(glows(p) && !glows(pig), "кастер " + glows(p) + ", свинья " + glows(pig));
            h.succeed();
        });
    }

    // --- target_nearest ----------------------------------------------------

    @GameTest(template = ARENA)
    public static void targetNearestPicksClosestExceptCaster(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig near = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        Pig far = still(h, EntityType.PIG, new BlockPos(11, 2, 7));
        h.assertTrue(castArcanes(p, prefix("{\"type\": \"target_nearest\", \"radius\": 8, \"excludeCaster\": true}"),
                root(GLOW)) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(glows(near) && !glows(far) && !glows(p), "ближняя " + glows(near) + ", дальняя "
                    + glows(far) + ", кастер " + glows(p));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void targetNearestFromEntityAnchorSkipsItself(GameTestHelper h) {
        // Цепочка: ближайший к кастеру (свинья A), затем ближайший к ней — не она сама, а свинья B.
        ServerPlayer p = caster(h);
        Pig a = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        Pig b = still(h, EntityType.PIG, new BlockPos(11, 2, 7));
        String nearest = "{\"type\": \"target_nearest\", \"radius\": 8, \"excludeCaster\": true}";
        h.assertTrue(castArcanes(p, prefix(nearest, nearest), root(GLOW)) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(glows(b) && !glows(a), "A " + glows(a) + ", B " + glows(b));
            h.succeed();
        });
    }

    // --- target_filter -----------------------------------------------------

    @GameTest(template = ARENA)
    public static void targetFilterMonstersOnly(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        Zombie zombie = still(h, EntityType.ZOMBIE, new BlockPos(7, 2, 9));
        h.assertTrue(castArcanes(p, prefix(radius(4, true), "{\"type\": \"target_filter\", \"filter\": \"monsters\"}"),
                root(GLOW)) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(glows(zombie) && !glows(pig) && !glows(p), "зомби " + glows(zombie) + ", свинья "
                    + glows(pig) + ", кастер " + glows(p));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void targetFilterNegatedAndByType(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        Zombie zombie = still(h, EntityType.ZOMBIE, new BlockPos(7, 2, 9));
        h.assertTrue(castArcanes(p, prefix(radius(4, true), "{\"type\": \"target_filter\", \"filter\": \"!players\"}",
                "{\"type\": \"target_filter\", \"filter\": \"type:minecraft:pig\"}"), root(GLOW)) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(glows(pig) && !glows(zombie) && !glows(p), "свинья " + glows(pig) + ", зомби "
                    + glows(zombie) + ", кастер " + glows(p));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void targetFilterByTag(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        Zombie zombie = still(h, EntityType.ZOMBIE, new BlockPos(7, 2, 9));
        h.assertTrue(castArcanes(p, prefix(radius(4, true),
                "{\"type\": \"target_filter\", \"filter\": \"tag:minecraft:undead\"}"), root(GLOW)) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(glows(zombie) && !glows(pig), "зомби " + glows(zombie) + ", свинья " + glows(pig));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void targetFilterEmptyDoesNotFallBack(GameTestHelper h) {
        // Фильтр не откатывается: животных нет — значит, никого, а не кастер.
        ServerPlayer p = caster(h);
        Zombie zombie = still(h, EntityType.ZOMBIE, new BlockPos(9, 2, 7));
        h.assertTrue(castArcanes(p, prefix("{\"type\": \"target_filter\", \"filter\": \"animals\"}"), root(GLOW))
                != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(!glows(p) && !glows(zombie), "кто-то засветился");
            h.succeed();
        });
    }

    // --- target_limit ------------------------------------------------------

    @GameTest(template = ARENA)
    public static void targetLimitKeepsNearest(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig a = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        Pig b = still(h, EntityType.PIG, new BlockPos(7, 2, 10));
        Pig c = still(h, EntityType.PIG, new BlockPos(11, 2, 11));
        h.assertTrue(castArcanes(p, prefix(radius(8, false), "{\"type\": \"target_limit\", \"count\": 2}"),
                root(GLOW)) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(glows(a) && glows(b) && !glows(c), "a " + glows(a) + " b " + glows(b) + " c " + glows(c));
            h.succeed();
        });
    }

    // --- shape_line --------------------------------------------------------

    @GameTest(template = ARENA)
    public static void shapeLineAlongLook(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, prefix("{\"type\": \"shape_line\", \"length\": 4, \"width\": 1}"), root(GLASS))
                != null, "каст");
        h.runAfterDelay(2, () -> {
            for (int x = 8; x <= 11; x++) {
                h.assertBlockPresent(Blocks.GLASS, new BlockPos(x, 3, 7));
            }
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(12, 3, 7));
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(9, 3, 8));
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(6, 3, 7));
            h.succeed();
        });
    }

    // --- shape_ring --------------------------------------------------------

    @GameTest(template = ARENA)
    public static void shapeRingThenGround(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, prefix("{\"type\": \"shape_ring\", \"radius\": 3, \"thickness\": 1}",
                "{\"type\": \"shape_ground\", \"maxDepth\": 4}"), root(GLASS)) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(10, 2, 7));
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(4, 2, 7));
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(7, 2, 10));
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(10, 3, 7));
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(8, 2, 7));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void shapeRingAroundEntityCatchesStanders(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig inRing = still(h, EntityType.PIG, new BlockPos(10, 2, 7));
        Pig inside = still(h, EntityType.PIG, new BlockPos(8, 2, 7));
        h.assertTrue(castArcanes(p, prefix("{\"type\": \"target_caster\"}",
                "{\"type\": \"shape_ring\", \"radius\": 3, \"thickness\": 1}"), root(GLOW)) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(glows(inRing) && !glows(inside), "в кольце " + glows(inRing) + ", внутри " + glows(inside));
            h.succeed();
        });
    }

    // --- shape_box ---------------------------------------------------------

    @GameTest(template = ARENA)
    public static void shapeBoxAroundOrigin(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, prefix("{\"type\": \"shape_box\", \"x\": 3, \"y\": 1, \"z\": 3}"), root(GLASS))
                != null, "каст");
        h.runAfterDelay(2, () -> {
            for (int x = 6; x <= 8; x++) {
                for (int z = 6; z <= 8; z++) {
                    h.assertBlockPresent(Blocks.GLASS, new BlockPos(x, 3, z));
                }
            }
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(9, 3, 7));
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(7, 4, 7));
            h.succeed();
        });
    }

    // --- shape_ground ------------------------------------------------------

    @GameTest(template = ARENA)
    public static void shapeGroundDropsSphereToFloor(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, prefix("{\"type\": \"shape_sphere\", \"radius\": 1.5}",
                "{\"type\": \"shape_ground\", \"maxDepth\": 4}"), root(GLASS)) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(8, 2, 7));
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(8, 3, 7));
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(7, 4, 7));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void shapeGroundRespectsMaxDepth(GameTestHelper h) {
        // При maxDepth 0 опускать нельзя: остаётся только то, что уже на полу.
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, prefix("{\"type\": \"shape_sphere\", \"radius\": 1.5}",
                "{\"type\": \"shape_ground\", \"maxDepth\": 0}"), root(GLASS)) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(7, 2, 7));
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(7, 3, 7));
            h.assertBlockNotPresent(Blocks.GLASS, new BlockPos(8, 4, 7));
            h.succeed();
        });
    }

    // --- repeat ------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void repeatAppliesRootSeveralTimes(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        h.assertTrue(castArcanes(p, prefix(radius(3, false), "{\"type\": \"repeat\", \"times\": 3}"),
                root("{\"type\": \"damage\", \"amount\": 1}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            log("repeat: pig " + pig.getHealth());
            h.assertTrue(pig.getHealth() == 7F, "здоровье " + pig.getHealth());
            h.succeed();
        });
    }

    // --- delay -------------------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 80)
    public static void delayPostponesApplication(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        h.assertTrue(castArcanes(p, prefix(radius(3, false), "{\"type\": \"delay\", \"seconds\": 1}"),
                root("{\"type\": \"damage\", \"amount\": 2}")) != null, "каст");
        h.runAfterDelay(10, () -> {
            h.assertTrue(pig.getHealth() == 10F, "сработало раньше: " + pig.getHealth());
            h.assertEntityPresent(MagicContent.SPELL.get());
        });
        h.runAfterDelay(30, () -> {
            h.assertTrue(pig.getHealth() == 8F, "не сработало: " + pig.getHealth());
            h.assertEntityNotPresent(MagicContent.SPELL.get());
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void modifierValidation(GameTestHelper h) {
        expectError(h, Role.PREFIX, "{\"type\": \"target_filter\", \"filter\": \"bats\"}", "нет такого фильтра");
        expectError(h, Role.PREFIX, "{\"type\": \"target_filter\", \"filter\": \"type:minecraft:nope\"}",
                "нет такого существа");
        expectError(h, Role.PREFIX, "{\"type\": \"target_nearest\", \"radius\": 4}", "excludeCaster");
        expectError(h, Role.PREFIX, "{\"type\": \"target_limit\", \"count\": -1}", "должно быть от 0");
        expectError(h, Role.PREFIX, "{\"type\": \"shape_box\", \"x\": 3, \"y\": 1}", "z: нет обязательного числа");
        expectError(h, Role.PREFIX, "{\"type\": \"shape_ring\", \"radius\": 3, \"thickness\": 99}", "должно быть от 0.0");
        expectError(h, Role.PREFIX, "{\"type\": \"repeat\", \"times\": 0}", "должно быть от 1");
        expectError(h, Role.PREFIX, "{\"type\": \"delay\", \"seconds\": -1}", "должно быть от 0.0");
        expectError(h, Role.INTERFIX, "{\"type\": \"shape_ground\", \"maxDepth\": 3}", "не процесс");
        h.succeed();
    }
}
