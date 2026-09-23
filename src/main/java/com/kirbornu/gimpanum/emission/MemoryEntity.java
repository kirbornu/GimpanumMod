package com.kirbornu.gimpanum.emission;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Воспоминание — невидимка, которая пару секунд искрит, а потом взрывается.
 *
 * <p>Взрыв не ванильный: тот ломает блоки и бьёт всех подряд, а этот трогает
 * только игроков. Урон падает от центра к краю, стены прикрывают так же, как
 * от обычного взрыва ({@link Explosion#getSeenPercent}), а тип урона —
 * взрывной, поэтому работает защита от взрывов.
 *
 * <p>В мире не сохраняется: воспоминание живёт две секунды и принадлежит
 * выбросу, а не чанку.
 */
public class MemoryEntity extends Entity {

    private int fuse = 40;
    private float radius = 7.0F;
    private float damage = 20.0F;

    public MemoryEntity(EntityType<? extends MemoryEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    void arm(int fuse, float radius, float damage) {
        this.fuse = fuse;
        this.radius = radius;
        this.damage = damage;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.tickCount == 1) {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.WARDEN_SONIC_CHARGE,
                    SoundSource.HOSTILE, 2.0F, 1.2F);
        }
        if (this.tickCount % 2 == 0) {
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY(), this.getZ(),
                    8, 0.4, 0.4, 0.4, 0.2);
            level.sendParticles(ParticleTypes.END_ROD, this.getX(), this.getY(), this.getZ(),
                    2, 0.2, 0.2, 0.2, 0.02);
        }
        if (--fuse <= 0) {
            detonate(level);
            this.discard();
        }
    }

    private void detonate(ServerLevel level) {
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, this.getX(), this.getY(), this.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 4.0F, 0.8F);
        Vec3 centre = this.position();
        for (ServerPlayer player : level.players()) {
            if (player.isCreative() || player.isSpectator()) {
                continue;
            }
            double distance = player.position().distanceTo(centre);
            if (distance >= radius) {
                continue;
            }
            double strength = (1.0 - distance / radius) * Explosion.getSeenPercent(centre, player);
            if (strength <= 0.0) {
                continue;
            }
            player.hurt(this.damageSources().explosion(this, null), (float) (damage * strength));
            Vec3 away = player.position().subtract(centre);
            if (away.lengthSqr() > 1.0E-4) {
                player.push(away.normalize().scale(strength * 1.5));
                player.hurtMarked = true;
            }
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }
}
