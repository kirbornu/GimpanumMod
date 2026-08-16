package com.kirbornu.gimpanum.registry;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.capture.CapturePointBlock;
import com.kirbornu.gimpanum.capture.CapturePointBlockEntity;
import com.kirbornu.gimpanum.converter.ConverterBlock;
import com.kirbornu.gimpanum.converter.ConverterBlockEntity;
import com.kirbornu.gimpanum.core.CoreBlock;
import com.kirbornu.gimpanum.core.CoreBlockEntity;
import com.kirbornu.gimpanum.core.CoreBlockItem;
import com.kirbornu.gimpanum.core.CoreConfig;
import com.kirbornu.gimpanum.debug.ProbeBlock;
import com.kirbornu.gimpanum.dimension.NebulaPortalBlock;
import com.kirbornu.gimpanum.dimension.NebulaPortalBlockEntity;
import com.kirbornu.gimpanum.dimension.ScorchingGasBlock;
import com.kirbornu.gimpanum.debug.ProbeBlockEntity;
import com.kirbornu.gimpanum.item.SealContents;
import com.kirbornu.gimpanum.item.SealItem;
import com.kirbornu.gimpanum.item.NebulaWoodItem;
import com.kirbornu.gimpanum.recipe.ThawingRecipe;
import com.kirbornu.gimpanum.worldgen.NebulaTreeFeature;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SignItem;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCookingSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public final class GimpanumContent {

    /**
     * Наибольшая стопка, которую выдерживает игра.
     *
     * <p>Не наше решение: {@code ItemStack.CODEC} в 1.21.1 принимает количество
     * только в диапазоне {@code 1..99}, и через него сохраняется каждый предмет
     * в мире. Больше — предмет не переживёт сохранение.
     */
    public static final int MAX_VANILLA_STACK = 99;

    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Gimpanum.MOD_ID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Gimpanum.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Gimpanum.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, Gimpanum.MOD_ID);
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Gimpanum.MOD_ID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, Gimpanum.MOD_ID);
    public static final DeferredRegister<Feature<?>> FEATURES =
            DeferredRegister.create(Registries.FEATURE, Gimpanum.MOD_ID);

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

    /**
     * Контрольная точка. {@code strength(-1, 3600000)} — те же числа, что у
     * бедрока, и это не украшение:
     * <ul>
     *   <li>нулевая прочность {@code -1} делает блок неломаемым в выживании и
     *       заодно отсеивает его из сборки конструкций — {@code Simulated}
     *       проверяет ровно это значение;</li>
     *   <li>сопротивление взрыву читает и Create Big Cannons через
     *       безаргументный {@code getExplosionResistance()}, поэтому снаряды
     *       точку не берут и датапак брони ей не нужен.</li>
     * </ul>
     *
     * <p>{@code noLootTable}: точку, как и Ядро, нельзя унести — только снести
     * в креативе.
     */
    public static final DeferredBlock<CapturePointBlock> CAPTURE_POINT = BLOCKS.registerBlock(
            "capture_point",
            CapturePointBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .strength(-1.0F, 3_600_000.0F)
                    .lightLevel(state -> 10)
                    .noLootTable()
    );

    public static final DeferredItem<?> CAPTURE_POINT_ITEM = ITEMS.registerSimpleBlockItem(CAPTURE_POINT);

    public static final Supplier<BlockEntityType<CapturePointBlockEntity>> CAPTURE_POINT_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("capture_point",
                    () -> BlockEntityType.Builder.of(CapturePointBlockEntity::new,
                            CAPTURE_POINT.get()).build(null));

    /**
     * Космический песок — из него состоит почти весь Гимпанум.
     *
     * <p>Обычный {@code Block}, а не {@code FallingBlock}: он намеренно не
     * осыпается. Иначе пещеры-лабиринты под пустыней обрушились бы сами на
     * себя при первой же генерации.
     *
     * <p>Никакой руды в измерении нет, и ценность у песка одна — промывка в
     * Create; рецепт лежит в {@code data/gimpanum/recipe/splashing}.
     */
    public static final DeferredBlock<Block> COSMIC_SAND = BLOCKS.registerSimpleBlock(
            "cosmic_sand",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.SAND)
                    .sound(SoundType.SAND)
                    .strength(0.5F)
    );

    public static final DeferredItem<?> COSMIC_SAND_ITEM = ITEMS.registerSimpleBlockItem(COSMIC_SAND);

    /**
     * Космический пепел — то, во что переходит песок у самого дна мира.
     *
     * <p>Как и песок, не осыпается. Промывка даёт не металлы, а обломок
     * незерита, и только с половиной процента — ради него и стоит лезть на дно.
     */
    public static final DeferredBlock<Block> COSMIC_ASH = BLOCKS.registerSimpleBlock(
            "cosmic_ash",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .sound(SoundType.SAND)
                    .strength(0.6F)
    );

    public static final DeferredItem<?> COSMIC_ASH_ITEM = ITEMS.registerSimpleBlockItem(COSMIC_ASH);

    /**
     * Раскалённый небула-газ — редкие вкрапления по всей толще песка.
     *
     * <p>Светится и жжёт наступившего. {@code noOcclusion} нужен из-за
     * полупрозрачной текстуры: без него соседние грани пропадут.
     */
    public static final DeferredBlock<ScorchingGasBlock> SCORCHING_NEBULA_GAS = BLOCKS.registerBlock(
            "scorching_nebula_gas",
            ScorchingGasBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .sound(SoundType.GLASS)
                    .strength(0.4F)
                    .lightLevel(state -> 11)
                    .noOcclusion()
    );

    public static final DeferredItem<?> SCORCHING_NEBULA_GAS_ITEM =
            ITEMS.registerSimpleBlockItem(SCORCHING_NEBULA_GAS);

    /**
     * Замороженная органика — руда в обломках астероидов.
     *
     * <p>Металла в ней нет: это занесённая из чужой системы жизнь, вмёрзшая в
     * камень. В печи она оттаивает случайным растением — см. {@link ThawingRecipe}.
     */
    public static final DeferredBlock<Block> FROZEN_ORGANICS = BLOCKS.registerSimpleBlock(
            "frozen_organics",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .sound(SoundType.STONE)
                    .strength(3.0F, 3.0F)
                    .requiresCorrectToolForDrops()
    );

    public static final DeferredItem<?> FROZEN_ORGANICS_ITEM = ITEMS.registerSimpleBlockItem(FROZEN_ORGANICS);

    /**
     * Небула-бревно — ствол пещерной поросли Гимпанума.
     *
     * <p>Ведёт себя как обычное бревно во всём, кроме огня: в мире без воздуха
     * гореть нечему, поэтому топливность снята в {@link NebulaWoodItem}.
     */
    public static final DeferredBlock<RotatedPillarBlock> NEBULA_LOG = BLOCKS.registerBlock(
            "nebula_log",
            RotatedPillarBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.WOOD)
                    .strength(2.0F)
    );

    public static final DeferredItem<NebulaWoodItem> NEBULA_LOG_ITEM = ITEMS.registerItem(
            "nebula_log",
            properties -> new NebulaWoodItem(NEBULA_LOG.get(), properties)
    );

    /** Небула-доски. Лежат в ванильном теге досок, но не горят. */
    public static final DeferredBlock<Block> NEBULA_PLANKS = BLOCKS.registerSimpleBlock(
            "nebula_planks",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.WOOD)
                    .strength(2.0F, 3.0F)
    );

    public static final DeferredItem<NebulaWoodItem> NEBULA_PLANKS_ITEM = ITEMS.registerItem(
            "nebula_planks",
            properties -> new NebulaWoodItem(NEBULA_PLANKS.get(), properties)
    );

    /**
     * Полный строительный набор из небула-древесины.
     *
     * <p>Тип блоков и древесины берём дубовые: они отвечают только за звуки
     * двери, калитки и кнопки, а свой пришлось бы регистрировать до всего
     * остального, ради одного лишь скрипа.
     *
     * <p>Все предметы набора — {@link NebulaWoodItem}: топливом не должна быть
     * ни одна часть, иначе печь топили бы ступеньками.
     */
    private static final BlockSetType SET = BlockSetType.OAK;
    private static final WoodType WOOD = WoodType.OAK;

    public static final DeferredBlock<RotatedPillarBlock> NEBULA_WOOD = BLOCKS.registerBlock(
            "nebula_wood", RotatedPillarBlock::new, woodProperties());
    public static final DeferredItem<NebulaWoodItem> NEBULA_WOOD_ITEM = woodItem("nebula_wood", NEBULA_WOOD);

    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_NEBULA_LOG = BLOCKS.registerBlock(
            "stripped_nebula_log", RotatedPillarBlock::new, woodProperties());
    public static final DeferredItem<NebulaWoodItem> STRIPPED_NEBULA_LOG_ITEM =
            woodItem("stripped_nebula_log", STRIPPED_NEBULA_LOG);

    public static final DeferredBlock<RotatedPillarBlock> STRIPPED_NEBULA_WOOD = BLOCKS.registerBlock(
            "stripped_nebula_wood", RotatedPillarBlock::new, woodProperties());
    public static final DeferredItem<NebulaWoodItem> STRIPPED_NEBULA_WOOD_ITEM =
            woodItem("stripped_nebula_wood", STRIPPED_NEBULA_WOOD);

    public static final DeferredBlock<StairBlock> NEBULA_STAIRS = BLOCKS.registerBlock(
            "nebula_stairs",
            properties -> new StairBlock(NEBULA_PLANKS.get().defaultBlockState(), properties),
            plankProperties());
    public static final DeferredItem<NebulaWoodItem> NEBULA_STAIRS_ITEM = woodItem("nebula_stairs", NEBULA_STAIRS);

    public static final DeferredBlock<SlabBlock> NEBULA_SLAB = BLOCKS.registerBlock(
            "nebula_slab", SlabBlock::new, plankProperties());
    public static final DeferredItem<NebulaWoodItem> NEBULA_SLAB_ITEM = woodItem("nebula_slab", NEBULA_SLAB);

    public static final DeferredBlock<FenceBlock> NEBULA_FENCE = BLOCKS.registerBlock(
            "nebula_fence", FenceBlock::new, plankProperties());
    public static final DeferredItem<NebulaWoodItem> NEBULA_FENCE_ITEM = woodItem("nebula_fence", NEBULA_FENCE);

    public static final DeferredBlock<FenceGateBlock> NEBULA_FENCE_GATE = BLOCKS.registerBlock(
            "nebula_fence_gate",
            properties -> new FenceGateBlock(WOOD, properties),
            plankProperties());
    public static final DeferredItem<NebulaWoodItem> NEBULA_FENCE_GATE_ITEM =
            woodItem("nebula_fence_gate", NEBULA_FENCE_GATE);

    public static final DeferredBlock<DoorBlock> NEBULA_DOOR = BLOCKS.registerBlock(
            "nebula_door",
            properties -> new DoorBlock(SET, properties),
            plankProperties().noOcclusion());
    public static final DeferredItem<NebulaWoodItem> NEBULA_DOOR_ITEM = woodItem("nebula_door", NEBULA_DOOR);

    public static final DeferredBlock<TrapDoorBlock> NEBULA_TRAPDOOR = BLOCKS.registerBlock(
            "nebula_trapdoor",
            properties -> new TrapDoorBlock(SET, properties),
            plankProperties().noOcclusion());
    public static final DeferredItem<NebulaWoodItem> NEBULA_TRAPDOOR_ITEM =
            woodItem("nebula_trapdoor", NEBULA_TRAPDOOR);

    public static final DeferredBlock<ButtonBlock> NEBULA_BUTTON = BLOCKS.registerBlock(
            "nebula_button",
            properties -> new ButtonBlock(SET, 30, properties),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.WOOD).noCollission().strength(0.5F));
    public static final DeferredItem<NebulaWoodItem> NEBULA_BUTTON_ITEM = woodItem("nebula_button", NEBULA_BUTTON);

    public static final DeferredBlock<PressurePlateBlock> NEBULA_PRESSURE_PLATE = BLOCKS.registerBlock(
            "nebula_pressure_plate",
            properties -> new PressurePlateBlock(SET, properties),
            BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.WOOD).noCollission().strength(0.5F));
    public static final DeferredItem<NebulaWoodItem> NEBULA_PRESSURE_PLATE_ITEM =
            woodItem("nebula_pressure_plate", NEBULA_PRESSURE_PLATE);

    /**
     * Свой тип древесины — только ради табличек.
     *
     * <p>Табличка рисуется отдельной моделью с собственной текстурой, и
     * связывает блок с текстурой именно {@code WoodType}. Всему остальному
     * набору он не нужен, поэтому и заведён так поздно.
     */
    public static final WoodType NEBULA_WOOD_TYPE =
            WoodType.register(new WoodType(Gimpanum.MOD_ID + ":nebula", SET));

    public static final DeferredBlock<StandingSignBlock> NEBULA_SIGN = BLOCKS.registerBlock(
            "nebula_sign",
            properties -> new StandingSignBlock(NEBULA_WOOD_TYPE, properties),
            signProperties());

    public static final DeferredBlock<WallSignBlock> NEBULA_WALL_SIGN = BLOCKS.registerBlock(
            "nebula_wall_sign",
            properties -> new WallSignBlock(NEBULA_WOOD_TYPE, properties),
            signProperties());

    /** Предмет один на оба блока: игра сама решает, ставить стоячую или настенную. */
    public static final DeferredItem<SignItem> NEBULA_SIGN_ITEM = ITEMS.registerItem(
            "nebula_sign",
            properties -> new SignItem(properties.stacksTo(16),
                    NEBULA_SIGN.get(), NEBULA_WALL_SIGN.get()));

    private static BlockBehaviour.Properties signProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .sound(SoundType.WOOD)
                .noCollission()
                .strength(1.0F);
    }

    private static BlockBehaviour.Properties woodProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .sound(SoundType.WOOD)
                .strength(2.0F);
    }

    private static BlockBehaviour.Properties plankProperties() {
        return BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .sound(SoundType.WOOD)
                .strength(2.0F, 3.0F);
    }

    private static DeferredItem<NebulaWoodItem> woodItem(String name, Supplier<? extends Block> block) {
        return ITEMS.registerItem(name, properties -> new NebulaWoodItem(block.get(), properties));
    }

    /** Фича, растящая эту самую поросль на полу пещер. */
    public static final DeferredHolder<Feature<?>, NebulaTreeFeature> NEBULA_TREE =
            FEATURES.register("nebula_tree", () -> new NebulaTreeFeature(NoneFeatureConfiguration.CODEC));

    /**
     * Сериализатор переплавки с непредсказуемым выходом.
     *
     * <p>200 тиков — то же время, что у ванильной печи по умолчанию; в самом
     * рецепте оно всё равно задано явно.
     */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCookingSerializer<ThawingRecipe>> THAWING =
            RECIPE_SERIALIZERS.register(
                    "thawing",
                    () -> new SimpleCookingSerializer<>(ThawingRecipe::new, 200)
            );

    /**
     * Рама портальной арки. Неразрушима и без предмета: порталы нельзя ни
     * построить, ни снести — они только находятся.
     */
    public static final DeferredBlock<Block> NEBULA_GATE = BLOCKS.registerSimpleBlock(
            "nebula_gate",
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .strength(-1.0F, 3_600_000.0F)
                    .lightLevel(state -> 6)
                    .noLootTable()
    );

    /** Плоскость перехода внутри арки. */
    public static final DeferredBlock<NebulaPortalBlock> NEBULA_PORTAL = BLOCKS.registerBlock(
            "nebula_portal",
            NebulaPortalBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_MAGENTA)
                    .sound(SoundType.GLASS)
                    .strength(-1.0F, 3_600_000.0F)
                    .lightLevel(state -> 13)
                    .noOcclusion()
                    .noLootTable()
    );

    public static final Supplier<BlockEntityType<NebulaPortalBlockEntity>> NEBULA_PORTAL_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("nebula_portal",
                    () -> BlockEntityType.Builder.of(NebulaPortalBlockEntity::new,
                            NEBULA_PORTAL.get()).build(null));

    /**
     * Фонос-конвертер — обменник, на котором держится экономика карты.
     *
     * <p>Прочность и сопротивление те же, что у Контрольной точки, и по тем же
     * причинам: {@code -1} отсеивает блок из сборки конструкций и делает его
     * неломаемым, а 3 600 000 останавливает снаряды Create Big Cannons. Обменник
     * обязан пережить бой, который идёт вокруг него.
     */
    public static final DeferredBlock<ConverterBlock> PHONOS_CONVERTER = BLOCKS.registerBlock(
            "phonos_converter",
            ConverterBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .strength(-1.0F, 3_600_000.0F)
                    .lightLevel(state -> 8)
                    .noLootTable()
    );

    public static final DeferredItem<?> PHONOS_CONVERTER_ITEM =
            ITEMS.registerSimpleBlockItem(PHONOS_CONVERTER);

    public static final Supplier<BlockEntityType<ConverterBlockEntity>> PHONOS_CONVERTER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("phonos_converter",
                    () -> BlockEntityType.Builder.of(ConverterBlockEntity::new,
                            PHONOS_CONVERTER.get()).build(null));

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

    /**
     * Хрусталик — валюта и ничего кроме. Ни свойств, ни применений: весь смысл в
     * том, чтобы его копили и обменивали между собой.
     *
     * <p>{@code fireResistant} — единственная поблажка, та же, что у Печати:
     * накопленное не должно сгорать в лаве. Срок жизни выброшенного Хрусталика
     * ванильный, в отличие от Печати.
     *
     * <p>Стопка в 99 — не выбор, а потолок игры: {@code ItemStack.CODEC}
     * принимает количество только в диапазоне {@code 1..99}, и через этот кодек
     * сохраняется каждый предмет в каждом сундуке. Стопка больше не пережила бы
     * сохранение мира.
     */
    public static final DeferredItem<Item> CRYSTAL_BEAD = ITEMS.registerSimpleItem(
            "crystal_bead",
            new Item.Properties().fireResistant().stacksTo(MAX_VANILLA_STACK)
    );

    /**
     * Осколок хрусталя — то, что остаётся от некрофагов.
     *
     * <p>Обыкновенный предмет без свойств: вся его ценность в том, во что его
     * можно обменять, а это задаётся настройкой конвертеров, а не кодом.
     */
    public static final DeferredItem<Item> CRYSTAL_SHARD = ITEMS.registerSimpleItem("crystal_shard");

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register(
            "main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.gimpanum.main"))
                    .icon(() -> new ItemStack(CORE.get()))
                    .displayItems((params, output) -> {
                        output.accept(CORE_ITEM.get());
                        output.accept(CAPTURE_POINT_ITEM.get());
                        output.accept(PHONOS_CONVERTER_ITEM.get());
                        output.accept(COSMIC_SAND_ITEM.get());
                        output.accept(COSMIC_ASH_ITEM.get());
                        output.accept(SCORCHING_NEBULA_GAS_ITEM.get());
                        output.accept(FROZEN_ORGANICS_ITEM.get());
                        output.accept(NEBULA_LOG_ITEM.get());
                        output.accept(NEBULA_WOOD_ITEM.get());
                        output.accept(STRIPPED_NEBULA_LOG_ITEM.get());
                        output.accept(STRIPPED_NEBULA_WOOD_ITEM.get());
                        output.accept(NEBULA_PLANKS_ITEM.get());
                        output.accept(NEBULA_STAIRS_ITEM.get());
                        output.accept(NEBULA_SLAB_ITEM.get());
                        output.accept(NEBULA_FENCE_ITEM.get());
                        output.accept(NEBULA_FENCE_GATE_ITEM.get());
                        output.accept(NEBULA_DOOR_ITEM.get());
                        output.accept(NEBULA_TRAPDOOR_ITEM.get());
                        output.accept(NEBULA_BUTTON_ITEM.get());
                        output.accept(NEBULA_PRESSURE_PLATE_ITEM.get());
                        output.accept(NEBULA_SIGN_ITEM.get());
                        output.accept(SEAL.get());
                        output.accept(CRYSTAL_BEAD.get());
                        output.accept(CRYSTAL_SHARD.get());
                        output.accept(com.kirbornu.gimpanum.entity.GimpanumEntities.COMET_WRAITH_EGG.get());
                        output.accept(com.kirbornu.gimpanum.entity.GimpanumEntities.DUNE_WALKER_EGG.get());
                        output.accept(com.kirbornu.gimpanum.entity.GimpanumEntities.SPACE_DEVOURER_EGG.get());
                        output.accept(com.kirbornu.gimpanum.entity.GimpanumEntities.PLASMA_BOLT_EGG.get());
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
        RECIPE_SERIALIZERS.register(modBus);
        FEATURES.register(modBus);
    }

    /** Удобный доступ без {@code .get()} на каждом вызове. */
    public static Block probe() {
        return PROBE.get();
    }
}
