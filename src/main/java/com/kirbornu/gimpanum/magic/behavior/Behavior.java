package com.kirbornu.gimpanum.magic.behavior;

import com.kirbornu.gimpanum.magic.spell.SpellContext;
import com.kirbornu.gimpanum.magic.spell.SpellEntity;

/**
 * Поведение из библиотеки, уже с параметрами из {@code arcanes.json}.
 *
 * <p>Поведения бывают трёх видов, и вид решает, в какой роли оно уместно:
 * <ul>
 *     <li>{@link Kind#MODIFIER} — меняет контекст: выбирает цели, задаёт форму,
 *     множит параметры. Корень, префикс, суффикс.</li>
 *     <li>{@link Kind#ACTION} — делает что-то с целями или блоками. Корень,
 *     префикс, суффикс.</li>
 *     <li>{@link Kind#PROCESS} — растянуто во времени: ожидание, движение.
 *     Только интерфикс.</li>
 * </ul>
 */
public abstract class Behavior {

    public enum Kind {
        MODIFIER,
        ACTION,
        PROCESS
    }

    public abstract Kind kind();

    /** Для {@link Kind#MODIFIER}: поменять контекст. */
    public void modify(SpellContext context) {
    }

    /**
     * Для {@link Kind#ACTION}: сделать дело.
     *
     * @param root исполняется ли поведение корнем — только тогда на него
     *             действуют усиления и продления
     */
    public void act(SpellContext context, boolean root) {
    }

    /** Для {@link Kind#PROCESS}: начать процесс у сущности-заклинания. */
    public Process start(SpellEntity entity) {
        throw new UnsupportedOperationException(getClass().getSimpleName() + " — не процесс");
    }

    /** Оставляет ли действие длительный след — горение, эффект, временный блок. */
    public boolean lasting() {
        return false;
    }

    /** Процесс интерфикса, исполняемый по тикам. */
    public interface Process {

        /** @return {@code true}, когда процесс закончил своё */
        boolean tick(SpellEntity entity);

        /**
         * Держит ли процесс интерфикс. Неблокирующий (вроде свечения) живёт,
         * пока идут блокирующие, и сам интерфикс не удлиняет.
         */
        default boolean blocking() {
            return true;
        }

        /** Интерфикс кончился или сущность исчезла — прибрать за собой. */
        default void stop(SpellEntity entity) {
        }
    }
}
