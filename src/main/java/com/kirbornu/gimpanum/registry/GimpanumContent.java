package com.kirbornu.gimpanum.registry;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.debug.ProbeBlock;
import com.kirbornu.gimpanum.debug.ProbeBlockEntity;
import net.minecraft.core.registries.Registries;
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

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gimpanum.main"))
                    .icon(() -> new ItemStack(PROBE.get()))
                    .displayItems((params, output) -> output.accept(PROBE_ITEM.get()))
                    .build()
    );

    private GimpanumContent() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        TABS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }

    /** Удобный доступ без {@code .get()} на каждом вызове. */
    public static Block probe() {
        return PROBE.get();
    }
}
