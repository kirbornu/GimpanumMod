package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.item.SealContents;
import com.kirbornu.gimpanum.item.SealItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Выброс Печатей и прочих предметов от Ядра.
 *
 * <p>Всё роняется в мировых координатах: у Ядра на физической конструкции
 * {@code BlockPos} указывает в служебный регион карты, и предмет, созданный
 * там, уехал бы вместе с конструкцией вместо того, чтобы остаться на месте.
 */
public final class SealDrops {

    private SealDrops() {
    }

    public static void spawnSeal(ServerLevel level, Vec3 worldPos, SealContents contents) {
        spawn(level, worldPos, SealItem.create(contents));
    }

    /**
     * Выдаёт несколько штук предмета, разбивая их по стопкам.
     *
     * <p>Одна сущность на стопку, как это делает ваниль. Слить всё в одну
     * сущность нельзя: количество больше стопки не переживает сохранение мира —
     * {@code ItemStack.CODEC} принимает только {@code 1..99}.
     */
    public static void spawnBatch(ServerLevel level, Vec3 worldPos, ItemStack prototype, int count) {
        int remaining = Math.max(1, count);
        int perStack = Math.max(1, prototype.getMaxStackSize());
        while (remaining > 0) {
            int size = Math.min(remaining, perStack);
            spawn(level, worldPos, prototype.copyWithCount(size));
            remaining -= size;
        }
    }

    public static void spawn(ServerLevel level, Vec3 worldPos, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, worldPos.x, worldPos.y, worldPos.z, stack);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoPickUpDelay();
        protect(entity, stack, level);
        level.addFreshEntity(entity);
    }

    /**
     * Печать — трофей, и пропасть она не должна.
     *
     * <p>{@code setInvulnerable} закрывает урон: взрыв, огонь, лаву, кактус.
     * Полностью неуязвимой сущность в Minecraft сделать нельзя: источники с
     * тегом {@code BYPASSES_INVULNERABILITY} — падение в пустоту, {@code /kill}
     * и удар игрока в креативе — проходят всегда, по устройству игры.
     *
     * <p>Срок жизни берётся у самого предмета, а не назначается здесь: у Печати
     * он свой ({@code SealItem}), у прочих выдаваемых предметов остаётся
     * ванильным. Так выброшенная Печать живёт одинаково долго независимо от
     * того, откуда она взялась.
     */
    private static void protect(ItemEntity entity, ItemStack stack, ServerLevel level) {
        entity.setInvulnerable(true);
        entity.lifespan = stack.getItem().getEntityLifespan(stack, level);
    }
}
