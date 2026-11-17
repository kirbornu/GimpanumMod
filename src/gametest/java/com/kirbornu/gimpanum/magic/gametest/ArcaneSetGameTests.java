package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.cast.SpellCaster;
import com.kirbornu.gimpanum.magic.config.MagicConfig;
import com.kirbornu.gimpanum.magic.core.Rune;
import com.kirbornu.gimpanum.magic.spell.SpellSpawns;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.ARENA;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.CENTER;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.castFrom;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.player;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.still;

/**
 * Набор арканов автора и карта полей из {@code docs/arcane-sets/kirbornu}: читается
 * модом без ошибок, руны ведут туда же, куда считал генератор карты, и каждый
 * аркан корнем срабатывает в мире, ничего не ломая.
 *
 * <p>Набор подменяет настройки на время одного вызова и тут же возвращает
 * прежние: соседние тесты кастуют с образцом.
 */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class ArcaneSetGameTests {

    /** Игровые тесты идут в папке run/, набор лежит в репозитории рядом. */
    static final Path SET = Path.of("..", "docs", "arcane-sets", "kirbornu");
    static final List<String> FILES = List.of("arcanes.json", "fields.json", "magic.json");

    /** Загрузить набор, отдать его арканы, вернуть прежние настройки. */
    static MagicConfig.Loaded withSet(GameTestHelper h, java.util.function.Consumer<MagicConfig.Loaded> inside) {
        Map<String, String> backup = new HashMap<>();
        try {
            for (String name : FILES) {
                backup.put(name, Files.readString(MagicConfig.dir().resolve(name)));
                Files.writeString(MagicConfig.dir().resolve(name), Files.readString(SET.resolve(name)));
            }
            List<String> errors = MagicConfig.load(h.getLevel().getServer());
            errors.forEach(e -> log("set error: " + e));
            h.assertTrue(errors.isEmpty(), "набор не читается: " + errors.size() + " ошибок");
            MagicConfig.Loaded loaded = MagicConfig.get();
            inside.accept(loaded);
            return loaded;
        } catch (IOException failure) {
            throw new RuntimeException(failure);
        } finally {
            try {
                for (Map.Entry<String, String> entry : backup.entrySet()) {
                    Files.writeString(MagicConfig.dir().resolve(entry.getKey()), entry.getValue());
                }
            } catch (IOException failure) {
                throw new RuntimeException(failure);
            }
            h.assertTrue(MagicConfig.load(h.getLevel().getServer()).isEmpty(), "возврат образца");
        }
    }

    static List<int[]> words(String spell) {
        List<int[]> out = new ArrayList<>();
        for (String word : spell.split(" ")) {
            String[] parts = word.split("\\.");
            int[] runes = new int[parts.length];
            for (int i = 0; i < parts.length; i++) {
                runes[i] = Rune.byId(parts[i]).index();
            }
            out.add(runes);
        }
        return out;
    }

    @GameTest(template = "magictest")
    public static void setLoadsAndRunesLandWhereMapSays(GameTestHelper h) {
        // Ожидания — из генератора карты (python): одиночные руны и несколько длинных слов.
        Map<String, String> expected = Map.ofEntries(
                Map.entry("an", "fire"), Map.entry("be", "water"), Map.entry("vol", "heal"),
                Map.entry("gi", "shadow"), Map.entry("dar", "light"), Map.entry("er", "wind"),
                Map.entry("zhin", "earth"), Map.entry("zar", "gravity"), Map.entry("im", "lightning"),
                Map.entry("kol", "poison"), Map.entry("lun", "frost"), Map.entry("mar", "impulse"),
                Map.entry("be.er", "veil"), Map.entry("an.vol", "eye"), Map.entry("an.dar", "snare"),
                Map.entry("an.an.an", "hex"), Map.entry("an.an.zhin", "smite"), Map.entry("an.be.be", "fangs"),
                Map.entry("dar.dar.dar", "arcane_1"), Map.entry("mar.mar.mar", "waiting"),
                Map.entry("an.an.an.be", "storm"),
                // Дикие арканы.
                Map.entry("an.zar", "henhouse"), Map.entry("an.er", "bounty"),
                Map.entry("an.zhin.an", "illumination"), Map.entry("an.dar.dar", "burden"),
                Map.entry("gi.gi.gi.gi", "muster"), Map.entry("an.an.zhin.an", "ascension"),
                // Призывы — только пятью рунами.
                Map.entry("an.be.be.be.be", "deep"), Map.entry("zhin.gi.mar.mar.mar", "guardian"),
                Map.entry("an.an.an.an.zhin", "edge"), Map.entry("vol.vol.an.dar.er", "swarm"),
                Map.entry("an.im.dar.er.dar", "undead"));
        withSet(h, loaded -> {
            h.assertTrue(loaded.arcanes().size() == 55, "арканов " + loaded.arcanes().size());
            for (Map.Entry<String, String> e : expected.entrySet()) {
                String got = SpellCaster.compile(words(e.getKey()), ItemStack.EMPTY, loaded).compilation().arcanes()
                        .get(0);
                h.assertTrue(got.equals(e.getValue()), e.getKey() + " → " + got + ", а карта говорит " + e.getValue());
            }
        });
        h.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 400)
    public static void everyArcaneWorksAsRoot(GameTestHelper h) {
        List<Arcane> arcanes = new ArrayList<>();
        withSet(h, loaded -> arcanes.addAll(loaded.arcanes().values()));
        ServerPlayer p = player(h, true, CENTER);
        still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        // По одному аркану раз в 5 тиков: каждый — корнем, как одна руна.
        for (int i = 0; i < arcanes.size(); i++) {
            Arcane arcane = arcanes.get(i);
            h.runAfterDelay(1 + i * 5L, () -> {
                h.assertTrue(castFrom(p, arcane) != null, arcane.id() + " не породил сущность");
            });
        }
        h.runAfterDelay(1 + arcanes.size() * 5L + 20, () -> {
            log("set smoke: " + arcanes.size() + " arcanes cast as root");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void fangsBite(GameTestHelper h) {
        Arcane[] fangs = new Arcane[1];
        withSet(h, loaded -> fangs[0] = loaded.arcane("fangs"));
        ServerPlayer p = player(h, true, CENTER);
        Pig pig = still(h, EntityType.PIG, new BlockPos(10, 2, 7));
        float health = pig.getHealth();
        Arcane prefix = MagicTestKit.arcane("near", 10,
                "{\"prefix\": [{\"type\": \"target_nearest\", \"radius\": 10, \"excludeCaster\": true}]}");
        h.assertTrue(castFrom(p, prefix, fangs[0]) != null, "каст");
        h.runAfterDelay(30, () -> {
            log("fangs: pig " + health + " → " + pig.getHealth());
            h.assertTrue(pig.getHealth() < health || pig.isDeadOrDying(), "клыки не укусили");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void comboEyeSmiteHitsWhatYouLookAt(GameTestHelper h) {
        // «АН·ВОЛ АН·АН·ЖИН» — Око префиксом, Кара корнем: удар по тому, на кого смотришь.
        ServerPlayer p = player(h, true, CENTER);
        Pig front = still(h, EntityType.PIG, new BlockPos(11, 2, 7));
        Pig side = still(h, EntityType.PIG, new BlockPos(7, 2, 10));
        // Свинья ниже глаз: смотрим чуть вниз, на неё.
        p.setXRot(15F);
        withSet(h, loaded -> h.assertTrue(SpellCaster.cast(p, words("an.vol an.an.zhin"), ItemStack.EMPTY).isEmpty(),
                "каст по рунам"));
        h.runAfterDelay(3, () -> {
            log("combo eye+smite: front " + front.getHealth() + " side " + side.getHealth());
            h.assertTrue(front.getHealth() < 10F, "по той, на кого смотрели, не попало");
            h.assertTrue(side.getHealth() == 10F, "задело не ту");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void comboSnareFangsBitesNearestEnemy(GameTestHelper h) {
        // «АН·ДАР АН·БЭ·БЭ» — Путы префиксом (ближайший, кроме себя), Клыки корнем.
        ServerPlayer p = player(h, true, CENTER);
        Pig pig = still(h, EntityType.PIG, new BlockPos(10, 2, 7));
        withSet(h, loaded -> h.assertTrue(SpellCaster.cast(p, words("an.dar an.be.be"), ItemStack.EMPTY).isEmpty(),
                "каст по рунам"));
        h.runAfterDelay(30, () -> {
            log("combo snare+fangs: pig " + pig.getHealth());
            h.assertTrue(pig.getHealth() < 10F || pig.isDeadOrDying(), "клыки не укусили ближайшего");
            h.succeed();
        });
    }

    /** Призывы набора и кого каждый должен вызвать. */
    static final Map<String, List<EntityType<?>>> SUMMONS = Map.of(
            "herd", List.of(EntityType.COW, EntityType.PIG),
            "thicket", List.of(EntityType.FOX, EntityType.RABBIT),
            "deep", List.of(EntityType.DROWNED),
            "swarm", List.of(EntityType.BEE),
            "guardian", List.of(EntityType.IRON_GOLEM),
            "nest", List.of(EntityType.CAVE_SPIDER),
            "undead", List.of(EntityType.ZOMBIE, EntityType.SKELETON),
            "vexes", List.of(EntityType.VEX),
            "inferno", List.of(EntityType.BLAZE),
            "edge", List.of(EntityType.ENDERMAN, EntityType.ENDERMITE));

    static List<Entity> around(GameTestHelper h, EntityType<?> type) {
        // Только своя площадка: соседние тесты идут рядом в то же время.
        return new ArrayList<>(h.getLevel().getEntities(type, h.getBounds(), e -> true));
    }

    /** Призванные заклинанием рядом: эндермен в воде Пучины успевает телепортироваться за площадку. */
    static List<Entity> summonedNear(GameTestHelper h, EntityType<?> type) {
        return new ArrayList<>(h.getLevel().getEntities(type, h.getBounds().inflate(32),
                SpellSpawns::isSpellSpawned));
    }

    @GameTest(template = ARENA, timeoutTicks = 200)
    public static void everySummonCallsItsCreatures(GameTestHelper h) {
        Map<String, Arcane> arcanes = new HashMap<>();
        withSet(h, loaded -> SUMMONS.keySet().forEach(id -> arcanes.put(id, loaded.arcane(id))));
        ServerPlayer p = player(h, true, CENTER);
        List<String> ids = new ArrayList<>(SUMMONS.keySet());
        java.util.Collections.sort(ids);
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            h.runAfterDelay(1 + i * 6L, () -> {
                Map<EntityType<?>, List<Entity>> before = new HashMap<>();
                SUMMONS.get(id).forEach(t -> before.put(t, summonedNear(h, t)));
                h.assertTrue(castFrom(p, arcanes.get(id)) != null, id + " не породил сущность");
                h.runAfterDelay(3, () -> {
                    for (EntityType<?> type : SUMMONS.get(id)) {
                        List<Entity> fresh = summonedNear(h, type);
                        fresh.removeAll(before.get(type));
                        log("summon " + id + ": " + EntityType.getKey(type) + " +" + fresh.size());
                        h.assertTrue(!fresh.isEmpty(), id + " не призвал " + EntityType.getKey(type));
                        // Чтобы враждебные не мешали следующим тестам.
                        fresh.forEach(Entity::discard);
                    }
                });
            });
        }
        h.runAfterDelay(1 + ids.size() * 6L + 10, h::succeed);
    }

    @GameTest(template = ARENA)
    public static void summonedAnimalsBreedOrdinaryYoung(GameTestHelper h) {
        Arcane[] herd = new Arcane[1];
        withSet(h, loaded -> herd[0] = loaded.arcane("herd"));
        ServerPlayer p = player(h, true, CENTER);
        h.assertTrue(castFrom(p, herd[0]) != null, "первый каст");
        h.assertTrue(castFrom(p, herd[0]) != null, "второй каст");
        h.runAfterDelay(3, () -> {
            List<Entity> cows = around(h, EntityType.COW);
            h.assertTrue(cows.size() >= 2, "коров призвано " + cows.size());
            ((Cow) cows.get(0)).spawnChildFromBreeding(h.getLevel(), (Cow) cows.get(1));
            List<Entity> calves = around(h, EntityType.COW);
            calves.removeAll(cows);
            log("breed: summoned " + cows.size() + ", calves " + calves.size());
            h.assertTrue(calves.size() == 1, "призванные коровы не дали телёнка");
            // Телёнок — обычная корова: с добычей и опытом, без срока.
            h.assertFalse(SpellSpawns.isSpellSpawned(calves.get(0)), "телёнок помечен как призванный");
            h.succeed();
        });
    }

    // --- дикие арканы: тройной повтор открывает интерфикс -------------------
    // У каждого своя партия (batch): ауры до 32 блоков задели бы мобов соседних
    // тестов, а партии идут по очереди.

    /** Каст по рунам с набором автора; цена списана сразу, сущность пойдёт со следующего тика. */
    static void castSet(GameTestHelper h, ServerPlayer p, String spell) {
        withSet(h, loaded -> h.assertTrue(SpellCaster.cast(p, words(spell), ItemStack.EMPTY).isEmpty(),
                "каст " + spell));
    }

    @GameTest(template = ARENA, batch = "wild_henhouse", timeoutTicks = 200)
    public static void henhouseTripleSummonsChickens(GameTestHelper h) {
        // АН·ЗАР ×3: корень, интерфикс, корень. 5 импульсов раз в секунду, на каждом
        // живом в радиусе 6 — курица, и на курах тоже, пока не кончится предел заклинания.
        ServerPlayer p = player(h, true, CENTER);
        still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        // Где курица появилась: потом она уходит, поэтому расстояние — в первый тик, когда её видно.
        Map<java.util.UUID, Double> born = new HashMap<>();
        net.minecraft.world.phys.Vec3 origin = p.getEyePosition();
        h.onEachTick(() -> summonedNear(h, EntityType.CHICKEN)
                .forEach(c -> born.putIfAbsent(c.getUUID(), c.position().distanceTo(origin))));
        castSet(h, p, "an.zar an.zar an.zar");
        h.runAfterDelay(100, () -> {
            List<Entity> chickens = summonedNear(h, EntityType.CHICKEN);
            double farthest = born.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
            log("henhouse: summoned chickens " + chickens.size() + ", seen " + born.size()
                    + ", farthest at birth " + farthest);
            h.assertTrue(chickens.size() >= 2 && chickens.size() <= 15, "кур " + chickens.size());
            // Курица встаёт ногами туда, где ноги цели; цель — в 6 блоках от глаз кастера.
            h.assertTrue(farthest <= 6.6, "курица появилась в " + farthest + " блоках");
            chickens.forEach(Entity::discard);
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void henhouseRootThrowsEgg(GameTestHelper h) {
        // Корень Курятника — ванильное яйцо: летит по взгляду, при ударе иногда вылупляется цыплёнок.
        Arcane[] henhouse = new Arcane[1];
        withSet(h, loaded -> henhouse[0] = loaded.arcane("henhouse"));
        ServerPlayer p = player(h, true, CENTER);
        h.assertTrue(castFrom(p, henhouse[0]) != null, "каст");
        h.runAfterDelay(2, () -> {
            List<Entity> eggs = new ArrayList<>(h.getLevel().getEntities(EntityType.EGG, h.getBounds(),
                    SpellSpawns::isSpellSpawned));
            log("henhouse root: eggs " + eggs.size() + (eggs.isEmpty() ? "" : ", velocity "
                    + eggs.get(0).getDeltaMovement()));
            h.assertTrue(eggs.size() == 1, "яиц " + eggs.size());
            h.assertTrue(eggs.get(0).getDeltaMovement().x > 1, "яйцо не летит по взгляду (+X)");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "wild_bounty", timeoutTicks = 200)
    public static void bountyTripleFeedsOthersNotCaster(GameTestHelper h) {
        // АН·ЭР ×3: три импульса — лечение на 4 и еда 2/2 всем в радиусе 16, кроме кастера.
        ServerPlayer p = player(h, true, CENTER);
        p.setHealth(10F);
        ServerPlayer friend = player(h, true, new BlockPos(9, 2, 9));
        friend.getFoodData().setFoodLevel(10);
        friend.getFoodData().setSaturation(0F);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        pig.setHealth(3F);
        castSet(h, p, "an.er an.er an.er");
        // Цена уже списана: дальше еда и здоровье кастера меняться не должны.
        float health = p.getHealth();
        int food = p.getFoodData().getFoodLevel();
        h.runAfterDelay(80, () -> {
            log("bounty: pig " + pig.getHealth() + ", friend food " + friend.getFoodData().getFoodLevel()
                    + ", caster " + health + "→" + p.getHealth() + " food " + food + "→"
                    + p.getFoodData().getFoodLevel());
            h.assertTrue(pig.getHealth() == pig.getMaxHealth(), "свинья не вылечена: " + pig.getHealth());
            h.assertTrue(friend.getFoodData().getFoodLevel() == 16, "соседа накормили до "
                    + friend.getFoodData().getFoodLevel());
            h.assertTrue(p.getHealth() == health, "кастера лечило");
            h.assertTrue(p.getFoodData().getFoodLevel() == food, "кастера кормило");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "wild_illumination", timeoutTicks = 100)
    public static void illuminationTripleLightsEveryone(GameTestHelper h) {
        // АН·ЖИН·АН ×3: всё живое в радиусе 32 светится 60 с и теряет невидимость.
        // Кастер — поддельный игрок, он не падает, поэтому может стоять за площадкой.
        ServerPlayer p = player(h, true, new BlockPos(-3, 2, 1));
        Pig far = still(h, EntityType.PIG, new BlockPos(13, 2, 13));
        Pig hidden = still(h, EntityType.PIG, new BlockPos(13, 2, 1));
        hidden.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 1200));
        castSet(h, p, "an.zhin.an an.zhin.an an.zhin.an");
        h.runAfterDelay(20, () -> {
            log("illumination: far " + far.distanceTo(p) + " glows " + far.hasEffect(MobEffects.GLOWING)
                    + ", hidden invisible " + hidden.hasEffect(MobEffects.INVISIBILITY));
            h.assertTrue(far.distanceTo(p) >= 19.5, "далёкая свинья ближе 20 блоков");
            h.assertTrue(far.hasEffect(MobEffects.GLOWING), "далёкая свинья не светится");
            h.assertTrue(p.hasEffect(MobEffects.GLOWING), "кастер не светится");
            h.assertFalse(hidden.hasEffect(MobEffects.INVISIBILITY), "невидимость не снята");
            h.assertTrue(hidden.hasEffect(MobEffects.GLOWING), "невидимая не светится");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "wild_burden", timeoutTicks = 100)
    public static void burdenTripleTakesEffectsOnCaster(GameTestHelper h) {
        // АН·ДАР·ДАР ×3: эффекты всех в радиусе 16, кроме кастера, переходят на кастера.
        ServerPlayer p = player(h, true, CENTER);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        pig.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 1200, 1));
        castSet(h, p, "an.dar.dar an.dar.dar an.dar.dar");
        h.runAfterDelay(40, () -> {
            log("burden: pig speed " + pig.hasEffect(MobEffects.MOVEMENT_SPEED) + ", caster "
                    + p.getEffect(MobEffects.MOVEMENT_SPEED));
            h.assertFalse(pig.hasEffect(MobEffects.MOVEMENT_SPEED), "эффект остался на свинье");
            MobEffectInstance taken = p.getEffect(MobEffects.MOVEMENT_SPEED);
            h.assertTrue(taken != null && taken.getAmplifier() == 1, "эффект не перешёл на кастера");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "wild_muster", timeoutTicks = 100)
    public static void musterTripleGathersEveryone(GameTestHelper h) {
        // ГИ·ГИ·ГИ·ГИ ×3: всё живое в радиусе 16 мгновенно оказывается у кастера.
        ServerPlayer p = player(h, true, new BlockPos(1, 2, 7));
        Pig pig = still(h, EntityType.PIG, new BlockPos(13, 2, 7));
        double before = pig.distanceTo(p);
        castSet(h, p, "gi.gi.gi.gi gi.gi.gi.gi gi.gi.gi.gi");
        h.runAfterDelay(20, () -> {
            log("muster: pig " + before + " → " + pig.distanceTo(p));
            h.assertTrue(before >= 11.9, "свинья стояла ближе 12 блоков");
            h.assertTrue(pig.distanceTo(p) <= 2, "свинья в " + pig.distanceTo(p) + " блоках");
            h.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "wild_ascension", timeoutTicks = 200)
    public static void ascensionTripleThrowsEveryoneUp(GameTestHelper h) {
        // АН·АН·ЖИН·АН ×3: всё живое в радиусе 10 подброшено, launch 2.0. Высоко над
        // площадкой: у арены потолок, о него свинья ударилась бы через 8 блоков.
        // Кастер — поддельный игрок, он не падает и висит в воздухе.
        ServerPlayer p = player(h, true, new BlockPos(7, 40, 7));
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(9, 40, 7));
        double[] start = {Double.NaN};
        double[] top = {-1.0E9};
        double[] fastest = {0};
        h.onEachTick(() -> {
            fastest[0] = Math.max(fastest[0], pig.getDeltaMovement().y);
            if (Double.isNaN(start[0]) && pig.getDeltaMovement().y > 1.5) {
                start[0] = pig.getY();
            }
            top[0] = Math.max(top[0], pig.getY());
        });
        castSet(h, p, "an.an.zhin.an an.an.zhin.an an.an.zhin.an");
        h.runAfterDelay(80, () -> {
            double rise = top[0] - start[0];
            log("ascension: pig vy max " + fastest[0] + ", rise " + rise);
            h.assertTrue(fastest[0] >= 1.8 && fastest[0] <= 2.2, "вертикальная скорость " + fastest[0]);
            // По ванильной физике launch 2.0 — подъём примерно на 20 блоков.
            h.assertTrue(rise >= 18 && rise <= 22, "подъём " + rise);
            h.succeed();
        });
    }

    // --- примеры README с дикими арканами ------------------------------------

    @GameTest(template = ARENA)
    public static void comboBountyHealHealsPlayersAround(GameTestHelper h) {
        // «АН·ЭР ВОЛ» — Щедрость префиксом (игроки в радиусе 6, включая себя), Исцеление корнем.
        ServerPlayer p = player(h, true, CENTER);
        p.setHealth(10F);
        ServerPlayer near = player(h, true, new BlockPos(9, 2, 7));
        near.setHealth(10F);
        ServerPlayer far = player(h, true, new BlockPos(14, 2, 7));
        far.setHealth(10F);
        Pig pig = still(h, EntityType.PIG, new BlockPos(8, 2, 9));
        pig.setHealth(3F);
        castSet(h, p, "an.er vol");
        h.runAfterDelay(3, () -> {
            log("combo bounty+heal: caster " + p.getHealth() + " near " + near.getHealth() + " far "
                    + far.getHealth() + " pig " + pig.getHealth());
            h.assertTrue(p.getHealth() > 10F, "себя не вылечило");
            h.assertTrue(near.getHealth() > 10F, "соседа не вылечило");
            h.assertTrue(far.getHealth() == 10F, "вылечило дальнего");
            h.assertTrue(pig.getHealth() == 3F, "вылечило свинью");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void comboIlluminationSmiteHitsMonsters(GameTestHelper h) {
        // «АН·ЖИН·АН АН·АН·ЖИН» — Иллюминация префиксом (враждебные в радиусе 12), Кара корнем.
        ServerPlayer p = player(h, true, CENTER);
        Spider spider = still(h, EntityType.SPIDER, new BlockPos(11, 2, 7));
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 9));
        float spiderHealth = spider.getHealth();
        castSet(h, p, "an.zhin.an an.an.zhin");
        h.runAfterDelay(3, () -> {
            log("combo illumination+smite: spider " + spiderHealth + "→" + spider.getHealth() + " pig "
                    + pig.getHealth());
            h.assertTrue(spider.getHealth() < spiderHealth, "паука не задело");
            h.assertTrue(pig.getHealth() == 10F, "задело свинью");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void comboHenhouseHealHealsAnimals(GameTestHelper h) {
        // «АН·ЗАР ВОЛ» — Курятник префиксом (животные в радиусе 8), Исцеление корнем.
        ServerPlayer p = player(h, true, CENTER);
        p.setHealth(10F);
        Pig pig = still(h, EntityType.PIG, new BlockPos(9, 2, 7));
        pig.setHealth(3F);
        Spider spider = still(h, EntityType.SPIDER, new BlockPos(9, 2, 10));
        spider.setHealth(5F);
        castSet(h, p, "an.zar vol");
        h.runAfterDelay(3, () -> {
            log("combo henhouse+heal: pig " + pig.getHealth() + " spider " + spider.getHealth() + " caster "
                    + p.getHealth());
            h.assertTrue(pig.getHealth() > 3F, "свинью не вылечило");
            h.assertTrue(spider.getHealth() == 5F, "вылечило паука");
            h.assertTrue(p.getHealth() == 10F, "вылечило кастера");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void comboMusterAscensionLiftsOthers(GameTestHelper h) {
        // «ГИ·ГИ·ГИ·ГИ АН·АН·ЖИН·АН» — Сбор префиксом (все в радиусе 10, кроме себя), Вознесение корнем.
        ServerPlayer p = player(h, true, CENTER);
        Pig pig = still(h, EntityType.PIG, new BlockPos(10, 2, 9));
        castSet(h, p, "gi.gi.gi.gi an.an.zhin.an");
        h.runAfterDelay(3, () -> {
            log("combo muster+ascension: pig " + pig.hasEffect(MobEffects.LEVITATION) + " caster "
                    + p.hasEffect(MobEffects.LEVITATION));
            h.assertTrue(pig.hasEffect(MobEffects.LEVITATION), "свинья не левитирует");
            h.assertFalse(p.hasEffect(MobEffects.LEVITATION), "левитирует кастер");
            h.succeed();
        });
    }
}
