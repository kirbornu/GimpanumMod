package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.core.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.SnowGolem;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.arcane;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.castArcanes;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.errors;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.player;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.still;

/**
 * Поведения-действия по одному: корнем, с префиксом выбора целей, где нужно.
 *
 * <p>Площадка {@code magicarena} 15×15, пол на высоте 1. Кастер стоит в
 * (7, 2, 7) и смотрит на +X.
 */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class ActionGameTests {

    static final String ARENA = "magicarena";
    static final BlockPos CENTER = new BlockPos(7, 2, 7);

    /** Префикс: все живые в радиусе, кроме кастера. */
    static Arcane radius(double r) {
        return arcane("radius", 10, "{\"prefix\": [{\"type\": \"target_radius\", \"radius\": " + r
                + ", \"includeCaster\": false}]}");
    }

    static Arcane root(String behaviors) {
        return arcane("root", 50, "{\"root\": [" + behaviors + "]}");
    }

    static ServerPlayer caster(GameTestHelper h) {
        return player(h, true, CENTER);
    }

    // --- knockback ---------------------------------------------------------

    @GameTest(template = ARENA)
    public static void knockbackPushesAway(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(9, 2, 7));
        double x0 = pig.getX();
        h.assertTrue(castArcanes(p, radius(3), root("{\"type\": \"knockback\", \"strength\": 1.5}")) != null, "каст");
        h.runAfterDelay(12, () -> {
            log("knockback: x " + x0 + " → " + pig.getX());
            h.assertTrue(pig.getX() - x0 > 1.0, "свинью не отбросило: " + (pig.getX() - x0));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void knockbackAlongLookWhenUnderneath(GameTestHelper h) {
        // Кастер не в мире; свинья стоит прямо под сущностью-заклинанием и
        // становится целью по умолчанию — её несёт по взгляду, на +X.
        ServerPlayer p = player(h, false, CENTER);
        Pig pig = h.spawn(EntityType.PIG, CENTER);
        double x0 = pig.getX();
        double z0 = pig.getZ();
        h.assertTrue(castArcanes(p, root("{\"type\": \"knockback\", \"strength\": 1.5}")) != null, "каст");
        h.runAfterDelay(12, () -> {
            log("knockback under: dx " + (pig.getX() - x0) + " dz " + (pig.getZ() - z0));
            h.assertTrue(pig.getX() - x0 > 1.0, "не по взгляду: " + (pig.getX() - x0));
            h.succeed();
        });
    }

    // --- pull --------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void pullDrawsIn(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(12, 2, 7));
        double x0 = pig.getX();
        h.assertTrue(castArcanes(p, radius(6), root("{\"type\": \"pull\", \"strength\": 1.5}")) != null, "каст");
        h.runAfterDelay(12, () -> {
            log("pull: x " + x0 + " → " + pig.getX());
            h.assertTrue(x0 - pig.getX() > 1.0, "свинью не притянуло: " + (x0 - pig.getX()));
            h.succeed();
        });
    }

    // --- launch ------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void launchThrowsUp(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(9, 2, 7));
        double y0 = pig.getY();
        h.assertTrue(castArcanes(p, radius(3), root("{\"type\": \"launch\", \"strength\": 1.0}")) != null, "каст");
        h.runAfterDelay(8, () -> {
            log("launch: y " + y0 + " → " + pig.getY());
            h.assertTrue(pig.getY() - y0 > 2.0, "свинью не подбросило: " + (pig.getY() - y0));
            h.succeed();
        });
    }

    // --- freeze ------------------------------------------------------------

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void freezeFreezesThenThaws(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        SnowGolem golem = still(h, EntityType.SNOW_GOLEM, new BlockPos(7, 2, 9));
        float health = pig.getHealth();
        h.assertTrue(castArcanes(p, radius(3), root("{\"type\": \"freeze\", \"seconds\": 2}")) != null, "каст");
        h.runAfterDelay(3, () -> {
            h.assertTrue(pig.isFullyFrozen(), "свинья не замёрзла: " + pig.getTicksFrozen());
            h.assertTrue(golem.getTicksFrozen() == 0, "снеговик замёрз");
        });
        h.runAfterDelay(50, () -> h.assertTrue(pig.getHealth() < health, "холод не ранил"));
        h.runAfterDelay(120, () -> {
            log("freeze: frozen " + pig.getTicksFrozen() + " health " + health + " → " + pig.getHealth());
            h.assertTrue(pig.getTicksFrozen() == 0, "не оттаяла: " + pig.getTicksFrozen());
            h.succeed();
        });
    }

    // --- feed --------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void feedFeedsPlayersOnly(GameTestHelper h) {
        ServerPlayer p = caster(h);
        p.getFoodData().setFoodLevel(10);
        p.getFoodData().setSaturation(0);
        still(h, EntityType.PIG, new BlockPos(8, 2, 7));
        h.assertTrue(castArcanes(p,
                arcane("radius", 10, "{\"prefix\": [{\"type\": \"target_radius\", \"radius\": 3, \"includeCaster\": true}]}"),
                root("{\"type\": \"feed\", \"food\": 4, \"saturation\": 2.5}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            log("feed: food " + p.getFoodData().getFoodLevel() + " sat " + p.getFoodData().getSaturationLevel());
            h.assertTrue(p.getFoodData().getFoodLevel() == 14, "еда " + p.getFoodData().getFoodLevel());
            h.assertTrue(p.getFoodData().getSaturationLevel() == 2.5F, "насыщенность");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void feedRespectsLimits(GameTestHelper h) {
        ServerPlayer p = caster(h);
        p.getFoodData().setFoodLevel(18);
        p.getFoodData().setSaturation(17);
        h.assertTrue(castArcanes(p, root("{\"type\": \"feed\", \"food\": 6, \"saturation\": 6}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(p.getFoodData().getFoodLevel() == 20, "еда " + p.getFoodData().getFoodLevel());
            h.assertTrue(p.getFoodData().getSaturationLevel() == 20F, "насыщенность "
                    + p.getFoodData().getSaturationLevel());
            h.succeed();
        });
    }

    // --- absorb ------------------------------------------------------------

    @GameTest(template = ARENA)
    public static void absorbGivesGoldenHearts(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.assertTrue(castArcanes(p, root("{\"type\": \"absorb\", \"amount\": 6, \"seconds\": 10}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            log("absorb: " + p.getAbsorptionAmount() + " effect " + p.getEffect(MobEffects.ABSORPTION));
            h.assertTrue(p.getAbsorptionAmount() == 6F, "поглощение " + p.getAbsorptionAmount());
            h.assertTrue(p.hasEffect(MobEffects.ABSORPTION), "нет эффекта");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, timeoutTicks = 120)
    public static void absorbExpiresWithEffect(GameTestHelper h) {
        ServerPlayer p = player(h, false, CENTER);
        Pig pig = still(h, EntityType.PIG, CENTER);
        h.assertTrue(castArcanes(p, root("{\"type\": \"absorb\", \"amount\": 3, \"seconds\": 2}")) != null, "каст");
        h.runAfterDelay(2, () -> h.assertTrue(pig.getAbsorptionAmount() == 3F, "поглощение " + pig.getAbsorptionAmount()));
        h.runAfterDelay(60, () -> {
            h.assertTrue(pig.getAbsorptionAmount() == 0F, "не исчезло: " + pig.getAbsorptionAmount());
            h.succeed();
        });
    }

    // --- extinguish --------------------------------------------------------

    @GameTest(template = ARENA)
    public static void extinguishOnlyPutsOutFire(GameTestHelper h) {
        ServerPlayer p = caster(h);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        pig.igniteForSeconds(10);
        pig.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
        h.assertTrue(castArcanes(p, radius(3), root("{\"type\": \"extinguish\"}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(!pig.isOnFire(), "горит");
            h.assertTrue(pig.hasEffect(MobEffects.POISON), "яд сняли — не должно");
            h.succeed();
        });
    }

    // --- проверка параметров -----------------------------------------------

    @GameTest(template = ARENA)
    public static void movementAndStateValidation(GameTestHelper h) {
        expectError(h, Role.ROOT, "{\"type\": \"knockback\", \"strength\": -1}", "должно быть от 0.0 до 10.0");
        expectError(h, Role.ROOT, "{\"type\": \"pull\"}", "нет обязательного числа");
        expectError(h, Role.INTERFIX, "{\"type\": \"launch\", \"strength\": 1}", "не процесс");
        expectError(h, Role.ROOT, "{\"type\": \"feed\", \"food\": 1.5, \"saturation\": 1}", "ожидалось целое число");
        expectError(h, Role.ROOT, "{\"type\": \"extinguish\", \"seconds\": 1}", "неизвестное поле");
        expectError(h, Role.ROOT, "{\"type\": \"absorb\", \"amount\": 4}", "seconds: нет обязательного числа");
        h.assertTrue(errors(Role.SUFFIX, "{\"type\": \"freeze\", \"seconds\": 3}").isEmpty(), "freeze в суффиксе");
        h.succeed();
    }

    static void expectError(GameTestHelper h, Role role, String behavior, String fragment) {
        List<String> errors = errors(role, behavior);
        log("validation " + behavior + " → " + errors);
        h.assertTrue(errors.stream().anyMatch(e -> e.contains(fragment)), behavior + " → " + errors);
    }
}
