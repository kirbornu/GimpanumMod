package com.kirbornu.gimpanum.debug;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.destruction.DestructionArbiter;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

/**
 * Носитель постоянного идентификатора для зонда.
 *
 * <p>Идентификатор обязан пережить переезд блока при сборке конструкции —
 * именно по нему {@link DestructionArbiter} понимает, что блок не уничтожен, а
 * перемещён. Заодно этот зонд проверяет само предположение, что NBT
 * блок-сущности переживает сборку: если UUID после сборки сменится, вся схема
 * не работает, и это будет видно в логе.
 */
public class ProbeBlockEntity extends BlockEntity {

    private static final String KEY_ID = "GimpanumBlockId";

    private UUID blockId;

    public ProbeBlockEntity(BlockPos pos, BlockState state) {
        super(GimpanumContent.PROBE_BLOCK_ENTITY.get(), pos, state);
    }

    /** Идентификатор, создаваемый при первом обращении и далее постоянный. */
    public UUID blockId() {
        if (blockId == null) {
            blockId = UUID.randomUUID();
            setChanged();
        }
        return blockId;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            // Появление блока где угодно отменяет ожидающее удаление с тем же
            // идентификатором — значит блок переехал, а не погиб.
            DestructionArbiter.onAppeared(blockId());
            Gimpanum.LOGGER.info("[зонд] появился id={} сырой={} наКонструкции={} тик={}",
                    blockId(), getBlockPos().toShortString(),
                    com.kirbornu.gimpanum.sublevel.SubLevelSupport.isOnSubLevel(level, getBlockPos()),
                    DestructionArbiter.currentTick());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(KEY_ID)) {
            blockId = tag.getUUID(KEY_ID);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID(KEY_ID, blockId());
    }
}
