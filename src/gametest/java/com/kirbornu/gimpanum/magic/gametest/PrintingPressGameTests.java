package com.kirbornu.gimpanum.magic.gametest;

import com.kirbornu.gimpanum.Gimpanum;
import com.kirbornu.gimpanum.magic.Arcane;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.ARENA;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.caster;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests.root;
import static com.kirbornu.gimpanum.magic.gametest.ActionGameTests2.sphere;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.arcane;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.castArcanes;
import static com.kirbornu.gimpanum.magic.gametest.MagicTestKit.log;

/**
 * Защита от печатного станка: блоки, созданные заклинанием, ничего не роняют
 * и не двигаются поршнем. Обычные блоки рядом ведут себя как всегда — это
 * контроль, что тест проверяет защиту, а не что-то другое.
 */
@GameTestHolder(Gimpanum.MOD_ID)
@PrefixGameTestTemplate(false)
public class PrintingPressGameTests {

    /** Префикс «один блок там, где сущность-заклинание» — это (7, 3, 7). */
    static final Arcane HERE = arcane("here", 10,
            "{\"prefix\": [{\"type\": \"shape_box\", \"x\": 1, \"y\": 1, \"z\": 1}]}");

    static Arcane place(String block) {
        return root("{\"type\": \"place_block\", \"block\": \"" + block + "\", \"seconds\": -1, \"onlyAir\": true}");
    }

    static long count(GameTestHelper h, Item item) {
        return h.getEntities(EntityType.ITEM).stream().map(ItemEntity::getItem).filter(s -> s.is(item))
                .mapToLong(ItemStack::getCount).sum();
    }

    @GameTest(template = ARENA)
    public static void spellBlockDropsNothingWhenMined(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.setBlock(new BlockPos(9, 2, 9), Blocks.DIAMOND_BLOCK);
        h.assertTrue(castArcanes(p, HERE, place("minecraft:diamond_block")) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertBlockPresent(Blocks.DIAMOND_BLOCK, new BlockPos(7, 3, 7));
            h.getLevel().destroyBlock(h.absolutePos(new BlockPos(7, 3, 7)), true, p);
            h.getLevel().destroyBlock(h.absolutePos(new BlockPos(9, 2, 9)), true, p);
        });
        h.runAfterDelay(4, () -> {
            log("press: diamond blocks dropped " + count(h, Items.DIAMOND_BLOCK));
            h.assertTrue(count(h, Items.DIAMOND_BLOCK) == 1, "выпало " + count(h, Items.DIAMOND_BLOCK)
                    + " (ждали 1 — только с обычного блока)");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void spellIceDropsNothingWithSilkTouch(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.setBlock(new BlockPos(9, 2, 7), Blocks.WATER);
        h.setBlock(new BlockPos(9, 2, 9), Blocks.ICE);
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        pick.enchant(h.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolderOrThrow(Enchantments.SILK_TOUCH), 1);
        h.runAfterDelay(1, () -> h.assertTrue(castArcanes(p, sphere(3),
                root("{\"type\": \"freeze_water\", \"seconds\": -1}")) != null, "каст"));
        h.runAfterDelay(4, () -> {
            h.assertBlockPresent(Blocks.ICE, new BlockPos(9, 2, 7));
            for (BlockPos rel : new BlockPos[]{new BlockPos(9, 2, 7), new BlockPos(9, 2, 9)}) {
                BlockPos abs = h.absolutePos(rel);
                BlockState state = h.getLevel().getBlockState(abs);
                Block.dropResources(state, h.getLevel(), abs, null, p, pick);
                h.getLevel().setBlock(abs, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        });
        h.runAfterDelay(6, () -> {
            h.assertTrue(count(h, Items.ICE) == 1, "льда выпало " + count(h, Items.ICE) + " (ждали 1)");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void spellBlockVanishesInExplosion(GameTestHelper h) {
        ServerPlayer p = caster(h);
        GameRules.BooleanValue decay = h.getLevel().getGameRules().getRule(GameRules.RULE_BLOCK_EXPLOSION_DROP_DECAY);
        boolean old = decay.get();
        h.setBlock(new BlockPos(9, 3, 7), Blocks.DIRT);
        h.assertTrue(castArcanes(p, HERE, place("minecraft:dirt")) != null, "каст");
        h.runAfterDelay(2, () -> {
            decay.set(false, h.getLevel().getServer());
            Vec3 at = Vec3.atCenterOf(h.absolutePos(new BlockPos(8, 3, 7)));
            h.getLevel().explode(null, at.x, at.y, at.z, 1.5F, Level.ExplosionInteraction.BLOCK);
            decay.set(old, h.getLevel().getServer());
        });
        h.runAfterDelay(4, () -> {
            h.assertBlockNotPresent(Blocks.DIRT, new BlockPos(7, 3, 7));
            h.assertBlockNotPresent(Blocks.DIRT, new BlockPos(9, 3, 7));
            log("press: explosion dirt drops " + count(h, Items.DIRT));
            h.assertTrue(count(h, Items.DIRT) == 1, "земли выпало " + count(h, Items.DIRT) + " (ждали 1)");
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void pistonDoesNotMoveSpellBlock(GameTestHelper h) {
        ServerPlayer p = caster(h);
        BlockState piston = Blocks.PISTON.defaultBlockState().setValue(PistonBaseBlock.FACING, Direction.EAST);
        h.setBlock(new BlockPos(6, 3, 7), piston);
        h.setBlock(new BlockPos(6, 3, 9), piston);
        h.setBlock(new BlockPos(7, 3, 9), Blocks.GLASS);
        h.assertTrue(castArcanes(p, HERE, place("minecraft:glass")) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.setBlock(new BlockPos(5, 3, 7), Blocks.REDSTONE_BLOCK);
            h.setBlock(new BlockPos(5, 3, 9), Blocks.REDSTONE_BLOCK);
        });
        h.runAfterDelay(8, () -> {
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(7, 3, 7));
            h.assertBlockPresent(Blocks.GLASS, new BlockPos(8, 3, 9));
            h.succeed();
        });
    }

    @GameTest(template = ARENA)
    public static void waterloggedOwnBlockStillDropsItself(GameTestHelper h) {
        ServerPlayer p = caster(h);
        h.setBlock(new BlockPos(8, 2, 7), Blocks.OAK_SLAB);
        h.assertTrue(castArcanes(p, sphere(2.5), root("{\"type\": \"water\", \"seconds\": -1}")) != null, "каст");
        h.runAfterDelay(2, () -> {
            h.assertTrue(h.getBlockState(new BlockPos(8, 2, 7)).getValue(SlabBlock.WATERLOGGED), "не обводнило");
            h.getLevel().destroyBlock(h.absolutePos(new BlockPos(8, 2, 7)), true, p);
        });
        h.runAfterDelay(4, () -> {
            h.assertTrue(count(h, Items.OAK_SLAB) == 1, "своя плита не выпала");
            h.succeed();
        });
    }
}
