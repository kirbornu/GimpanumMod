package com.kirbornu.gimpanum.registry;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.core.CoreBlock;
import com.kirbornu.gimpanum.core.CoreBlockEntity;
import com.kirbornu.gimpanum.core.CoreBlockItem;
import com.kirbornu.gimpanum.core.CoreConfig;
import com.kirbornu.gimpanum.debug.ProbeBlock;
import com.kirbornu.gimpanum.debug.ProbeBlockEntity;
import com.kirbornu.gimpanum.item.SealContents;
import com.kirbornu.gimpanum.item.SealItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class GimpanumContent {

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Gimpanum.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Gimpanum.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Gimpanum.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Gimpanum.MOD_ID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Gimpanum.MOD_ID);

    /**
     * Диагностический блок-зонд. Не часть задуманной механики — служит только
     * для выяснения того, как Sable ведёт себя с блоками на конструкциях.
     */
    public static final DeferredBlock<ProbeBlock> PROBE = BLOCKS.registerBlock(
            "probe",
            ProbeBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .strength(1.0F)
                    .sound(SoundType.METAL)
    );

    public static final DeferredItem<?> PROBE_ITEM = ITEMS.registerSimpleBlockItem(PROBE);

    public static final Supplier<BlockEntityType<ProbeBlockEntity>> PROBE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("probe",
                    () -> BlockEntityType.Builder.of(ProbeBlockEntity::new, PROBE.get()).build(null));

    /**
     * Ядро. {@code noLootTable} — принципиально: обычный игрок может Ядро
     * только уничтожить, но не унести.
     *
     * <p>Прочность здесь не задаётся: она зависит от тега неразрушимости и
     * потому вычисляется в {@link CoreBlock#getDestroyProgress} и
     * {@link CoreBlock#getExplosionResistance}.
     */
    public static final DeferredBlock<CoreBlock> CORE = BLOCKS.registerBlock(
            "core",
            CoreBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .lightLevel(state -> 7)
                    .noLootTable()
    );

    /**
     * Настройка, перенесённая в предмет. Благодаря этому Ядро копируется
     * средней кнопкой вместе со всеми привязками и тегами.
     */
    public static final Supplier<DataComponentType<CoreConfig>> CORE_CONFIG =
            DATA_COMPONENTS.register("core_config",
                    () -> DataComponentType.<CoreConfig>builder()
                            .persistent(CoreConfig.CODEC)
                            .networkSynchronized(CoreConfig.STREAM_CODEC)
                            .build());

    public static final DeferredItem<CoreBlockItem> CORE_ITEM = ITEMS.registerItem(
            "core",
            properties -> new CoreBlockItem(CORE.get(), properties),
            new Item.Properties()
    );

    public static final Supplier<BlockEntityType<CoreBlockEntity>> CORE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("core",
                    () -> BlockEntityType.Builder.of(CoreBlockEntity::new, CORE.get()).build(null));

    /** Привязки, записанные в Печать. */
    public static final Supplier<DataComponentType<SealContents>> SEAL_CONTENTS =
            DATA_COMPONENTS.register("seal_contents",
                    () -> DataComponentType.<SealContents>builder()
                            .persistent(SealContents.CODEC)
                            .networkSynchronized(SealContents.STREAM_CODEC)
                            .build());

    /** {@code fireResistant} — чтобы Печать не сгорела даже там, где её не защитили. */
    public static final DeferredItem<SealItem> SEAL = ITEMS.registerItem(
            "seal",
            SealItem::new,
            new Item.Properties().stacksTo(1).fireResistant()
    );

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gimpanum.main"))
                    .icon(() -> new ItemStack(CORE.get()))
                    .displayItems((params, output) -> {
                        output.accept(CORE_ITEM.get());
                        output.accept(SEAL.get());
                        output.accept(PROBE_ITEM.get());
                    })
                    .build()
    );

    private GimpanumContent() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        DATA_COMPONENTS.register(modBus);
    }

    /** Удобный доступ без {@code .get()} на каждом вызове. */
    public static Block probe() {
        return PROBE.get();
    }
}
