package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.MagicContent;
import com.kirbornu.gimpanum.magic.tablet.TabletContents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Arrays;

import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.player;
import static com.kirbornu.gimpanum.magic.gametest.PaymentGameTests.shards;

/**
 * Перезарядка не мешает открыть табличку, но и не даёт колдовать раньше срока.
 *
 * <p>Табличка используется так же, как рукой игрока: через
 * {@code ServerPlayerGameMode.useItem}, — именно там ванильная перезарядка
 * предмета глушила бы использование.
 */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class CooldownGameTests {

    /** Табличка с одной руной АН (свет) в руке. */
    static ItemStack tabletIn(ServerPlayer p) {
        int[] cells = new int[TabletContents.CELLS];
        Arrays.fill(cells, -1);
        cells[0] = 0;
        ItemStack tablet = new ItemStack(MagicContent.TABLET.get());
        tablet.set(MagicContent.TABLET_CONTENTS.get(), new TabletContents(cells, ItemStack.EMPTY, ItemStack.EMPTY));
        p.setItemInHand(InteractionHand.MAIN_HAND, tablet);
        return tablet;
    }

    static InteractionResult use(GameTestHelper h, ServerPlayer p, boolean sneak) {
        p.setShiftKeyDown(sneak);
        InteractionResult result = p.gameMode.useItem(p, h.getLevel(), p.getMainHandItem(), InteractionHand.MAIN_HAND);
        p.setShiftKeyDown(false);
        return result;
    }

    static int spells(GameTestHelper h) {
        return h.getEntities(MagicContent.SPELL.get()).size();
    }

    @GameTest(template = "magictest", timeoutTicks = 120)
    public static void tabletUsableDuringCooldownButCannotCast(GameTestHelper h) {
        ServerPlayer p = player(h);
        tabletIn(p);
        int before = shards(p);
        h.assertTrue(use(h, p, false).consumesAction(), "первый каст табличкой");
        h.assertTrue(!p.getCooldowns().isOnCooldown(MagicContent.TABLET.get()), "ванильная перезарядка поставлена");
        h.assertTrue(shards(p) == before - 1, "первый каст не оплачен");

        // Во время перезарядки: открыть можно (игра доходит до предмета), колдовать — нет.
        InteractionResult open = use(h, p, true);
        log("cooldown: open during cooldown → " + open);
        h.assertTrue(open.consumesAction(), "табличка не открывается во время перезарядки: " + open);
        h.runAfterDelay(2, () -> {
            int spellsNow = spells(h);
            use(h, p, false);
            h.assertTrue(shards(p) == before - 1, "второй каст во время перезарядки оплачен");
            h.runAfterDelay(1, () -> h.assertTrue(spells(h) <= spellsNow, "второй каст сотворён"));
        });
        h.runAfterDelay(70, () -> {
            h.assertTrue(use(h, p, false).consumesAction(), "после перезарядки");
            h.assertTrue(shards(p) == before - 2, "каст после перезарядки не оплачен");
            h.succeed();
        });
    }

    @GameTest(template = "magictest")
    public static void otherItemsUnaffected(GameTestHelper h) {
        // Перезарядка магии — не ванильная, на остальные предметы не влияет.
        ServerPlayer p = player(h);
        tabletIn(p);
        use(h, p, false);
        h.assertTrue(!p.getCooldowns().isOnCooldown(Items.ENDER_PEARL), "что-то поставило перезарядку жемчугу");
        h.succeed();
    }
}
