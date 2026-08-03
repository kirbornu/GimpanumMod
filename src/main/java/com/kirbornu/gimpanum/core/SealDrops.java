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

    public static void spawn(ServerLevel level, Vec3 worldPos, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, worldPos.x, worldPos.y, worldPos.z, stack);
        entity.setDeltaMovement(Vec3.ZERO);
        entity.setNoPickUpDelay();
        protect(entity);
        level.addFreshEntity(entity);
    }

    /**
     * Печать — трофей, и пропасть она не должна.
     *
     * <p>{@code setInvulnerable} закрывает урон: взрыв, огонь, лаву, кактус.
     * {@code setUnlimitedLifetime} убирает пятиминутный таймер исчезновения.
     *
     * <p>Полностью неуязвимой сущность в Minecraft сделать нельзя: источники с
     * тегом {@code BYPASSES_INVULNERABILITY} — падение в пустоту, {@code /kill}
     * и удар игрока в креативе — проходят всегда, по устройству игры.
     */
    private static void protect(ItemEntity entity) {
        entity.setInvulnerable(true);
        entity.setUnlimitedLifetime();
    }
}
