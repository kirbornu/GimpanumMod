package com.kirbornu.gimpanum.item;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.stats.Stats;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;

import java.util.List;

/**
 * Панцирь Склава — панцирь шулкера, доведённый до предела: короб на один вид
 * вещей, зато на тысячи штук.
 *
 * <p>Склавы копили хрусталь в панцирях и таскали на себе всё, что им
 * поручали. Их панцирь делает то же: первая положенная вещь задаёт вид, и
 * дальше он принимает только её — до предела из настройки.
 *
 * <p>Как пользоваться:
 * <ul>
 *     <li>подобранные вещи его вида сами уходят в него, если в инвентаре есть
 *     такой панцирь;</li>
 *     <li>в инвентаре — как мешок: ПКМ панцирем по вещи или вещью по панцирю
 *     кладёт её внутрь, ПКМ пустой рукой по панцирю вынимает стак;</li>
 *     <li>ПКМ в руке выдаёт стак, Shift+ПКМ — столько, сколько влезет в
 *     инвентарь.</li>
 * </ul>
 *
 * <p>Берёт только то, что складывается в стопки: инструментам, шулкерам и
 * другим панцирям в нём не место. Сам панцирь, брошенный на землю, не горит и
 * не взрывается — терять тысячи вещей разом слишком обидно.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public class SklavCarapaceItem extends Item {

    private static final int BAR_COLOR = Mth.color(0.85F, 0.6F, 0.2F);

    public SklavCarapaceItem(Properties properties) {
        super(properties);
    }

    public static int capacity() {
        return ItemConfig.of("sklav_carapace").integer("capacity");
    }

    private static CarapaceContents contents(ItemStack carapace) {
        return carapace.get(GimpanumContent.CARAPACE_CONTENTS.get());
    }

    /** Возьмёт ли панцирь эту вещь — по виду, а не по месту. */
    private static boolean accepts(ItemStack carapace, ItemStack stack) {
        if (stack.isEmpty() || stack.getMaxStackSize() <= 1 || !stack.canFitInsideContainerItems()) {
            return false;
        }
        CarapaceContents contents = contents(carapace);
        return contents == null || contents.holds(stack);
    }

    /**
     * Кладёт сколько влезет, уменьшая {@code stack}.
     *
     * @return сколько положено
     */
    public static int insert(ItemStack carapace, ItemStack stack) {
        if (!accepts(carapace, stack)) {
            return 0;
        }
        CarapaceContents contents = contents(carapace);
        int held = contents == null ? 0 : contents.count();
        int moved = Math.min(stack.getCount(), capacity() - held);
        if (moved <= 0) {
            return 0;
        }
        carapace.set(GimpanumContent.CARAPACE_CONTENTS.get(), new CarapaceContents(stack, held + moved));
        stack.shrink(moved);
        return moved;
    }

    /** Вынимает не больше одного стака и не больше {@code max}. Пусто, если вынимать нечего. */
    private static ItemStack extract(ItemStack carapace, int max) {
        CarapaceContents contents = contents(carapace);
        if (contents == null || max <= 0) {
            return ItemStack.EMPTY;
        }
        int taken = Math.min(Math.min(max, contents.kind().getMaxStackSize()), contents.count());
        int left = contents.count() - taken;
        if (left > 0) {
            carapace.set(GimpanumContent.CARAPACE_CONTENTS.get(), new CarapaceContents(contents.kind(), left));
        } else {
            carapace.remove(GimpanumContent.CARAPACE_CONTENTS.get());
        }
        return contents.kind().copyWithCount(taken);
    }

    /** Панцирь в курсоре, ПКМ по ячейке: пустая — выложить стак, с вещью — забрать её. */
    @Override
    public boolean overrideStackedOnOther(ItemStack carapace, Slot slot, ClickAction action, Player player) {
        if (carapace.getCount() != 1 || action != ClickAction.SECONDARY) {
            return false;
        }
        ItemStack inSlot = slot.getItem();
        if (inSlot.isEmpty()) {
            ItemStack out = extract(carapace, Integer.MAX_VALUE);
            if (!out.isEmpty()) {
                ItemStack rest = slot.safeInsert(out);
                if (!rest.isEmpty()) {
                    insert(carapace, rest);
                }
                playRemove(player);
            }
        } else if (accepts(carapace, inSlot)) {
            CarapaceContents contents = contents(carapace);
            int room = capacity() - (contents == null ? 0 : contents.count());
            if (room > 0) {
                ItemStack taken = slot.safeTake(inSlot.getCount(), room, player);
                if (insert(carapace, taken) > 0) {
                    playInsert(player);
                }
            }
        }
        return true;
    }

    /** Вещь в курсоре, ПКМ по панцирю: положить её. Пустой курсор — вынуть стак. */
    @Override
    public boolean overrideOtherStackedOnMe(ItemStack carapace, ItemStack other, Slot slot, ClickAction action,
                                            Player player, SlotAccess cursor) {
        if (carapace.getCount() != 1 || action != ClickAction.SECONDARY || !slot.allowModification(player)) {
            return false;
        }
        if (other.isEmpty()) {
            ItemStack out = extract(carapace, Integer.MAX_VALUE);
            if (!out.isEmpty()) {
                cursor.set(out);
                playRemove(player);
            }
        } else if (insert(carapace, other) > 0) {
            playInsert(player);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack carapace = player.getItemInHand(hand);
        if (contents(carapace) == null) {
            return InteractionResultHolder.pass(carapace);
        }
        if (!level.isClientSide) {
            Inventory inventory = player.getInventory();
            if (player.isShiftKeyDown()) {
                // Сколько влезет: стак за стаком, пока есть место. Что не
                // влезло — обратно в панцирь.
                while (contents(carapace) != null) {
                    ItemStack out = extract(carapace, Integer.MAX_VALUE);
                    place(inventory, out);
                    if (!out.isEmpty()) {
                        insert(carapace, out);
                        break;
                    }
                }
            } else {
                ItemStack out = extract(carapace, Integer.MAX_VALUE);
                place(inventory, out);
                // Что не влезло — под ноги, а не в пустоту.
                if (!out.isEmpty()) {
                    player.drop(out, false);
                }
            }
            playRemove(player);
            player.awardStat(Stats.ITEM_USED.get(this));
        }
        return InteractionResultHolder.sidedSuccess(carapace, level.isClientSide);
    }

    /**
     * Раскладывает вещь по инвентарю, уменьшая {@code stack} на положенное.
     *
     * <p>Своя раскладка, а не {@code Inventory.add}: тот в творческом режиме
     * «берёт» и то, что не влезло, — просто стирает остаток. Здесь остаток
     * всегда остаётся в {@code stack}, и вызывающий решает, куда его деть.
     */
    private static void place(Inventory inventory, ItemStack stack) {
        while (!stack.isEmpty()) {
            int slot = inventory.getSlotWithRemainingSpace(stack);
            if (slot >= 0) {
                ItemStack there = inventory.getItem(slot);
                int limit = Math.min(inventory.getMaxStackSize(), there.getMaxStackSize());
                int moved = Math.min(stack.getCount(), limit - there.getCount());
                if (moved <= 0) {
                    break;
                }
                there.grow(moved);
                stack.shrink(moved);
                continue;
            }
            slot = inventory.getFreeSlot();
            if (slot < 0) {
                break;
            }
            inventory.setItem(slot, stack.split(Math.min(stack.getCount(), stack.getMaxStackSize())));
        }
        inventory.setChanged();
    }

    @Override
    public boolean isBarVisible(ItemStack carapace) {
        return contents(carapace) != null;
    }

    @Override
    public int getBarWidth(ItemStack carapace) {
        CarapaceContents contents = contents(carapace);
        int held = contents == null ? 0 : contents.count();
        return Math.min(13, 1 + Mth.floor(12.0F * held / Math.max(1, capacity())));
    }

    @Override
    public int getBarColor(ItemStack carapace) {
        return BAR_COLOR;
    }

    @Override
    public void appendHoverText(ItemStack carapace, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        CarapaceContents contents = contents(carapace);
        if (contents == null) {
            tooltip.add(Component.translatable("item.gimpanum.sklav_carapace.empty").withStyle(ChatFormatting.GRAY));
        } else {
            tooltip.add(Component.translatable("item.gimpanum.sklav_carapace.holds",
                    contents.kind().getHoverName(), contents.count(), capacity()).withStyle(ChatFormatting.GOLD));
        }
        tooltip.add(Component.translatable("item.gimpanum.sklav_carapace.hint").withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean canBeHurtBy(ItemStack carapace, DamageSource source) {
        return false;
    }

    private static void playInsert(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_INSERT, 0.8F, 0.6F + entity.level().getRandom().nextFloat() * 0.3F);
    }

    private static void playRemove(Entity entity) {
        entity.playSound(SoundEvents.BUNDLE_REMOVE_ONE, 0.8F, 0.6F + entity.level().getRandom().nextFloat() * 0.3F);
    }

    /**
     * Подобранное уходит в панцирь своего вида, если такой есть в инвентаре.
     *
     * <p>Условия подбора проверяем сами — задержку и хозяина брошенной вещи:
     * событие приходит до ванильной проверки. Пустой панцирь вещи не ловит:
     * иначе вид ему задавал бы первый попавшийся под ноги мусор.
     */
    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Pre event) {
        ItemEntity entity = event.getItemEntity();
        Player player = event.getPlayer();
        if (event.canPickup().isFalse() || entity.hasPickUpDelay()
                || entity.getTarget() != null && !entity.getTarget().equals(player.getUUID())) {
            return;
        }
        ItemStack loot = entity.getItem();
        Item item = loot.getItem();
        Inventory inventory = player.getInventory();
        int absorbed = 0;
        for (int i = 0; i < inventory.getContainerSize() && !loot.isEmpty(); i++) {
            ItemStack carapace = inventory.getItem(i);
            if (carapace.getItem() instanceof SklavCarapaceItem && contents(carapace) != null) {
                absorbed += insert(carapace, loot);
            }
        }
        if (absorbed == 0) {
            return;
        }
        player.take(entity, absorbed);
        player.awardStat(Stats.ITEM_PICKED_UP.get(item), absorbed);
        if (loot.isEmpty()) {
            entity.discard();
            event.setCanPickup(TriState.FALSE);
        } else {
            // Стопку уменьшили на месте; копия — чтобы игра заметила перемену
            // и показала клиентам новое количество, а не прежнее.
            entity.setItem(loot.copy());
        }
    }
}
