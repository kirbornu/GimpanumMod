package com.kirbornu.gimpanum.magic.spell;

import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.particle.ParticleLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Контекст применения одного корня: откуда, на кого, с какими множителями.
 *
 * <h2>Как префиксы комбинируются</h2>
 *
 * Выбор целей — цепочка. Контекст держит набор {@linkplain Anchor якорей}; в
 * начале это одна точка — сама сущность-заклинание. Каждый префикс выбора
 * превращает <i>каждый</i> якорь в новые и объединяет результат:
 * «цель под взглядом» даёт ту сущность, на которую смотрят от якоря, «все в
 * радиусе 2» — всех вокруг якоря. Поэтому «взгляд, затем радиус» — это все
 * вокруг того, на кого смотришь, а «радиус, затем взгляд» — те, на кого
 * смотрят стоящие рядом.
 *
 * <p>Если шаг не нашёл ничего, он откатывается: набор остаётся прежним, как
 * будто этого префикса не было. Так «исцеление на того, кто под прицелом»
 * без цели под прицелом лечит цель по умолчанию, а не никого.
 *
 * <p>Форма (конус, сфера) — тоже шаг цепочки, но её результат — область: блоки
 * внутри неё и живые сущности внутри неё. Область пустой не бывает, поэтому
 * форма не откатывается, даже если живых в ней нет: огненный конус в пустоту
 * не должен вместо этого поджечь самого заклинателя.
 *
 * <p>Если ни один префикс не выбрал цели, корень бьёт цель по умолчанию —
 * ближайшую к сущности-заклинанию живую сущность. Обычно это сам кастер.
 */
public final class SpellContext {

    /** Сколько раз подряд можно повторить корень, как бы ни множились повторы. */
    public static final int MAX_REPEAT = 100;

    private final SpellSession session;
    private final SpellEntity entity;
    private final Arcane root;
    private final Vec3 origin;

    private List<Anchor> anchors;
    /** Выбирал ли кто-то цели — иначе действует цель по умолчанию. */
    private boolean targetsChosen;
    private Set<BlockPos> blocks;

    private double primaryMultiplier = 1.0;
    private double durationMultiplier = 1.0;
    private double periodicInterval;
    private double periodicDuration;
    private int repeat = 1;
    private double delay;

    /** Чьё поведение сейчас исполняется — от этого зависят частицы. */
    private Arcane arcane;
    private Role role;

    private List<LivingEntity> resolvedTargets;

    public SpellContext(SpellSession session, SpellEntity entity, Arcane root) {
        this.session = session;
        this.entity = entity;
        this.root = root;
        this.origin = entity.position();
        this.anchors = List.of(new Anchor(origin, null));
    }

    public SpellSession session() {
        return session;
    }

    public SpellEntity entity() {
        return entity;
    }

    /** Корень, ради которого собран контекст. */
    public Arcane root() {
        return root;
    }

    public ServerLevel level() {
        return session.level();
    }

    public Vec3 origin() {
        return origin;
    }

    public Vec3 direction() {
        return session.direction();
    }

    public List<Anchor> anchors() {
        return anchors;
    }

    /** Следующее поведение исполняет этот аркан в этой роли. */
    public void enter(Arcane arcane, Role role) {
        this.arcane = arcane;
        this.role = role;
    }

    public Arcane arcane() {
        return arcane;
    }

    public Role role() {
        return role;
    }

    /** Частицы текущего аркана в текущей роли. */
    public ParticleLayer particles() {
        return arcane == null ? null : arcane.particles(role);
    }

    // --- выбор -------------------------------------------------------------

    /**
     * Шаг выбора целей: каждый якорь → новые якоря. Пустой итог откатывается.
     *
     * @return сработал ли шаг
     */
    public boolean chain(Function<Anchor, List<Anchor>> step) {
        Map<Object, Anchor> next = new LinkedHashMap<>();
        for (Anchor anchor : anchors) {
            for (Anchor found : step.apply(anchor)) {
                // Одна сущность, найденная от двух якорей, — одна цель, а не две.
                next.putIfAbsent(found.entity() != null ? found.entity().getUUID() : found.point(), found);
            }
        }
        if (next.isEmpty()) {
            return false;
        }
        anchors = List.copyOf(next.values());
        targetsChosen = true;
        resolvedTargets = null;
        return true;
    }

