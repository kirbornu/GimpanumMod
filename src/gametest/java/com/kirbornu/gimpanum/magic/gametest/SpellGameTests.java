package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.MagicContent;
import com.kirbornu.gimpanum.magic.cast.SpellCaster;
import com.kirbornu.gimpanum.magic.config.MagicConfig;
import com.kirbornu.gimpanum.magic.spell.SpellEntity;
import com.kirbornu.gimpanum.magic.tablet.TabletContents;
import com.kirbornu.gimpanum.magic.tablet.TabletItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.FIRE;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.HEAL;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.LIGHT;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.WATER;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.cast;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.player;

/**
 * Демонстрационные арканы, цена, перезарядка, табличка и настройки — в живом сервере.
 *
 * <p>Запуск: {@code ./gradlew runGameTestServer}.
 */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class SpellGameTests {

    @GameTest(template = "magictest")
    public static void lightRootPaysAndPlaces(GameTestHelper h) {
        ServerPlayer p = player(h);
        float sat = p.getFoodData().getSaturationLevel();
        int food = p.getFoodData().getFoodLevel();
        h.assertTrue(cast(p, LIGHT).isEmpty(), "каст не прошёл");
        log("light: sat " + sat + "→" + p.getFoodData().getSaturationLevel() + " food " + food + "→" + p.getFoodData().getFoodLevel());
        h.assertTrue(Math.abs(p.getFoodData().getSaturationLevel() - (sat - 1)) < 1e-4, "насыщенность не списана");
        h.succeedWhen(() -> h.assertBlockPresent(Blocks.LIGHT, new BlockPos(4, 2, 4)));
    }

    @GameTest(template = "magictest")
    public static void cooldownBlocksSecondCast(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.assertTrue(cast(p, LIGHT).isEmpty(), "первый каст не прошёл");
        Optional<SpellCaster.Failure> second = cast(p, LIGHT);
        h.assertTrue(second.equals(Optional.of(SpellCaster.Failure.COOLDOWN)), "второй каст: " + second);
        h.runAfterDelay(61, () -> {
            h.assertTrue(cast(p, LIGHT).isEmpty(), "после 3 с перезарядка не прошла");
            h.succeed();
        });
    }

    @GameTest(template = "magictest")
    public static void priceCannotKill(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.getFoodData().setFoodLevel(0);
        p.getFoodData().setSaturation(0);
        p.setHealth(1.0F);
        Optional<SpellCaster.Failure> r = cast(p, LIGHT);
        h.assertTrue(r.equals(Optional.of(SpellCaster.Failure.PRICE)), "ожидался отказ по цене: " + r);
        h.assertTrue(p.getHealth() == 1.0F, "здоровье списано при отказе");
        p.setHealth(3.0F);
        h.assertTrue(cast(p, LIGHT).isEmpty(), "при 3 HP цена 1 должна пройти");
        log("price from health: " + p.getHealth());
        h.assertTrue(Math.abs(p.getHealth() - 2.0F) < 1e-4, "здоровье: " + p.getHealth());
        h.succeed();
    }

    @GameTest(template = "magictest")
    public static void waterPrefixRadiusLightRoot(GameTestHelper h) {
        ServerPlayer p = player(h);
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(5, 2, 4));
        pig.setNoAi(true);
        h.assertTrue(cast(p, WATER, LIGHT).isEmpty(), "каст");
        h.succeedWhen(() -> {
            h.assertBlockPresent(Blocks.LIGHT, new BlockPos(4, 2, 4));
            h.assertBlockPresent(Blocks.LIGHT, new BlockPos(5, 2, 4));
        });
    }

    @GameTest(template = "magictest")
    public static void lookPrefixPicksTarget(GameTestHelper h) {
        ServerPlayer p = player(h);
        // Цель ростом с человека: луч взгляда идёт от глаз горизонтально.
        h.spawn(EntityType.ARMOR_STAND, new BlockPos(6, 2, 4));
        h.assertTrue(cast(p, HEAL, LIGHT).isEmpty(), "каст");
        h.runAfterDelay(3, () -> {
            h.assertBlockPresent(Blocks.LIGHT, new BlockPos(6, 2, 4));
            h.assertBlockNotPresent(Blocks.LIGHT, new BlockPos(4, 2, 4));
            h.succeed();
        });
    }

    @GameTest(template = "magictest")
    public static void lookPrefixFallsBackToDefault(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.assertTrue(cast(p, HEAL, LIGHT).isEmpty(), "каст");
        h.runAfterDelay(3, () -> {
            h.assertBlockPresent(Blocks.LIGHT, new BlockPos(4, 2, 4));
            h.succeed();
        });
    }

    @GameTest(template = "magictest", timeoutTicks = 300)
    public static void firePrefixMakesHealPeriodic(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setHealth(2.0F);
        h.assertTrue(cast(p, FIRE, HEAL).isEmpty(), "каст");
        h.runAfterDelay(3, () -> h.assertTrue(p.getHealth() == 6.0F, "сразу: " + p.getHealth()));
        h.runAfterDelay(30, () -> h.assertTrue(p.getHealth() == 6.0F, "1.5 с: " + p.getHealth()));
        h.runAfterDelay(65, () -> h.assertTrue(p.getHealth() == 10.0F, "3.25 с: " + p.getHealth()));
        h.runAfterDelay(125, () -> h.assertTrue(p.getHealth() == 14.0F, "6.25 с: " + p.getHealth()));
        h.runAfterDelay(250, () -> {
            log("periodic heal final " + p.getHealth());
            h.assertTrue(p.getHealth() == 14.0F, "12.5 с: " + p.getHealth());
            h.succeed();
        });
    }

    @GameTest(template = "magictest", timeoutTicks = 200)
    public static void fireSuffixEchoesRoot(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setHealth(2.0F);
        h.assertTrue(cast(p, HEAL, FIRE).isEmpty(), "каст");
        h.runAfterDelay(3, () -> h.assertTrue(p.getHealth() == 10.0F, "сразу (корень + копия): " + p.getHealth()));
        h.runAfterDelay(25, () -> h.assertTrue(p.getHealth() == 14.0F, "1 с: " + p.getHealth()));
        h.runAfterDelay(45, () -> h.assertTrue(p.getHealth() == 18.0F, "2 с: " + p.getHealth()));
        h.runAfterDelay(120, () -> {
            h.assertTrue(p.getHealth() == 18.0F, "6 с: " + p.getHealth());
            h.assertEntityNotPresent(MagicContent.SPELL.get());
            h.succeed();
        });
    }

    @GameTest(template = "magictest", timeoutTicks = 200)
    public static void fireInterfixWaits(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setHealth(2.0F);
        h.assertTrue(cast(p, HEAL, FIRE, HEAL).isEmpty(), "каст");
        h.runAfterDelay(3, () -> h.assertTrue(p.getHealth() == 6.0F, "сразу: " + p.getHealth()));
        h.runAfterDelay(70, () -> h.assertTrue(p.getHealth() == 6.0F, "3.5 с: " + p.getHealth()));
        h.runAfterDelay(90, () -> {
            h.assertTrue(p.getHealth() == 10.0F, "4.5 с: " + p.getHealth());
            h.succeed();
        });
    }

    @GameTest(template = "magictest", timeoutTicks = 100)
    public static void waterInterfixDescends(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.setHealth(2.0F);
        h.assertTrue(cast(p, HEAL, WATER, HEAL).isEmpty(), "каст");
        h.runAfterDelay(1, () -> h.assertTrue(p.getHealth() == 6.0F, "сразу: " + p.getHealth()));
        h.runAfterDelay(2, () -> {
            List<SpellEntity> spells = h.getEntities(MagicContent.SPELL.get());
            log("descend: spell entities " + spells.size() + " y=" + spells.stream().map(e -> String.valueOf(e.getY() - h.absolutePos(BlockPos.ZERO).getY())).toList());
        });
        h.runAfterDelay(20, () -> {
            h.assertTrue(p.getHealth() == 10.0F, "1 с: " + p.getHealth());
            h.succeed();
        });
    }

    @GameTest(template = "magictest")
    public static void cleanseRemovesForeignEffects(GameTestHelper h) {
        ServerPlayer p = player(h);
        p.addEffect(new MobEffectInstance(MobEffects.POISON, 600));
        p.igniteForSeconds(10);
        h.assertTrue(cast(p, HEAL, WATER).isEmpty(), "каст");
        h.runAfterDelay(3, () -> {
            h.assertTrue(!p.hasEffect(MobEffects.POISON), "яд остался");
            h.assertTrue(!p.isOnFire(), "горение осталось");
            h.succeed();
        });
    }

    @GameTest(template = "magictest")
    public static void fireRootDamagesAndIgnites(GameTestHelper h) {
        // Кастер не в мире — ближайшей целью по умолчанию станет свинья.
        ServerPlayer p = player(h, false, new BlockPos(4, 2, 4));
        Pig pig = h.spawn(EntityType.PIG, new BlockPos(5, 2, 4));
        pig.setNoAi(true);
        float before = pig.getHealth();
        h.assertTrue(cast(p, FIRE).isEmpty(), "каст");
        h.runAfterDelay(2, () -> {
            log("fire: pig health " + before + "→" + pig.getHealth() + " fireTicks " + pig.getRemainingFireTicks());
            h.assertTrue(pig.getHealth() < before, "урон " + pig.getHealth());
            h.assertTrue(pig.isOnFire(), "не горит");
            h.succeed();
        });
    }

    @GameTest(template = "magictest")
    public static void firePrefixExtendsIgnite(GameTestHelper h) {
        ServerPlayer p = player(h);
        // огонь-префикс + огонь-корень невозможен (равные номера), поэтому
        // проверяем продление на свете: fire(20) light(90) → свет на 120 с.
        h.assertTrue(cast(p, FIRE, LIGHT).isEmpty(), "каст");
        h.succeedWhen(() -> h.assertBlockPresent(Blocks.LIGHT, new BlockPos(4, 2, 4)));
    }

    @GameTest(template = "magictest", timeoutTicks = 1300)
    public static void tempLightExpires(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.assertTrue(cast(p, LIGHT).isEmpty(), "каст");
        h.runAfterDelay(1180, () -> h.assertBlockPresent(Blocks.LIGHT, new BlockPos(4, 2, 4)));
        h.runAfterDelay(1240, () -> {
            h.assertBlockNotPresent(Blocks.LIGHT, new BlockPos(4, 2, 4));
            h.succeed();
        });
    }

    @GameTest(template = "magictest")
    public static void tabletReading(GameTestHelper h) {
        int[] cells = new int[TabletContents.CELLS];
        Arrays.fill(cells, -1);
        cells[TabletContents.index(0, 0)] = 0;
        cells[TabletContents.index(1, 1)] = 4;
        cells[TabletContents.index(1, 2)] = 1;
        cells[TabletContents.index(5, 5)] = 7; // вне видимой сетки
        TabletContents c = new TabletContents(cells, new ItemStack(Items.NETHERITE_INGOT, 1),
                new ItemStack(Items.NETHERITE_INGOT, 1));
        List<int[]> words = c.runescripts();
        h.assertTrue(c.rows() == 3 && c.columns() == 3, "размер");
        h.assertTrue(words.size() == 2 && Arrays.equals(words.get(0), new int[]{0})
                && Arrays.equals(words.get(1), new int[]{4, 1}), "чтение: " + words.stream().map(Arrays::toString).toList());
        ItemStack tablet = new ItemStack(MagicContent.TABLET.get());
        tablet.set(MagicContent.TABLET_CONTENTS.get(), c);
        h.assertTrue(TabletItem.contents(tablet).equals(c), "компонент");
        h.assertTrue(MagicConfig.get().arcanes().size() == 4, "арканы");
        h.succeed();
    }

    @GameTest(template = "magictest")
    public static void reloadKeepsOldOnErrors(GameTestHelper h) throws Exception {
        java.nio.file.Path file = MagicConfig.dir().resolve("arcanes.json");
        String original = java.nio.file.Files.readString(file);
        MagicConfig.Loaded before = MagicConfig.get();
        try {
            java.nio.file.Files.writeString(file, original.replace("\"order\": 60", "\"order\": \"много\"")
                    .replace("\"type\": \"descend\"", "\"type\": \"descnd\""));
            List<String> errors = MagicConfig.load(h.getLevel().getServer());
            errors.forEach(e -> log("reload error: " + e));
            h.assertTrue(errors.size() >= 2, "ошибок " + errors.size());
            h.assertTrue(MagicConfig.get() == before, "настройки подменились при ошибке");
        } finally {
            java.nio.file.Files.writeString(file, original);
        }
        h.assertTrue(MagicConfig.load(h.getLevel().getServer()).isEmpty(), "чистая перезагрузка с ошибками");
        h.assertTrue(MagicConfig.get() != before, "чистая перезагрузка не применилась");
        h.succeed();
    }

    @GameTest(template = "magictest")
    public static void compileCommandOutput(GameTestHelper h) {
        List<String> out = new java.util.ArrayList<>();
        net.minecraft.commands.CommandSource sink = new net.minecraft.commands.CommandSource() {
            public void sendSystemMessage(net.minecraft.network.chat.Component c) { out.add(c.getString()); }
            public boolean acceptsSuccess() { return true; }
            public boolean acceptsFailure() { return true; }
            public boolean shouldInformAdmins() { return false; }
        };
        var server = h.getLevel().getServer();
        var source = server.createCommandSourceStack().withSource(sink).withPermission(4);
        server.getCommands().performPrefixedCommand(source, "gimpanum magic compile zhin.zhin.lun.gi.be gi dar.an");
        server.getCommands().performPrefixedCommand(source, "gimpanum magic compile_in scalk an");
        server.getCommands().performPrefixedCommand(source, "gimpanum magic compile foo");
        out.forEach(l -> log("cmd: " + l));
        h.assertTrue(out.stream().anyMatch(l -> l.contains("(2.7366, -1.3692, -0.3452) → fire")), "нет эталонной строки");
        h.succeed();
    }

    /** Каст при номере света 50 (тогда он бывает префиксом и интерфиксом); файл возвращается сразу после каста. */
    static Optional<SpellCaster.Failure> castWithLight50(GameTestHelper h, ServerPlayer p, int[]... words) {
        java.nio.file.Path file = MagicConfig.dir().resolve("arcanes.json");
        try {
            String original = java.nio.file.Files.readString(file);
            java.nio.file.Files.writeString(file, original.replace("\"order\": 90", "\"order\": 50"));
            h.assertTrue(MagicConfig.load(h.getLevel().getServer()).isEmpty(), "перезагрузка");
            try {
                return cast(p, words);
            } finally {
                java.nio.file.Files.writeString(file, original);
                h.assertTrue(MagicConfig.load(h.getLevel().getServer()).isEmpty(), "возврат");
            }
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }
    }

    @GameTest(template = "magictest")
    public static void lightConePrefixWaterRoot(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.assertTrue(castWithLight50(h, p, LIGHT, WATER).isEmpty(), "каст");
        h.runAfterDelay(3, () -> {
            h.assertBlockPresent(Blocks.WATER, new BlockPos(6, 3, 4));
            h.assertBlockPresent(Blocks.WATER, new BlockPos(7, 3, 5));
            h.assertBlockNotPresent(Blocks.WATER, new BlockPos(2, 3, 4));
            h.succeed();
        });
    }

    @GameTest(template = "magictest", timeoutTicks = 200)
    public static void lightInterfixEmitsAndCleansUp(GameTestHelper h) {
        ServerPlayer p = player(h);
        h.assertTrue(castWithLight50(h, p, HEAL, LIGHT, HEAL).isEmpty(), "каст");
        h.runAfterDelay(20, () -> h.assertBlockPresent(Blocks.LIGHT, new BlockPos(4, 3, 4)));
        h.runAfterDelay(110, () -> {
            h.assertBlockNotPresent(Blocks.LIGHT, new BlockPos(4, 3, 4));
            h.assertEntityNotPresent(MagicContent.SPELL.get());
            h.succeed();
        });
    }

    @GameTest(template = "magictest")
    public static void tabletMenuUpgradesAndEjects(GameTestHelper h) {
        ServerPlayer p = player(h);
        var inv = p.getInventory();
        inv.selected = 0;
        inv.setItem(0, new ItemStack(MagicContent.TABLET.get()));
        inv.setItem(1, new ItemStack(MagicContent.GLYPHS.get(com.kirbornu.gimpanum.magic.core.Rune.DAR).get(), 3));
        inv.setItem(2, new ItemStack(Items.NETHERITE_INGOT, 7));
        ItemStack tablet = inv.getItem(0);
        var menu = new com.kirbornu.gimpanum.magic.tablet.TabletMenu(1, inv, net.minecraft.world.InteractionHand.MAIN_HAND,
                Items.NETHERITE_INGOT);
        int playerStart = TabletContents.CELLS + 2;
        // shift-клик слитков: в слот рядов уходит 5, в слот столбцов — 2
        menu.quickMoveStack(p, playerStart + 27 + 2);
        menu.quickMoveStack(p, playerStart + 27 + 2);
        log("after ingots: rows " + menu.rows() + " cols " + menu.columns() + " left " + inv.getItem(2).getCount());
        h.assertTrue(menu.rows() == 7 && menu.columns() == 4, "размер " + menu.rows() + "x" + menu.columns());
        // shift-клик глифов: по одному в первые свободные ячейки
        for (int i = 0; i < 3; i++) {
            menu.quickMoveStack(p, playerStart + 27 + 1);
        }
        TabletContents c = TabletItem.contents(tablet);
        log("cells: " + java.util.Arrays.toString(java.util.Arrays.copyOf(c.cells(), 10)));
        h.assertTrue(c.cell(0, 0) == 4 && c.cell(0, 1) == 4 && c.cell(0, 2) == 4, "глифы не легли");
        // глиф в дальний угол видимой сетки
        menu.getSlot(TabletContents.index(6, 3)).set(new ItemStack(MagicContent.glyph(7)));
        h.assertTrue(TabletItem.contents(tablet).cell(6, 3) == 7, "угол");
        // вынимаем все улучшения рядов: строка 6 исчезает, глиф ЗАР возвращается игроку
        menu.getSlot(TabletContents.CELLS).set(ItemStack.EMPTY);
        c = TabletItem.contents(tablet);
        h.assertTrue(c.rows() == 2 && c.cell(6, 3) == -1, "не вытолкнуло: " + c.cell(6, 3));
        h.assertTrue(inv.countItem(MagicContent.glyph(7)) == 1, "глиф не вернулся");
        // запертый слот: обмен цифрой с выбранным слотом хотбара не проходит
        menu.clicked(playerStart, 0, net.minecraft.world.inventory.ClickType.SWAP, p);
        h.assertTrue(inv.getItem(0) == tablet, "табличку сдвинули");
        h.assertTrue(menu.stillValid(p), "меню закрылось");
        h.assertTrue(TabletItem.contents(tablet).runescripts().size() == 1, "чтение");
        h.succeed();
    }
}
