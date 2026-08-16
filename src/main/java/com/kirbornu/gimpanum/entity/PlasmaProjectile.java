package com.kirbornu.gimpanum.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Разряд Плазменной молнии.
 *
 * <p>Наследуется от огненного шара ради готовой физики и отрисовки, всё
 * остальное своё: урон берётся у стрелка, а в точке попадания бьёт настоящая
 * молния. Её урон приходит отдельно от урона самого попадания — попасть под
 * разряд плохо дважды.
 */
public class PlasmaProjectile extends Fireball {

    /** Урон от самой молнии — столько же, сколько у настоящей. */
    private static final float LIGHTNING_DAMAGE = 10.0F;

    public PlasmaProjectile(EntityType<? extends PlasmaProjectile> type, Level level) {
        super(type, level);
        this.setItem(new ItemStack(Items.PRISMARINE_CRYSTALS));
    }

    public PlasmaProjectile(Level level, LivingEntity shooter, Vec3 direction) {
        super(GimpanumEntities.PLASMA_PROJECTILE.get(), shooter, direction, level);
        this.setItem(new ItemStack(Items.PRISMARINE_CRYSTALS));
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        if (this.level().isClientSide) {
            return;
        }
        Entity victim = result.getEntity();
        Entity owner = this.getOwner();
        float damage = owner instanceof LivingEntity shooter
                ? (float) shooter.getAttributeValue(Attributes.ATTACK_DAMAGE)
                : 5.0F;
        victim.hurt(this.damageSources().fireball(this, owner), damage);
        strike(victim.position());
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (!this.level().isClientSide) {
            strike(Vec3.atBottomCenterOf(result.getBlockPos().above()));
        }
    }

    /**
     * Молния в точке попадания.
     *
     * <p>Молния зажигается «показной»: настоящая подожгла бы всё вокруг, а в
     * Гимпануме гореть нечему по замыслу. Поэтому урон от разряда наносим
     * сами — он приходит отдельно от урона самого попадания, как и должен.
     */
    private void strike(Vec3 where) {
        if (!(this.level() instanceof ServerLevel server)) {
            this.discard();
            return;
        }
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
        if (bolt != null) {
            bolt.moveTo(Vec3.atBottomCenterOf(BlockPos.containing(where)));
            bolt.setVisualOnly(true);
            server.addFreshEntity(bolt);
        }
        for (Entity victim : server.getEntities(this, new AABB(where, where).inflate(2.5))) {
            if (victim instanceof LivingEntity) {
                victim.hurt(this.damageSources().lightningBolt(), LIGHTNING_DAMAGE);
            }
        }
        this.discard();
    }
}
