package com.kirbornu.gimpanum.emission;

import com.kirbornu.gimpanum.config.JsonConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ностальгия: вокруг игроков бродят исполинские видения домашних животных.
 *
 * <p>Видение — настоящее ванильное животное, раздутое атрибутом размера до
 * десятка блоков: модель игра рисует сама, свой рендерер не нужен. Разум у него
 * отключён, оно бессмертно и неосязаемо для взаимодействий, а двигает его этот
 * класс — медленно, по воздуху и сквозь блоки, от одной точки возле игрока к
 * другой. Коснуться его — значит получать урон, пока не выйдешь.
 *
 * <p>Видения помечены в данных сущности. Если чанк выгрузится вместе с видением
 * или сервер перезапустится посреди выброса, при следующей загрузке помеченное
 * и никем не ведомое видение не войдёт в мир ({@link Emissions}): иначе
 * исполинская корова так и висела бы в небе навсегда.
 */
final class Nostalgia extends LastingEmission {

    /** Метка видения в данных сущности. */
    static final String MARK = "gimpanum_vision";

    /** Кем может предстать прошлое. */
    private static final List<EntityType<? extends Mob>> ANIMALS = List.of(
            EntityType.COW, EntityType.PIG, EntityType.SHEEP, EntityType.CHICKEN, EntityType.HORSE,
            EntityType.DONKEY, EntityType.LLAMA, EntityType.RABBIT, EntityType.FOX, EntityType.WOLF,
            EntityType.CAT, EntityType.OCELOT, EntityType.PANDA, EntityType.POLAR_BEAR, EntityType.GOAT,
            EntityType.TURTLE, EntityType.FROG, EntityType.AXOLOTL, EntityType.PARROT, EntityType.CAMEL,
            EntityType.SNIFFER, EntityType.ARMADILLO, EntityType.MOOSHROOM, EntityType.BEE);

    /** Больше этого атрибут размера не позволяет. */
    private static final double MAX_SCALE = 16.0;

    /** Не чаще, чем раз в столько тиков, у одного игрока появляется новое видение. */
    private static final int SPAWN_GAP = 40;

    private static final class Vision {
        final UUID owner;
        final int until;
        Vec3 goal;
        int retargetAt;

        Vision(UUID owner, int until, Vec3 goal) {
            this.owner = owner;
            this.until = until;
            this.goal = goal;
        }
    }

    private final Map<UUID, Vision> visions = new HashMap<>();

    /** Сколько видений положено каждому игроку — бросается один раз. */
    private final Map<UUID, Integer> wanted = new HashMap<>();

    /** Тик, раньше которого игроку не показывают нового видения. */
    private final Map<UUID, Integer> cooldown = new HashMap<>();

    boolean tracks(UUID vision) {
        return visions.containsKey(vision);
    }

    @Override
    protected int duration(ServerLevel level) {
        return EmissionConfig.of("nostalgia").integer("duration_seconds") * 20;
    }

