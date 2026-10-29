package com.kirbornu.gimpanum.magic.spell;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Блоки, которые заклинание создало из ничего, — чтобы магия не стала печатным станком.
 *
 * <p>Заклинание ставит блок за горсть еды и хрусталя. Если такой блок можно
 * было бы сломать и забрать, {@code place_block} с алмазным блоком печатал
 * бы алмазы, а {@code freeze_water} и шёлковое касание — лёд. Поэтому каждый
 * такой блок запоминается здесь, на диске, и:
 * <ul>
 *     <li>сломанный чем угодно — рукой, инструментом с любыми чарами, водой,
 *     поршнем, — ничего не роняет и не даёт опыта;</li>
 *     <li>в взрыве исчезает без добычи (ванильный взрыв роняет добычу в обход
 *     события выпадения, поэтому он обрабатывается отдельно);</li>
 *     <li>поршень его не двигает: сдвинутый блок ушёл бы из-под учёта и стал
 *     бы обычным.</li>
 * </ul>
 *
 * <p>Запоминается только то, что заклинание поставило на пустое или
 * заменяемое место. Если заклинание лишь обводнило чужую ступеньку, ступенька
 * осталась своей и при поломке роняет себя, как обычно. Жидкости не
 * запоминаются: с них взять нечего.
 *
 * <p>Запись забывается, когда блок сломан, когда временный блок убран по
 * сроку, когда на это место ставят другой блок или когда на месте оказался
 * другой блок (так бывает после {@code /setblock}).
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class SpellBlocks extends SavedData {

    private static final String FILE_NAME = "gimpanum_magic_spell_blocks";

    private final Map<ResourceKey<Level>, Map<Long, Block>> blocks = new HashMap<>();

    private SpellBlocks() {
    }

    private static SpellBlocks get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SpellBlocks::new, SpellBlocks::load, null), FILE_NAME);
    }

    /** Запомнить, если заклинание создало блок там, где его не было. */
    public static void noteCreated(ServerLevel level, BlockPos pos, BlockState before, BlockState placed) {
        if (!before.canBeReplaced() || placed.getBlock() instanceof LiquidBlock) {
            return;
        }
        SpellBlocks data = get(level);
        data.blocks.computeIfAbsent(level.dimension(), d -> new HashMap<>()).put(pos.asLong(), placed.getBlock());
        data.setDirty();
    }

    public static void forget(ServerLevel level, BlockPos pos) {
        SpellBlocks data = get(level);
        Map<Long, Block> here = data.blocks.get(level.dimension());
        if (here != null && here.remove(pos.asLong()) != null) {
            data.setDirty();
        }
    }

    /**
     * Создан ли этот блок заклинанием. Запись о другом блоке на этом месте
     * устарела — она забывается, и ответ «нет».
     */
    public static boolean isSpellBlock(ServerLevel level, BlockPos pos, BlockState state) {
        SpellBlocks data = get(level);
        Map<Long, Block> here = data.blocks.get(level.dimension());
        Block block = here == null ? null : here.get(pos.asLong());
        if (block == null) {
            return false;
        }
        if (!state.is(block)) {
            here.remove(pos.asLong());
            data.setDirty();
            return false;
        }
        return true;
    }

    private static boolean anyIn(ServerLevel level) {
        Map<Long, Block> here = get(level).blocks.get(level.dimension());
        return here != null && !here.isEmpty();
    }

    // --- события ------------------------------------------------------------

    /** Выпадение при поломке — рукой, инструментом, водой, поршнем. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDrops(BlockDropsEvent event) {
        ServerLevel level = event.getLevel();
        if (anyIn(level) && isSpellBlock(level, event.getPos(), event.getState())) {
            event.setCanceled(true);
            forget(level, event.getPos());
        }
    }

    /** Взрыв роняет добычу сам, мимо события выпадения: такие блоки убираем из него заранее. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !anyIn(level)) {
            return;
        }
        Iterator<BlockPos> it = event.getAffectedBlocks().iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (isSpellBlock(level, pos, level.getBlockState(pos))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                forget(level, pos);
                it.remove();
            }
        }
    }

    /** Поршень не двигает блоки заклинаний: сдвинутый ушёл бы из-под учёта. */
    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !anyIn(level)) {
            return;
        }
        PistonStructureResolver structure = event.getStructureHelper();
        if (structure == null || !structure.resolve()) {
            return;
        }
        for (BlockPos pos : structure.getToPush()) {
            if (isSpellBlock(level, pos, level.getBlockState(pos))) {
                event.setCanceled(true);
                return;
            }
        }
    }

    /** На место блока заклинания ставят другой — запись больше не о нём. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        LevelAccessor accessor = event.getLevel();
        if (!event.isCanceled() && accessor instanceof ServerLevel level && anyIn(level)) {
            forget(level, event.getPos());
        }
    }

    // --- диск -----------------------------------------------------------------

    private static SpellBlocks load(CompoundTag tag, HolderLookup.Provider registries) {
        SpellBlocks data = new SpellBlocks();
        for (String dimension : tag.getAllKeys()) {
            ResourceLocation id = ResourceLocation.tryParse(dimension);
            if (id == null) {
                continue;
            }
            Map<Long, Block> here = new HashMap<>();
            ListTag list = tag.getList(dimension, Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                ResourceLocation block = ResourceLocation.tryParse(entry.getString("Block"));
                if (block != null && BuiltInRegistries.BLOCK.containsKey(block)) {
                    here.put(entry.getLong("Pos"), BuiltInRegistries.BLOCK.get(block));
                }
            }
            data.blocks.put(ResourceKey.create(Registries.DIMENSION, id), here);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        for (Map.Entry<ResourceKey<Level>, Map<Long, Block>> dimension : blocks.entrySet()) {
            ListTag list = new ListTag();
            for (Map.Entry<Long, Block> entry : dimension.getValue().entrySet()) {
                CompoundTag stored = new CompoundTag();
                stored.putLong("Pos", entry.getKey());
                stored.putString("Block", BuiltInRegistries.BLOCK.getKey(entry.getValue()).toString());
                list.add(stored);
            }
            tag.put(dimension.getKey().location().toString(), list);
        }
        return tag;
    }
}
