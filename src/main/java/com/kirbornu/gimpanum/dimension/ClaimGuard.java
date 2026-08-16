package com.kirbornu.gimpanum.dimension;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.core.CoreFakePlayer;
import com.kirbornu.gimpanum.integration.ClaimsSupport;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.UUID;

/**
 * В Гимпануме клеймы не держатся.
 *
 * <p>Измерение задумано как ничейная территория: всё, что там построено,
 * можно сломать, и поглотители космоса ломают в том числе базы. Клейм сводил
 * бы это на нет.
 *
 * <p>Клеймового события ни один из клеймовых модов не даёт, поэтому клейм не
 * запрещается, а снимается: раз в пару секунд чанки вокруг игроков
 * проверяются, и чужая заявка отменяется. Заявки нашего служебного игрока
 * остаются — на них держатся Контрольные точки.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class ClaimGuard {

    private static final int SCAN_INTERVAL = 40;
    private static final int RADIUS = 3;

    private ClaimGuard() {
    }

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !NebulaPortal.GIMPANUM.equals(level.dimension())
                || level.getGameTime() % SCAN_INTERVAL != 0
                || level.players().isEmpty()
                || !ClaimsSupport.isAvailable(level.getServer())) {
            return;
        }

        LongSet seen = new LongOpenHashSet();
        for (ServerPlayer player : level.players()) {
            ChunkPos centre = player.chunkPosition();
            boolean stripped = false;
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    ChunkPos chunk = new ChunkPos(centre.x + dx, centre.z + dz);
                    if (!seen.add(chunk.toLong())) {
                        continue;
                    }
                    BlockPos pos = chunk.getMiddleBlockPosition(level.getMinBuildHeight() + 1);
                    UUID owner = ClaimsSupport.ownerAt(level, pos);
                    if (owner != null && !CoreFakePlayer.UUID_VALUE.equals(owner)) {
                        ClaimsSupport.unclaim(level, pos);
                        stripped = true;
                    }
                }
            }
            if (stripped) {
                player.displayClientMessage(
                        Component.translatable("gimpanum.claim.forbidden").withStyle(ChatFormatting.RED),
                        true);
            }
        }
    }
}