    /**
     * Шаг формы: каждый якорь → область. Блоки областей объединяются, якорями
     * становятся живые внутри них.
     */
    public void shape(Function<Anchor, Region> region) {
        Set<BlockPos> found = new LinkedHashSet<>();
        Map<Object, Anchor> inside = new LinkedHashMap<>();
        for (Anchor anchor : anchors) {
            Region r = region.apply(anchor);
            found.addAll(r.blocks());
            for (LivingEntity living : level().getEntitiesOfClass(LivingEntity.class, r.bounds(),
                    e -> e.isAlive() && !e.isSpectator() && r.contains(e.getBoundingBox().getCenter()))) {
                inside.putIfAbsent(living.getUUID(), Anchor.of(living));
            }
        }
        blocks = found;
        anchors = List.copyOf(inside.values());
        targetsChosen = true;
        resolvedTargets = null;
    }

    /**
     * Строгий выбор: ровно эти цели, даже если их нет. Так работают фильтры —
     * «только чудовища» без чудовищ рядом не должны откатиться и ударить кастера.
     */
    public void selectStrictly(List<LivingEntity> targets) {
        anchors = targets.stream().map(Anchor::of).toList();
        targetsChosen = true;
        resolvedTargets = null;
    }

    /** Выделенные формой блоки или {@code null}, если формы не было. */
    public Set<BlockPos> blocks() {
        return blocks;
    }

    /** Заменить выделение блоков — например, опустив его на землю. Цели не меняются. */
    public void replaceBlocks(Set<BlockPos> blocks) {
        this.blocks = blocks;
    }

    /**
     * Живые цели корня: выбранные префиксами или цель по умолчанию.
     *
     * <p>Список запоминается: периодический корень бьёт тех же, кого выбрал в
     * первый раз, и не перескакивает на случайно подошедших.
     */
    public List<LivingEntity> targets() {
        if (resolvedTargets == null) {
            resolvedTargets = targetsChosen
                    ? anchors.stream().map(Anchor::entity).filter(e -> e != null).toList()
                    : defaultTarget();
        }
        return resolvedTargets.stream().filter(LivingEntity::isAlive).toList();
    }

    private List<LivingEntity> defaultTarget() {
        double radius = session.config().settings().limits().defaultTargetRadius();
        Vec3 at = entity.position();
        return level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                        e -> e.isAlive() && !e.isSpectator() && e.getBoundingBox().getCenter().distanceTo(at) <= radius)
                .stream()
                .min(Comparator.comparingDouble(e -> e.getBoundingBox().getCenter().distanceToSqr(at)))
                .map(List::of)
                .orElse(List.of());
    }

    /**
     * Блоки для корня, работающего с блоками: выделенные формой, а без формы —
     * блок в позиции каждой цели; без целей — блок самой сущности-заклинания.
     */
    public List<BlockPos> blockTargets() {
        if (blocks != null) {
            return new ArrayList<>(blocks);
        }
        List<BlockPos> out = new ArrayList<>();
        for (LivingEntity target : targets()) {
            out.add(target.blockPosition());
        }
        if (out.isEmpty()) {
            out.add(entity.blockPosition());
        }
        return out;
    }

    // --- множители ------------------------------------------------------

    public void multiplyPrimary(double factor) {
        primaryMultiplier *= factor;
    }

    public void multiplyDuration(double factor) {
        durationMultiplier *= factor;
    }

    /** Основной параметр с учётом усилений — только для поведений корня. */
    public double primary(double value, boolean root) {
        return root ? value * primaryMultiplier : value;
    }

    /** Длительность с учётом продлений — только для поведений корня. */
    public double duration(double seconds, boolean root) {
        return root ? seconds * durationMultiplier : seconds;
    }

    /** Применять действия корня {@code times} раз подряд; повторы множатся. */
    public void multiplyRepeat(int times) {
        repeat = (int) Math.min(MAX_REPEAT, (long) repeat * times);
    }

    public int repeat() {
        return repeat;
    }

    /** Отложить применение на {@code seconds}; задержки складываются. */
    public void addDelay(double seconds) {
        delay += seconds;
    }

    public double delay() {
        return delay;
    }

    public void makePeriodic(double intervalSeconds, double durationSeconds) {
        periodicInterval = intervalSeconds;
        periodicDuration = durationSeconds;
    }

    public boolean periodic() {
        return periodicInterval > 0 && periodicDuration > 0;
    }

    public double periodicInterval() {
        return periodicInterval;
    }

    public double periodicDuration() {
        return periodicDuration;
    }
}
