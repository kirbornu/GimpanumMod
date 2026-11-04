package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.cast.SpellCaster;
import com.kirbornu.gimpanum.magic.config.MagicConfig;
import com.kirbornu.gimpanum.magic.core.Rune;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
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
                Map.entry("be.er", "veil"), Map.entry("mar.mar", "explosion"), Map.entry("an.an.an", "smite"),
                Map.entry("dar.dar.dar", "arcane_1"), Map.entry("lun.lun.lun", "void"),
                Map.entry("an.an.an.an", "storm"), Map.entry("an.an.im.dar", "echo"));
        withSet(h, loaded -> {
            h.assertTrue(loaded.arcanes().size() == 39, "арканов " + loaded.arcanes().size());
            for (Map.Entry<String, String> e : expected.entrySet()) {
                String got = SpellCaster.compile(words(e.getKey()), ItemStack.EMPTY, loaded).compilation().arcanes()
                        .get(0);
                h.assertTrue(got.equals(e.getValue()), e.getKey() + " → " + got + ", а карта говорит " + e.getValue());
            }
        });
        h.succeed();
    }

    @GameTest(template = ARENA, timeoutTicks = 300)
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
        // «АН·ЖИН АН·АН·АН» — Око префиксом, Кара корнем: удар по тому, на кого смотришь.
        ServerPlayer p = player(h, true, CENTER);
        Pig front = still(h, EntityType.PIG, new BlockPos(11, 2, 7));
        Pig side = still(h, EntityType.PIG, new BlockPos(7, 2, 10));
        // Свинья ниже глаз: смотрим чуть вниз, на неё.
        p.setXRot(15F);
        withSet(h, loaded -> h.assertTrue(SpellCaster.cast(p, words("an.zhin an.an.an"), ItemStack.EMPTY).isEmpty(),
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
        // «АН·БЭ БЭ·БЭ·БЭ» — Путы префиксом (ближайший, кроме себя), Клыки корнем.
        ServerPlayer p = player(h, true, CENTER);
        Pig pig = still(h, EntityType.PIG, new BlockPos(10, 2, 7));
        withSet(h, loaded -> h.assertTrue(SpellCaster.cast(p, words("an.be be.be.be"), ItemStack.EMPTY).isEmpty(),
                "каст по рунам"));
        h.runAfterDelay(30, () -> {
            log("combo snare+fangs: pig " + pig.getHealth());
            h.assertTrue(pig.getHealth() < 10F || pig.isDeadOrDying(), "клыки не укусили ближайшего");
            h.succeed();
        });
    }
}
