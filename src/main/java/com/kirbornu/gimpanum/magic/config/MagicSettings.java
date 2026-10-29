package com.kirbornu.gimpanum.magic.config;

import com.kirbornu.gimpanum.magic.core.TurtleSettings;

/** Общие настройки магии — {@code magic.json}. */
public record MagicSettings(TurtleSettings turtle, Cost cost, double cooldownSeconds, String tabletUpgradeItem,
                            Limits limits, Homing homing) {

    /**
     * Цена заклинания.
     *
     * @param foodPerRune     сколько единиц еды стоит каждая руна
     * @param canKill         может ли цена убить заклинателя
     * @param healthPerFood   сколько здоровья берётся за единицу еды, когда еда кончилась
     * @param crystalItem     id предмета, которым платят хрусталём
     * @param runesPerCrystal сколько рун оплачивает один такой предмет; 0 — хрусталём не платят
     */
    public record Cost(double foodPerRune, boolean canKill, double healthPerFood, String crystalItem,
                       int runesPerCrystal) {

        /** Сколько хрусталя стоит заклинание: начатая четвёрка рун — целый осколок. */
        public int crystals(int runes) {
            return runesPerCrystal <= 0 ? 0 : (runes + runesPerCrystal - 1) / runesPerCrystal;
        }
    }

    /** Пределы, которые держат заклинания в рамках, как бы их ни сложили. */
    public record Limits(int blocksPerSpell, int entitiesPerSpell, double entityLifetimeSeconds, int homingEntities,
                         int particlesPerTick, double defaultTargetRadius) {
    }

    /** Самонаведение: как часто и как далеко искать цель. */
    public record Homing(int searchIntervalTicks, double searchRadius) {
    }
}
