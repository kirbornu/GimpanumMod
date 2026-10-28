package com.kirbornu.gimpanum.magic.spell;

import com.kirbornu.gimpanum.magic.Arcane;
import com.kirbornu.gimpanum.magic.behavior.Behavior;
import com.kirbornu.gimpanum.magic.core.Program;
import com.kirbornu.gimpanum.magic.core.Role;
import com.kirbornu.gimpanum.magic.particle.SpellParticles;
import com.kirbornu.gimpanum.magic.MagicContent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Невидимая сущность-заклинание: исполняет программу по тикам.
 *
 * <p>Это конечный автомат из трёх состояний: исполнить очередной корень,
 * исполнять интерфикс, ждать фоновые повторы. Корни без интерфикса между ними
 * исполняются в один тик подряд; интерфикс тянется, пока не закончат все его
 * блокирующие процессы.
 *
 * <p>Сущность — и главная, и копия. Копия (её порождает огненный суффикс) не
 * имеет программы, только повтор одного корня. Фоновые повторы — это и
 * периодический корень, и работа копии: сущность исчезает, когда программа
 * исполнена и повторов не осталось.
 *
 * <p>На диск не пишется. Заклинание после перезапуска не продолжается, а всё,
 * что оно оставило в мире надолго, — временные блоки — записано отдельно в
 * {@link TempBlocks}. Жёсткий предел жизни — из настройки, на случай если
 * какой-то процесс не закончится сам.
 */
public class SpellEntity extends Entity {

    private enum Phase {
        ROOT,
        INTERFIX,
        WINDING_DOWN
    }

    private SpellSession session;
    private Program<Arcane> program;
    private int step;
    private Phase phase = Phase.ROOT;

    private Arcane interfixArcane;
    private final List<Behavior.Process> processes = new ArrayList<>();
    private final List<Repeater> repeaters = new ArrayList<>();

    private int lifetimeTicks;

    /** Повтор действия раз в {@code interval} тиков ещё {@code remaining} раз. */
    private static final class Repeater {
        private final int interval;
        private int remaining;
        private int countdown;
        private final Runnable action;

        private Repeater(int interval, int remaining, Runnable action) {
            this.interval = interval;
            this.remaining = remaining;
            this.countdown = interval;
            this.action = action;
        }
    }

    public SpellEntity(EntityType<? extends SpellEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvisible(true);
    }

    /**
     * Порождает главную сущность заклинания.
     *
     * @return сущность или {@code null}, если её не удалось добавить в мир
     */
    public static SpellEntity cast(SpellSession session, Vec3 at, Program<Arcane> program) {
        SpellEntity entity = new SpellEntity(MagicContent.SPELL.get(), session.level());
        entity.session = session;
        entity.program = program;
        entity.lifetimeTicks = lifetime(session);
        entity.setPos(at);
        if (!session.takeEntity() || !session.level().addFreshEntity(entity)) {
            return null;
        }
        return entity;
    }

    /**
     * Копия на месте сущности: только корень, раз в {@code interval} тиков, {@code times} раз.
     */
    public void spawnEcho(Arcane root, int interval, int times) {
        if (times <= 0 || !session.takeEntity()) {
            return;
        }
        SpellEntity echo = new SpellEntity(MagicContent.SPELL.get(), level());
        echo.session = session;
        echo.program = new Program<>(List.of());
        echo.lifetimeTicks = lifetime(session);
        echo.setPos(position());
        if (level().addFreshEntity(echo)) {
            SpellExecutor.applyRootOnly(echo, root);
            echo.addRepeater(interval, times - 1, () -> SpellExecutor.applyRootOnly(echo, root));
        }
    }

    private static int lifetime(SpellSession session) {
        return (int) Math.round(session.config().settings().limits().entityLifetimeSeconds() * 20);
    }

    public SpellSession session() {
        return session;
    }

    public void addRepeater(int interval, int times, Runnable action) {
        if (times > 0) {
            repeaters.add(new Repeater(Math.max(1, interval), times, action));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (!(level() instanceof ServerLevel)) {
            return;
        }
        if (session == null || tickCount > lifetimeTicks) {
            // Без сессии сущность взялась не из каста (например, призвана
            // командой) и исполнять ей нечего.
            discard();
            return;
        }
        tickRepeaters();
        if (phase == Phase.INTERFIX) {
            tickInterfix();
        }
        while (phase == Phase.ROOT) {
            if (step >= program.steps().size()) {
                phase = Phase.WINDING_DOWN;
                break;
            }
            Program.Step<Arcane> current = program.steps().get(step);
            SpellExecutor.apply(this, current);
            if (current.interfixAfter().isPresent()) {
                startInterfix(current.interfixAfter().get());
            } else {
                step++;
            }
        }
        if (phase == Phase.WINDING_DOWN && repeaters.isEmpty()) {
            discard();
        }
    }

    private void tickRepeaters() {
        // По копии: действие повтора может завести новый повтор.
        for (Repeater repeater : List.copyOf(repeaters)) {
            if (--repeater.countdown <= 0) {
                repeater.action.run();
                repeater.countdown = repeater.interval;
                repeater.remaining--;
            }
        }
        repeaters.removeIf(r -> r.remaining <= 0);
    }

    private void startInterfix(Arcane arcane) {
        interfixArcane = arcane;
        for (Behavior behavior : arcane.behaviors(Role.INTERFIX)) {
            processes.add(behavior.start(this));
        }
        phase = Phase.INTERFIX;
        // Интерфикс без единого блокирующего процесса ничего не ждёт.
        if (processes.stream().noneMatch(Behavior.Process::blocking)) {
            finishInterfix();
        }
    }

    private void tickInterfix() {
        boolean waiting = false;
        for (Behavior.Process process : processes) {
            boolean done = process.tick(this);
            if (process.blocking() && !done) {
                waiting = true;
            }
        }
        SpellParticles.burst((ServerLevel) level(), interfixArcane.particles(Role.INTERFIX), position());
        if (!waiting) {
            finishInterfix();
        }
    }

    private void finishInterfix() {
        stopProcesses();
        interfixArcane = null;
        step++;
        phase = Phase.ROOT;
    }

    private void stopProcesses() {
        for (Behavior.Process process : processes) {
            process.stop(this);
        }
        processes.clear();
    }

    @Override
    public void remove(RemovalReason reason) {
        // Процессы держат временные блоки (свет за сущностью) — их надо
        // убрать при любом исчезновении, в том числе при выгрузке чанка.
        if (!level().isClientSide) {
            stopProcesses();
        }
        super.remove(reason);
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

    @Override
    public boolean isPushable() {
        return false;
    }
}