    @Override
    protected void pulse(ServerLevel level, List<ServerPlayer> targets) {
        JsonConfig.Section config = EmissionConfig.of("nostalgia");
        RandomSource random = level.random;
        for (ServerPlayer player : targets) {
            UUID id = player.getUUID();
            int want = wanted.computeIfAbsent(id, key -> config.between("visions", random));
            long have = visions.values().stream().filter(vision -> vision.owner.equals(id)).count();
            if (have < want && elapsed >= cooldown.getOrDefault(id, 0)) {
                cooldown.put(id, elapsed + SPAWN_GAP);
                summon(level, player, config, random);
            }
        }

        boolean strike = elapsed % Math.max(1, config.integer("damage_interval_ticks")) == 0;
        double step = config.number("speed_blocks_per_second") / 20.0;
        Iterator<Map.Entry<UUID, Vision>> it = visions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Vision> entry = it.next();
            Entity body = level.getEntity(entry.getKey());
            Vision vision = entry.getValue();
            if (!(body instanceof Mob mob)) {
                it.remove();
                continue;
            }
            if (elapsed >= vision.until) {
                vanish(level, mob);
                it.remove();
                continue;
            }
            drift(level, mob, vision, step, random);
            if (strike) {
                touch(level, mob, config);
            }
        }
    }

    @Override
    public void stop(ServerLevel level) {
        for (UUID id : visions.keySet()) {
            if (level.getEntity(id) instanceof Mob mob) {
                vanish(level, mob);
            }
        }
        visions.clear();
    }

    private void summon(ServerLevel level, ServerPlayer player, JsonConfig.Section config, RandomSource random) {
        EntityType<? extends Mob> type = ANIMALS.get(random.nextInt(ANIMALS.size()));
        Mob mob = type.create(level);
        if (mob == null) {
            return;
        }
        double angle = random.nextDouble() * Math.PI * 2.0;
        int distance = config.between("distance_blocks", random);
        double y = Mth.clamp(player.getY() + 2.0 + random.nextDouble() * 6.0,
                level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        mob.moveTo(player.getX() + Math.cos(angle) * distance, y, player.getZ() + Math.sin(angle) * distance,
                random.nextFloat() * 360.0F, 0.0F);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()),
                MobSpawnType.EVENT, null);
        mob.setNoAi(true);
        mob.setNoGravity(true);
        mob.setInvulnerable(true);
        mob.setSilent(true);
        AttributeInstance scale = mob.getAttribute(Attributes.SCALE);
        if (scale != null) {
            double size = Math.max(type.getWidth(), type.getHeight());
            scale.setBaseValue(Math.min(MAX_SCALE, config.number("size_blocks") / size));
            mob.refreshDimensions();
        }
        mob.getPersistentData().putBoolean(MARK, true);

        int life = config.between("life_seconds", random) * 20;
        // Сначала в список, потом в мир: при входе в мир метку проверяют, и
        // неведомое видение туда не пустили бы.
        visions.put(mob.getUUID(), new Vision(player.getUUID(), elapsed + life, around(player.position(), random)));
        if (level.addFreshEntity(mob)) {
            puff(level, mob);
        } else {
            visions.remove(mob.getUUID());
        }
    }

    /**
     * Плыть к своей точке, изредка выбирая новую — возле хозяина, если он тут.
     *
     * <p>Разум у видения отключён, а с ним и всякое движение: игра не двигает
     * такую сущность вовсе. Поэтому ставим её сами, шаг за шагом; клиент
     * сглаживает эти шаги так же, как обычную ходьбу, и даже перебирает ногами.
     */
    private void drift(ServerLevel level, Mob mob, Vision vision, double step, RandomSource random) {
        Vec3 position = mob.position();
        Vec3 way = vision.goal.subtract(position);
        if (way.length() < 1.0 || elapsed >= vision.retargetAt) {
            Entity owner = level.getEntity(vision.owner);
            vision.goal = around(owner != null ? owner.position() : position, random);
            vision.retargetAt = elapsed + 100 + random.nextInt(100);
            way = vision.goal.subtract(position);
        }
        Vec3 move = way.normalize().scale(Math.min(step, way.length()));
        double y = Mth.clamp(position.y + move.y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        mob.setPos(position.x + move.x, y, position.z + move.z);
        float yaw = (float) (Mth.atan2(move.z, move.x) * Mth.RAD_TO_DEG) - 90.0F;
        mob.setYRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
    }

    /** Точка поблизости от центра: в стороне и чуть выше, чтобы видение проходило рядом. */
    private static Vec3 around(Vec3 centre, RandomSource random) {
        return centre.add((random.nextDouble() - 0.5) * 20.0, random.nextDouble() * 10.0 - 2.0,
                (random.nextDouble() - 0.5) * 20.0);
    }

    /** Урон всем, кто внутри видения, — мимо брони. */
    private static void touch(ServerLevel level, Mob mob, JsonConfig.Section config) {
        for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, mob.getBoundingBox(),
                player -> !player.isCreative() && !player.isSpectator())) {
            player.hurt(level.damageSources().magic(), (float) config.number("damage"));
        }
    }

    private static void vanish(ServerLevel level, Mob mob) {
        puff(level, mob);
        mob.discard();
    }

    /** Облако, в котором видение возникает и тает — размером с него самого. */
    private static void puff(ServerLevel level, Mob mob) {
        double spread = mob.getBbWidth() / 2.0;
        level.sendParticles(ParticleTypes.CLOUD, mob.getX(), mob.getY() + mob.getBbHeight() / 2.0, mob.getZ(),
                80, spread, mob.getBbHeight() / 2.0, spread, 0.02);
    }
}
