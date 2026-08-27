package com.kirbornu.gimpanum.entity.goal;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Ближний бой со своим темпом — и без поиска пути через полкарты.
 *
 * <p>Ванильная цель ближнего боя раз в десяток тиков строит полный путь до
 * жертвы. Для одиночного зомби это ничего не стоит, а здесь стоит очень
 * дорого, и по двум причинам сразу.
 *
 * <p><b>Первая — чутьё у некрофага дальнее и сквозь стены.</b> Ходока берёт в
 * погоню всё, что оказалось в его {@code FOLLOW_RANGE} — а это 96 блоков.
 * Значит гонятся не единицы, а все ходоки округи разом, и каждый строит свой
 * путь.
 *
 * <p><b>Вторая — бюджет поиска растёт вместе с чутьём.</b> Поиск пути берёт
 * предел обхода как {@code FOLLOW_RANGE * 16}: у ванильного зомби с его 35
 * блоками это 560 узлов, у нашего — 1536. И весь этот предел выедается
 * целиком как раз тогда, когда пути нет: жертва за стеной или на другом ярусе
 * лабиринта — обычное дело. На каждый узел приходится осмотр окрестности 3×3×3,
 * то есть под три десятка обращений к блокам.
 *
 * <p>Отказываться от дальнего чутья нельзя — на нём держится весь замысел
 * ходока: он выходит на игрока издалека и отовсюду. Поэтому <b>чутьё и поиск
 * пути разведены</b>. Замечает ходок по-прежнему за 96 блоков, но пока жертва
 * дальше {@link #PATHFIND_RANGE}, он просто идёт на неё напрямик, без всякого
 * поиска: по открытым барханам это и выглядит, и работает одинаково. Настоящий
 * путь строится только вблизи, где надо обходить углы, — и там его строят
 * единицы, а не вся округа.
 *
 * <p>На случай, если напрямик не выходит — уткнулся в стену, стоит в яме, —
 * есть {@link #STUCK_TICKS}: не продвинулся за это время, получает один
 * настоящий поиск пути. Так расплачиваются за поиск только застрявшие, а не все.
 */
public class PacedMeleeAttackGoal extends Goal {

    /**
     * Ближе этого — настоящий путь, дальше — напрямик.
     *
     * <p>Два с половиной десятка блоков: на таком расстоянии жертва уже за
     * углом или на уступе, и обойти препятствие надо всерьёз. Дальше рельеф
     * барханов всё равно проходим напрямую.
     */
    private static final double PATHFIND_RANGE = 24.0;

    /** Сколько тиков без продвижения терпим, прежде чем всё же построить путь. */
    private static final int STUCK_TICKS = 40;

    /** Что считается продвижением за это время. */
    private static final double PROGRESS = 1.5;

    /**
     * Как часто пересматривается способ движения.
     *
     * <p>С разбросом: иначе сотня ходоков думала бы в один и тот же тик, и
     * ровный расход превратился бы в пилу.
     */
    private static final int THINK = 10;

    private final PathfinderMob mob;
    private final double speed;
    private final int interval;

    private int cooldown;
    private int think;
    private int stuck;
    private boolean pathing;
    private double lastX;
    private double lastY;
    private double lastZ;

    public PacedMeleeAttackGoal(PathfinderMob mob, double speed, int interval) {
        this.mob = mob;
        this.speed = speed;
        this.interval = interval;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity target = mob.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        mob.setAggressive(true);
        think = 0;
        stuck = 0;
        pathing = false;
        remember();
    }

    @Override
    public void stop() {
        mob.setAggressive(false);
        mob.getNavigation().stop();
        cooldown = 0;
        pathing = false;
    }

    @Override
    public void tick() {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            return;
        }
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);

        if (--think <= 0) {
            think = THINK + mob.getRandom().nextInt(THINK);
            choose(target);
        }
        if (!pathing) {
            // Каждый тик, а не раз в десяток: управление движением само
            // сбрасывает ход в ноль, как только отработает поставленную задачу.
            mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
        }

        if (cooldown > 0) {
            cooldown--;
        } else if (mob.isWithinMeleeAttackRange(target) && mob.getSensing().hasLineOfSight(target)) {
            cooldown = interval;
            mob.swing(InteractionHand.MAIN_HAND);
            mob.doHurtTarget(target);
        }
    }

    /** Решить, идти напрямик или всё-таки строить путь. */
    private void choose(LivingEntity target) {
        stuck = mob.distanceToSqr(lastX, lastY, lastZ) < PROGRESS * PROGRESS ? stuck + think : 0;
        remember();

        boolean near = mob.distanceToSqr(target) <= PATHFIND_RANGE * PATHFIND_RANGE;
        pathing = near || stuck >= STUCK_TICKS;
        if (!pathing) {
            mob.getNavigation().stop();
            return;
        }
        stuck = 0;
        if (!mob.getNavigation().moveTo(target, speed)) {
            // Пути нет — значит пойдём без него. Стена на дороге не повод
            // останавливаться: ходок дойдёт до неё и будет ломиться.
            pathing = false;
        }
    }

    private void remember() {
        lastX = mob.getX();
        lastY = mob.getY();
        lastZ = mob.getZ();
    }
}
