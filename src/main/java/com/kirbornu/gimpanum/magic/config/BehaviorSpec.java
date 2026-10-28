package com.kirbornu.gimpanum.magic.config;

/**
 * Поведение, как оно записано в {@code arcanes.json}: имя и параметры.
 *
 * <p>Что параметры значат, ядро не знает: их читает библиотека поведений в
 * игре. Здесь только место в файле, чтобы ошибка указала на нужную строку.
 *
 * @param params весь объект поведения, вместе с полем {@code type}
 */
public record BehaviorSpec(String type, Json params) {
}
