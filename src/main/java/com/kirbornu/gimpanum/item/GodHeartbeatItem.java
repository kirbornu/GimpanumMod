package com.kirbornu.gimpanum.item;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.List;

/**
 * Биение Сердца Бога — сердце моря, доведённое до предела: навсегда +1 сердце.
 *
 * <p>Избранные растят себя, поднося хрусталь к Утробе, а Утроба — в Сердце
 * Бога. Там же хранится фонос-тело Избранного, поэтому прибавка переживает
 * смерть: новое тело получает её от старого.
 *
 * <p>Счёт хранится в самом модификаторе здоровья, а не рядом: модификатор и
 * так сохраняется с игроком и доходит до его клиента, так что подсказка видит
 * тот же счёт, что и сервер. Снять прибавку можно ванильной командой
 * {@code /attribute <игрок> minecraft:generic.max_health modifier remove gimpanum:god_heartbeat}.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public class GodHeartbeatItem extends Item {

    public static final ResourceLocation MODIFIER =
            ResourceLocation.fromNamespaceAndPath(Gimpanum.MOD_ID, "god_heartbeat");

    /** Одно сердце — два очка здоровья. */
    private static final double HEALTH_PER_HEART = 2.0;

    public GodHeartbeatItem(Properties properties) {
        super(properties);
    }

    /** Сколько сердец игрок уже получил от Биений. */
    public static int hearts(Player player) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier modifier = health == null ? null : health.getModifier(MODIFIER);
        return modifier == null ? 0 : (int) Math.round(modifier.amount() / HEALTH_PER_HEART);
    }

    private static void setHearts(Player player, int hearts) {
        AttributeInstance health = player.getAttribute(Attributes.MAX_HEALTH);
        if (health == null) {
            return;
        }
        if (hearts <= 0) {
            health.removeModifier(MODIFIER);
        } else {
            health.addOrReplacePermanentModifier(new AttributeModifier(MODIFIER, hearts * HEALTH_PER_HEART,
                    AttributeModifier.Operation.ADD_VALUE));
        }
    }

    /**
     * Клиент выделенного сервера судит по образцу из джарки. Если на сервере
     * предел другой, клиент разок покажет лишнюю анимацию еды, но съест всё
     * равно сервер.
     */
    private static boolean full(Player player) {
        return hearts(player) >= ItemConfig.of("god_heartbeat").integer("max_hearts");
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (full(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("item.gimpanum.god_heartbeat.full"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (entity instanceof ServerPlayer player && level instanceof ServerLevel server && !full(player)) {
            setHearts(player, hearts(player) + 1);
            player.heal((float) HEALTH_PER_HEART);
            server.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2.0F, 0.6F);
            server.sendParticles(ParticleTypes.HEART, player.getX(), player.getY() + 1.2, player.getZ(),
                    8, 0.5, 0.4, 0.5, 0.0);
            stack.consume(1, player);
        }
        return stack;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 32;
    }

    /** Сколько сердец уже получено — дописывает {@link com.kirbornu.gimpanum.client.TopItemTooltips}. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.gimpanum.god_heartbeat.hint").withStyle(ChatFormatting.GRAY));
    }

    /**
     * Новое тело после смерти или возвращения из Энда получает прибавку от
     * старого. Ванилла переносит только базовые значения атрибутов, а
     * модификаторы теряет.
     */
    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        int hearts = hearts(event.getOriginal());
        if (hearts <= 0) {
            return;
        }
        Player player = event.getEntity();
        setHearts(player, hearts);
        // Здоровье ставилось ещё без прибавки и упёрлось в старый предел.
        player.setHealth(event.isWasDeath() ? player.getMaxHealth() : event.getOriginal().getHealth());
    }
}
