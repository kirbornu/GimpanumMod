package com.kirbornu.gimpanum.core;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.destruction.DestructionArbiter;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import com.kirbornu.gimpanum.sublevel.SubLevelSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;
import java.util.UUID;

/** Хранилище настройки Ядра и его постоянного идентификатора. */
public class CoreBlockEntity extends BlockEntity {

    private static final String KEY_ID = "GimpanumCoreId";
    private static final String KEY_CONFIG = "GimpanumCoreConfig";

    private UUID coreId;
    private CoreConfig config = CoreConfig.EMPTY;

    /**
     * Отсчёт до следующей выдачи предмета. Намеренно не сохраняется: после
     * перезагрузки отсчёт начинается заново, и это честнее, чем выдать всё
     * накопленное разом.
     */
    private int spawnTimer;

    public CoreBlockEntity(BlockPos pos, BlockState state) {
        super(GimpanumContent.CORE_BLOCK_ENTITY.get(), pos, state);
    }

    /**
     * Постоянный идентификатор Ядра. Обязан пережить сборку конструкции: по
     * нему {@link DestructionArbiter} отличает переезд от уничтожения.
     */
    public UUID coreId() {
        if (coreId == null) {
            coreId = UUID.randomUUID();
            setChanged();
        }
        return coreId;
    }

    public CoreConfig config() {
        return config;
    }

    public void setConfig(CoreConfig config) {
        CoreConfig previous = this.config;
        this.config = config;
        setChanged();

        if (level == null || level.isClientSide) {
            return;
        }
        if (!previous.name().equals(config.name())) {
            CoreIndex.put(config.name(), coreId(), level.dimension(), worldPosition);
        }
        if (previous.invulnerable() != config.invulnerable()) {
            syncInvulnerableState();
        }
        sendToClients();
    }

    /**
     * Толкает настройку клиентам.
     *
     * <p>Без этого не работает копирование средней кнопкой: pick-block целиком
     * клиентская операция, и {@code getCloneItemStack} читает ту блок-сущность,
     * что есть у клиента. Несинхронизированная настройка означала бы копию с
     * пустыми полями.
     */
    private void sendToClients() {
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Переносит признак неразрушимости в состояние блока: неподвижность для
     * поршней читается только из состояния, позиция там недоступна.
     */
    private void syncInvulnerableState() {
        BlockState state = getBlockState();
        if (!state.hasProperty(CoreBlock.INVULNERABLE)
                || state.getValue(CoreBlock.INVULNERABLE) == config.invulnerable()) {
            return;
        }
        // Блок тот же, меняется только свойство, поэтому onRemove не сработает
        // и ложного «уничтожения» не будет.
        level.setBlock(worldPosition,
                state.setValue(CoreBlock.INVULNERABLE, config.invulnerable()),
                Block.UPDATE_ALL);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide) {
            return;
        }

        // Появление Ядра где угодно отменяет ожидающее удаление с тем же
        // идентификатором: значит блок переехал, а не погиб.
        DestructionArbiter.onAppeared(coreId());

        if (config.name().isEmpty()) {
            config = config.withName(CoreIndex.nextFreeName());
            setChanged();
        }
        CoreIndex.put(config.name(), coreId(), level.dimension(), worldPosition);
        syncInvulnerableState();
    }

    /**
     * Принимает настройку из предмета при установке Ядра.
     *
     * <p>Имя в шаблоне пустое, поэтому {@link #onLoad} выдаст новому Ядру
     * собственное. Идентификатор в настройку не входит и потому не копируется —
     * иначе две копии оказались бы для арбитра одним и тем же блоком.
     */
    @Override
    protected void applyImplicitComponents(DataComponentInput input) {
        super.applyImplicitComponents(input);
        CoreConfig stored = input.get(GimpanumContent.CORE_CONFIG.get());
        if (stored != null) {
            config = stored.asTemplate();
            setChanged();
        }
    }

    /** Отдаёт настройку в предмет при копировании Ядра. */
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder builder) {
        super.collectImplicitComponents(builder);
        builder.set(GimpanumContent.CORE_CONFIG.get(), config.asTemplate());
    }

    /**
     * Периодическая выдача предмета.
     *
     * <p>Предохранитель здесь ни при чём: он отвечает за последствия гибели, а
     * выдача — отдельный тег со своим выключателем.
     */
    public void serverTick() {
        if (!config.spawnEnabled()) {
            spawnTimer = 0;
            return;
        }
        if (++spawnTimer < config.spawnIntervalTicks()) {
            return;
        }
        spawnTimer = 0;

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // Мировая позиция, а не BlockPos: на конструкции они разные.
        Vec3 worldPos = SubLevelSupport.worldCenter(level, worldPosition);

        Optional<ResourceLocation> customItem = config.spawnItem();
        if (customItem.isEmpty()) {
            SealDrops.spawnSeal(serverLevel, worldPos, config.sealContents());
            return;
        }

        Item item = BuiltInRegistries.ITEM.getOptional(customItem.get()).orElse(null);
        if (item == null) {
            Gimpanum.LOGGER.warn("Ядро '{}': предмет {} не найден, выдача пропущена",
                    config.name(), customItem.get());
            return;
        }
        SealDrops.spawn(serverLevel, worldPos, new ItemStack(item));
    }

    /** Данные для клиента при загрузке чанка. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    /** Данные для клиента при изменении настройки. */
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID(KEY_ID)) {
            coreId = tag.getUUID(KEY_ID);
        }
        if (tag.contains(KEY_CONFIG)) {
            config = CoreConfig.CODEC
                    .parse(NbtOps.INSTANCE, tag.get(KEY_CONFIG))
                    .resultOrPartial(error -> Gimpanum.LOGGER.error(
                            "Настройка Ядра в {} повреждена: {}", getBlockPos().toShortString(), error))
                    .orElse(CoreConfig.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID(KEY_ID, coreId());
        CoreConfig.CODEC.encodeStart(NbtOps.INSTANCE, config)
                .resultOrPartial(error -> Gimpanum.LOGGER.error(
                        "Не удалось сохранить настройку Ядра в {}: {}",
                        getBlockPos().toShortString(), error))
                .ifPresent(encoded -> tag.put(KEY_CONFIG, encoded));
    }
}
