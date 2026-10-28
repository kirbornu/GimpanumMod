package com.kirbornu.gimpanum.magic.config;

import com.kirbornu.gimpanum.magic.core.FieldSet;

import java.util.Map;

/**
 * Наборы полей и привязка катализаторов к ним — {@code fields.json}.
 *
 * @param catalysts id предмета → имя набора
 */
public record FieldsConfig(Map<String, FieldSet> sets, Map<String, String> catalysts) {

    public static final String DEFAULT = "default";

    /**
     * Набор для предмета во второй руке.
     *
     * <p>Без катализатора, с непривязанным предметом и с привязанным к пустому
     * набору — набор {@code default}. Последнее — допущение: пустой набор не
     * может дать аркан, а рунописи без аркана не бывает, поэтому честнее
     * читать руны как без катализатора, чем ломать заклинание.
     *
     * @param itemId id предмета или {@code null}, если рука пуста
     */
    public FieldSet forCatalyst(String itemId) {
        String name = itemId == null ? null : catalysts.get(itemId);
        FieldSet set = name == null ? null : sets.get(name);
        return set == null || set.isEmpty() ? sets.get(DEFAULT) : set;
    }
}
