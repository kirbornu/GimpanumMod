package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.cast.SpellCaster;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.Optional;

import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.FIRE;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.HEAL;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.LIGHT;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.cast;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.player;

/** Цена заклинания: хрусталь по осколку за начатые четыре руны — вместе с едой и атомарно. */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class PaymentGameTests {

    static int shards(ServerPlayer p) {
        return p.getInventory().countItem(GimpanumContent.CRYSTAL_SHARD.get());
    }

    static void giveShards(ServerPlayer p, int count) {
        p.getInventory().setItem(20, count == 0 ? ItemStack.EMPTY
                : new ItemStack(GimpanumContent.CRYSTAL_SHARD.get(), count));
    }

    @GameTest(template = "magictest")
    public static void crystalPerStartedFourRunes(GameTestHelper h) {
        ServerPlayer one = player(h);
        giveShards(one, 10);
        h.assertTrue(cast(one, LIGHT).isEmpty(), "1 руна");
        h.assertTrue(shards(one) == 9, "1 руна стоила " + (10 - shards(one)));

        ServerPlayer six = player(h);
        giveShards(six, 10);
        h.assertTrue(cast(six, FIRE, HEAL).isEmpty(), "6 рун");
        log("crystals: 1 rune → " + (10 - shards(one)) + ", 6 runes → " + (10 - shards(six)));
        h.assertTrue(shards(six) == 8, "6 рун стоили " + (10 - shards(six)));
        h.succeed();
    }

    @GameTest(template = "magictest")
    public static void noCrystalNoCastNothingTaken(GameTestHelper h) {
        ServerPlayer p = player(h);
        giveShards(p, 1);
        float saturation = p.getFoodData().getSaturationLevel();
        Optional<SpellCaster.Failure> result = cast(p, FIRE);
        h.assertTrue(result.equals(Optional.of(SpellCaster.Failure.CRYSTAL)), "ожидался отказ: " + result);
        h.assertTrue(shards(p) == 1, "хрусталь списан при отказе");
        h.assertTrue(p.getFoodData().getSaturationLevel() == saturation, "еда списана при отказе");
        // Отказ — не каст: перезарядки нет, и с хрусталём сразу можно снова.
        giveShards(p, 2);
        h.assertTrue(cast(p, FIRE).isEmpty(), "после отказа не кастуется");
        h.assertTrue(shards(p) == 0, "осталось " + shards(p));
        h.succeed();
    }

    @GameTest(template = "magictest")
    public static void noFoodKeepsCrystal(GameTestHelper h) {
        ServerPlayer p = player(h);
        giveShards(p, 5);
        p.getFoodData().setFoodLevel(0);
        p.getFoodData().setSaturation(0);
        p.setHealth(1);
        Optional<SpellCaster.Failure> result = cast(p, LIGHT);
        h.assertTrue(result.equals(Optional.of(SpellCaster.Failure.PRICE)), "ожидался отказ по еде: " + result);
        h.assertTrue(shards(p) == 5, "хрусталь списан, хотя не хватило еды");
        h.succeed();
    }

    @GameTest(template = "magictest")
    public static void offhandCatalystSpentLast(GameTestHelper h) {
        ServerPlayer p = player(h);
        giveShards(p, 2);
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(GimpanumContent.CRYSTAL_SHARD.get(), 1));
        h.assertTrue(cast(p, FIRE).isEmpty(), "каст");
        h.assertTrue(p.getOffhandItem().getCount() == 1, "съели катализатор из второй руки");
        h.assertTrue(p.getInventory().getItem(20).isEmpty(), "основной хрусталь не потрачен");

        ServerPlayer q = player(h);
        giveShards(q, 1);
        q.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(GimpanumContent.CRYSTAL_SHARD.get(), 1));
        h.assertTrue(cast(q, FIRE).isEmpty(), "каст с добором из второй руки");
        h.assertTrue(q.getOffhandItem().isEmpty() && q.getInventory().getItem(20).isEmpty(), "не добрал");
        h.succeed();
    }

    @GameTest(template = "magictest")
    public static void creativeIsFree(GameTestHelper h) {
        ServerPlayer p = player(h);
        giveShards(p, 0);
        p.getAbilities().instabuild = true;
        h.assertTrue(cast(p, FIRE).isEmpty(), "в творческом без хрусталя не кастуется");
        h.succeed();
    }
}
