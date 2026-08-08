package com.kirbornu.gimpanum.converter;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/** Копилка Фонос-конвертера и его настройка. */
public class ConverterBlockEntity extends BlockEntity {

    private static final String KEY_CONFIG = "GimpanumConverterConfig";
    private static final String KEY_PROGRESS = "GimpanumConverterProgress";

    /**
     * Как часто конвертер осматривается в поисках брошенного.
     *
     * <p>Четверть секунды: глазу неотличимо от мгновенного, а осмотр области
     * ради каждого тика ради стоящего блока — расточительство. Воронки в ваниле
     * живут с похожим интервалом.
     */
    private static final int ABSORB_INTERVAL_TICKS = 5;

    private ConverterConfig config = ConverterConfig.EMPTY;

    /** Сколько принятого набрано к текущей квоте. */
    private int progress;

    private int absorbTimer;

    public ConverterBlockEntity(BlockPos pos, BlockState state) {
        super(GimpanumContent.PHONOS_CONVERTER_BLOCK_ENTITY.get(), pos, state);
    }

    public ConverterConfig config() {
        return config;
    }

    public int progress() {
        return progress;
    }

    public void setConfig(ConverterConfig config) {
        this.config = config;
        setChanged();
    }

    /** Обнуляет копилку, не трогая условия обмена. */
    public void resetProgress() {
        progress = 0;
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            ConverterIndex.put(serverLevel.getServer(), level.dimension(), worldPosition);
        }
    }

    // --- Приём и выдача ------------------------------------------------------

    public void serverTick() {
        try {
            tickInternal();
        } catch (Throwable t) {
            // Тик идёт в общем цикле блок-сущностей: исключение отсюда
            // остановило бы тик всего чанка.
            Gimpanum.LOGGER.error("Тик Фонос-конвертера в {} прерван ошибкой",
                    worldPosition.toShortString(), t);
        }
    }

    private void tickInternal() {
        if (!(level instanceof ServerLevel serverLevel) || !config.isOperational()) {
            return;
        }
        if (++absorbTimer < ABSORB_INTERVAL_TICKS) {
            return;
        }
        absorbTimer = 0;

        int taken = absorbNearbyItems(serverLevel);
        if (taken <= 0) {
            return;
        }
        progress += taken;
        setChanged();
        payOut(serverLevel);
    }

    /**
     * Забирает лежащее на конвертере и вокруг него.
     *
     * <p>Область — сам блок, расширенный на полблока по сторонам и на блок
     * вверх: предмет, брошенный через Q, обычно ложится сверху, но может и
     * скатиться к подножию.
     *
     * <p>Стопка забирается целиком, даже если она больше остатка квоты. Излишек
     * остаётся в копилке и достанется следующему — в этом и смысл общей
     * копилки.
     */
    private int absorbNearbyItems(ServerLevel serverLevel) {
        AABB area = new AABB(worldPosition).inflate(0.5, 0.0, 0.5)
                .expandTowards(0.0, 1.0, 0.0);
        List<ItemEntity> nearby = serverLevel.getEntitiesOfClass(ItemEntity.class, area,
                entity -> entity.isAlive() && config.matchesInput(entity.getItem()));

        int taken = 0;
        for (ItemEntity entity : nearby) {
            taken += entity.getItem().getCount();
            entity.discard();
        }
        return taken;
    }

    /**
     * Выдаёт столько раз, сколько квот набралось.
     *
     * <p>Принесённое сверх квоты не пропадает: остаток переходит к следующей.
     */
    private void payOut(ServerLevel serverLevel) {
        int quota = config.effectiveQuota();
        int completed = progress / quota;
        if (completed <= 0) {
            return;
        }
        progress -= completed * quota;
        setChanged();

        drop(serverLevel, completed * config.outputCount());
    }

    /**
     * Роняет награду перед конвертером, разбивая её по стопкам.
     *
     * <p>Стопка больше {@code ItemStack.CODEC}-предела не пережила бы сохранение
     * мира, поэтому крупная выдача выпадает несколькими сущностями — так же, как
     * это делает ваниль.
     */
    private void drop(ServerLevel serverLevel, int total) {
        ItemStack prototype = config.outputStack(1);
        if (prototype.isEmpty()) {
            return;
        }
        int remaining = total;
        int perStack = Math.max(1, prototype.getMaxStackSize());
        while (remaining > 0) {
            int size = Math.min(remaining, perStack);
            Block.popResource(serverLevel, worldPosition.above(), prototype.copyWithCount(size));
            remaining -= size;
        }
    }

    // --- Осмотр --------------------------------------------------------------

    /**
     * Курс обмена и состояние копилки — то, что игрок обязан видеть до броска.
     *
     * <p>Получатель задаётся снаружи, а не берётся игроком: тот же текст уходит
     * и в чат по правой кнопке, и в вывод команды, которую может выполнить
     * консоль, где игрока нет вовсе.
     */
    public void describe(Consumer<Component> sink) {
        sink.accept(Component.translatable("gimpanum.converter.header", label())
                .withStyle(ChatFormatting.GOLD));

        if (!config.isOperational()) {
            sink.accept(Component.translatable("gimpanum.converter.idle")
                    .withStyle(ChatFormatting.RED));
            return;
        }

        sink.accept(Component.translatable("gimpanum.converter.takes",
                        describeItem(config.input()), config.effectiveQuota())
                .withStyle(ChatFormatting.AQUA));
        sink.accept(Component.translatable("gimpanum.converter.gives",
                        describeItem(config.output()), config.outputCount())
                .withStyle(ChatFormatting.GREEN));
        sink.accept(Component.translatable("gimpanum.converter.progress",
                        progress, config.effectiveQuota(), config.effectiveQuota() - progress)
                .withStyle(ChatFormatting.GRAY));
    }

    public void describeTo(Player player) {
        describe(player::sendSystemMessage);
    }

    public String label() {
        return config.label().orElseGet(() -> Component
                .translatable("block.gimpanum.phonos_converter").getString());
    }

    /** Показывает предмет так, как игрок увидит его в руке — с именем и цветом. */
    private Component describeItem(Optional<ItemStack> stack) {
        return stack.map(ItemStack::getHoverName)
                .orElseGet(() -> Component.translatable("gimpanum.converter.unknown_item"));
    }

    // --- Хранение ------------------------------------------------------------

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progress = tag.getInt(KEY_PROGRESS);
        if (tag.contains(KEY_CONFIG)) {
            config = ConverterConfig.CODEC
                    .parse(NbtOps.INSTANCE, tag.get(KEY_CONFIG))
                    .resultOrPartial(error -> Gimpanum.LOGGER.error(
                            "Настройка конвертера в {} повреждена: {}",
                            getBlockPos().toShortString(), error))
                    .orElse(ConverterConfig.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(KEY_PROGRESS, progress);
        ConverterConfig.CODEC.encodeStart(NbtOps.INSTANCE, config)
                .resultOrPartial(error -> Gimpanum.LOGGER.error(
                        "Не удалось сохранить настройку конвертера в {}: {}",
                        getBlockPos().toShortString(), error))
                .ifPresent(encoded -> tag.put(KEY_CONFIG, encoded));
    }
}
