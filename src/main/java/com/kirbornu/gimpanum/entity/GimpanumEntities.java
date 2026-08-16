package com.kirbornu.gimpanum.entity;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.registry.GimpanumContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacementTypes;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.entity.RegisterSpawnPlacementsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Обитатели Гимпанума.
 *
 * <p>Условия появления заданы своими предикатами, а не ванильными: ванильный
 * требует темноты, а у Гимпанума в типе измерения стоит порог освещения 0 при
 * вечном полудне — с ним на поверхности не появился бы никто. Поэтому свет в
 * условиях не участвует вовсе, вместо него — высота и порода под ногами.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class GimpanumEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(Registries.ENTITY_TYPE, Gimpanum.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CometWraith>> COMET_WRAITH =
            ENTITIES.register("comet_wraith", () -> EntityType.Builder
                    .of(CometWraith::new, MobCategory.MONSTER)
                    .sized(0.7F, 0.8F)
                    .clientTrackingRange(10)
                    .fireImmune()
                    .build("comet_wraith"));

    public static final DeferredHolder<EntityType<?>, EntityType<DuneWalker>> DUNE_WALKER =
            ENTITIES.register("dune_walker", () -> EntityType.Builder
                    .of(DuneWalker::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .eyeHeight(1.74F)
                    .clientTrackingRange(8)
                    .build("dune_walker"));

    public static final DeferredHolder<EntityType<?>, EntityType<SpaceDevourer>> SPACE_DEVOURER =
            ENTITIES.register("space_devourer", () -> EntityType.Builder
                    .of(SpaceDevourer::new, MobCategory.MONSTER)
                    .sized(4.0F, 3.0F)
                    .eyeHeight(2.0F)
                    .clientTrackingRange(8)
                    .build("space_devourer"));

    public static final DeferredHolder<EntityType<?>, EntityType<PlasmaBolt>> PLASMA_BOLT =
            ENTITIES.register("plasma_bolt", () -> EntityType.Builder
                    .of(PlasmaBolt::new, MobCategory.MONSTER)
                    .sized(1.2F, 4.0F)
                    .eyeHeight(3.5F)
                    .clientTrackingRange(16)
                    .fireImmune()
                    .build("plasma_bolt"));

    /** Разряд Плазменной молнии — отдельная сущность ради удара молнии при попадании. */
    public static final DeferredHolder<EntityType<?>, EntityType<PlasmaProjectile>> PLASMA_PROJECTILE =
            ENTITIES.register("plasma_projectile", () -> EntityType.Builder
                    .<PlasmaProjectile>of(PlasmaProjectile::new, MobCategory.MISC)
                    .sized(0.3125F, 0.3125F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("plasma_projectile"));

    public static final DeferredItem<Item> COMET_WRAITH_EGG = egg("comet_wraith", COMET_WRAITH, 0x8FD8E6, 0xEAFBFF);
    public static final DeferredItem<Item> DUNE_WALKER_EGG = egg("dune_walker", DUNE_WALKER, 0x8B877D, 0xCFCABE);
    public static final DeferredItem<Item> SPACE_DEVOURER_EGG = egg("space_devourer", SPACE_DEVOURER, 0x0C0A11, 0x4A3E62);
    public static final DeferredItem<Item> PLASMA_BOLT_EGG = egg("plasma_bolt", PLASMA_BOLT, 0x2C4A9E, 0xB6D6FF);

    private GimpanumEntities() {
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
    }

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(COMET_WRAITH.get(), CometWraith.createAttributes().build());
        event.put(DUNE_WALKER.get(), DuneWalker.createAttributes().build());
        event.put(SPACE_DEVOURER.get(), SpaceDevourer.createAttributes().build());
        event.put(PLASMA_BOLT.get(), PlasmaBolt.createAttributes().build());
    }

    @SubscribeEvent
    public static void placements(RegisterSpawnPlacementsEvent event) {
        event.register(DUNE_WALKER.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) -> onSand(level, pos),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(SPACE_DEVOURER.get(), SpawnPlacementTypes.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) -> inCaves(level, pos, 60),
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(COMET_WRAITH.get(), SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) -> inAir(level, pos) && pos.getY() <= 20,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);

        event.register(PLASMA_BOLT.get(), SpawnPlacementTypes.NO_RESTRICTIONS,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                (type, level, spawnType, pos, random) -> inAir(level, pos) && pos.getY() >= 104,
                RegisterSpawnPlacementsEvent.Operation.REPLACE);
    }

    /** Поверхность пустыни: под ногами космический песок, над головой пусто. */
    private static boolean onSand(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(GimpanumContent.COSMIC_SAND.get())
                && inAir(level, pos);
    }

    /** Пол лабиринта: любая твердь ниже заданной высоты. */
    private static boolean inCaves(LevelAccessor level, BlockPos pos, int ceiling) {
        return pos.getY() < ceiling
                && level.getBlockState(pos.below()).isSolidRender(level, pos.below())
                && inAir(level, pos);
    }

    private static boolean inAir(LevelAccessor level, BlockPos pos) {
        return level.getBlockState(pos).isAir() && level.getBlockState(pos.above()).isAir();
    }

    private static DeferredItem<Item> egg(String name, DeferredHolder<EntityType<?>, ? extends EntityType<? extends Mob>> type,
                                          int background, int highlight) {
        return GimpanumContent.ITEMS.registerItem(name + "_spawn_egg",
                properties -> new DeferredSpawnEggItem(type::get, background, highlight, properties));
    }
}
