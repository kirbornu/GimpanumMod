package com.kirbornu.gimpanum.registry;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.BlockEntityTypeAddBlocksEvent;

/**
 * Небула-таблички умеют хранить надпись.
 *
 * <p>Своя блок-сущность им не нужна и была бы лишней: текст, цвет и свечение
 * умеет ванильная. Нужно лишь разрешить ей стоять в наших блоках — список
 * допустимых блоков у типа блок-сущности закрыт, и правится он ровно этим
 * событием.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class GimpanumSigns {

    private GimpanumSigns() {
    }

    @SubscribeEvent
    public static void addBlocks(BlockEntityTypeAddBlocksEvent event) {
        event.modify(BlockEntityType.SIGN,
                GimpanumContent.NEBULA_SIGN.get(),
                GimpanumContent.NEBULA_WALL_SIGN.get());
    }
}
