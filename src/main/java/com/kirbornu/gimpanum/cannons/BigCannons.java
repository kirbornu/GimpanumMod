package com.kirbornu.gimpanum.cannons;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import rbasamoyai.createbigcannons.index.CBCMunitionPropertiesHandlers;
import rbasamoyai.createbigcannons.munitions.big_cannon.ProjectileBlockItem;
import rbasamoyai.createbigcannons.munitions.config.MunitionPropertiesHandler;

/**
 * Всё, что Гимпанум добавляет в Create Big Cannons.
 *
 * <p>Отдельный класс не ради порядка, а по необходимости: он ссылается на
 * классы CBC, и одна загрузка этого класса без CBC роняет запуск —
 * проверено. Поэтому проверка «стоит ли CBC» живёт не здесь, а в
 * {@link com.kirbornu.gimpanum.Gimpanum}: класс, который спрашивает, обязан
 * сам быть чистым от CBC, иначе вопрос уронит сервер раньше ответа. Java
 * загружает класс лениво, и пока в него не зашли, его как бы и нет.
 *
 * <p>Внутри — три записи в реестры и две подписки. Снаряд-блок заряжают в
 * пушку; снаряд-сущность летит; блок-сущность ему нужна чужая, поэтому наш
 * блок дописывается в чужой {@code fuzed_block} через
 * {@code BlockEntityTypeAddBlocksEvent}. Числа — сила взрыва и урон — лежат в
 * датапаке, потому что так устроен сам CBC.
 */
public final class BigCannons {

    public static final String CBC = "createbigcannons";

    private static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(Gimpanum.MOD_ID);
    private static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(Gimpanum.MOD_ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Gimpanum.MOD_ID);

    public static final DeferredBlock<FuriousShellBlock> FURIOUS_SHELL = BLOCKS.registerBlock(
            "furious_shell",
            FuriousShellBlock::new,
            BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .strength(4.0F)
                    .noOcclusion());

    public static final DeferredItem<ProjectileBlockItem> FURIOUS_SHELL_ITEM = ITEMS.registerItem(
            "furious_shell",
            properties -> new ProjectileBlockItem(FURIOUS_SHELL.get(), properties),
            new Item.Properties());

    /**
     * Тип летящего снаряда.
     *
     * <p>Числа взяты у снарядов CBC один в один: размер, бессмертие к огню,
     * частота обновления и дальность слежения. Отличаться в этом от остальных
     * снарядов незачем — отличие только в силе взрыва, а она в датапаке.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<FuriousShellProjectile>> FURIOUS_SHELL_PROJECTILE =
            ENTITIES.register("furious_shell", () -> EntityType.Builder
                    .<FuriousShellProjectile>of(FuriousShellProjectile::new, MobCategory.MISC)
                    .sized(0.8F, 0.8F)
                    .fireImmune()
                    .updateInterval(1)
                    .setShouldReceiveVelocityUpdates(false)
                    .clientTrackingRange(16)
                    .build("furious_shell"));

    private BigCannons() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        ENTITIES.register(modBus);
        modBus.addListener(BigCannons::setup);
        modBus.addListener(BigCannons::addToTab);
        modBus.addListener(BigCannons::attachBlockEntity);
        if (FMLEnvironment.dist.isClient()) {
            // Ссылка на клиентский класс живёт только внутри этой ветки:
            // на сервере он так и не будет загружен.
            modBus.addListener(BigCannonsClient::registerRenderers);
        }
    }

    /**
     * Записывает снаряд в разборщик свойств CBC.
     *
     * <p>Без этого CBC не найдёт для нашего типа никаких настроек и выдаст
     * значения по умолчанию — снаряд полетит, но взрываться будет как пустышка.
     */
    private static void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> MunitionPropertiesHandler.registerProjectileHandler(
                FURIOUS_SHELL_PROJECTILE.get(),
                CBCMunitionPropertiesHandlers.COMMON_SHELL_BIG_CANNON_PROJECTILE));
    }

    private static void addToTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == GimpanumContent.TAB.getKey()) {
            event.accept(FURIOUS_SHELL_ITEM.get());
        }
    }

    /**
     * Дописывает наш блок в чужую блок-сущность.
     *
     * <p>Своей заводить незачем: снаряду нужна ровно та, что у остальных
     * снарядов CBC, — она хранит взрыватель и трассер. Тип блок-сущности знает
     * список своих блоков наперёд, и вписаться в него можно только этим
     * событием NeoForge.
     */
    private static void attachBlockEntity(BlockEntityTypeAddBlocksEvent event) {
        BlockEntityType<?> fuzed = BuiltInRegistries.BLOCK_ENTITY_TYPE
                .get(ResourceLocation.fromNamespaceAndPath(CBC, "fuzed_block"));
        if (fuzed == null) {
            Gimpanum.LOGGER.error("В Create Big Cannons не нашлось fuzed_block — "
                    + "Яростный снаряд останется без взрывателя");
            return;
        }
        event.modify(fuzed, FURIOUS_SHELL.get());
    }
}
