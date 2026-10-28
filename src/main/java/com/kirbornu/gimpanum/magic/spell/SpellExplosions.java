package com.kirbornu.gimpanum.magic.spell;

import com.kirbornu.gimpanum.Gimpanum;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Взрывы заклинаний ломают только то, что можно сломать заклинателю.
 *
 * <p>Ванильный взрыв сам по себе ничего не знает ни о приватах, ни о пределе
 * блоков заклинания. Поэтому список блоков, которые он собрался снести,
 * просеивается здесь — тем же {@link BlockEdits#mayBreak}, что и у
 * ломающего поведения, — и каждый оставленный блок занимает место в пределе.
 *
 * <p>Взрывы снарядов, выпущенных заклинанием, блоков не ломают вовсе.
 */
@EventBusSubscriber(modid = Gimpanum.MOD_ID)
public final class SpellExplosions {

    private SpellExplosions() {
    }

    @SubscribeEvent
    public static void onDetonate(ExplosionEvent.Detonate event) {
        net.minecraft.world.entity.Entity source = event.getExplosion().getDirectSourceEntity();
        if (source != null && SpellSpawns.isSpellSpawned(source)) {
            // Снаряд заклинания взрывается, когда заклинания уже нет, и
            // спросить, можно ли кастеру ломать здесь, не у кого.
            event.getAffectedBlocks().clear();
            return;
        }
        if (!(source instanceof SpellEntity spell) || spell.session() == null) {
            return;
        }
        SpellSession session = spell.session();
        event.getAffectedBlocks().removeIf(pos -> !keep(session, pos));
    }

    private static boolean keep(SpellSession session, BlockPos pos) {
        // Воздух взрыв перечисляет тоже, но не ломает: место в пределе ему не нужно.
        if (session.level().getBlockState(pos).isAir()) {
            return true;
        }
        return BlockEdits.mayBreak(session, pos) && session.takeBlock();
    }
}
