package com.kirbornu.gimpanum.magic.config;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.Set;

/**
 * Чтение полей JSON с записью ошибок вместо исключений.
 *
 * <p>Каждый метод либо возвращает значение, либо записывает ошибку с полным
 * путём и возвращает {@code null}/запасное значение, чтобы разбор шёл дальше
 * и нашёл остальные ошибки.
 */
public final class Json {

    private final JsonObject object;
    private final String path;
    private final ConfigErrors errors;

    public Json(JsonObject object, String path, ConfigErrors errors) {
        this.object = object;
        this.path = path;
        this.errors = errors;
    }

    public String path() {
        return path;
    }

    public String path(String field) {
        return path.isEmpty() ? field : path + "." + field;
    }

    public JsonObject raw() {
        return object;
    }

    public ConfigErrors errors() {
        return errors;
    }

    public boolean has(String field) {
        return object.has(field) && !object.get(field).isJsonNull();
    }

    /** Отмечает поля, которых не ждали: почти всегда это опечатка в имени. */
    public void rejectUnknown(Set<String> known) {
        for (String key : object.keySet()) {
            if (!known.contains(key)) {
                errors.add(path(key), "неизвестное поле (ожидались: " + String.join(", ", known) + ")");
            }
        }
    }

    public Double number(String field) {
        JsonElement value = object.get(field);
        if (value == null || value.isJsonNull()) {
            errors.add(path(field), "нет обязательного числа");
            return null;
        }
        if (!(value instanceof JsonPrimitive primitive) || !primitive.isNumber()) {
            errors.add(path(field), "ожидалось число, а здесь " + value);
            return null;
        }
        double number = primitive.getAsDouble();
        if (!Double.isFinite(number)) {
            errors.add(path(field), "число должно быть конечным");
            return null;
        }
        return number;
    }

    /** Необязательное число: нет поля — {@code null} без ошибки. */
    public Double optionalNumber(String field) {
        return has(field) ? number(field) : null;
    }

    public Double number(String field, double min, double max) {
        Double number = number(field);
        if (number != null && (number < min || number > max)) {
            errors.add(path(field), "должно быть от " + min + " до " + max + ", а здесь " + number);
            return null;
        }
        return number;
    }

    public Integer integer(String field, int min, int max) {
        Double number = number(field);
        if (number == null) {
            return null;
        }
        if (number != Math.rint(number)) {
            errors.add(path(field), "ожидалось целое число, а здесь " + number);
            return null;
        }
        if (number < min || number > max) {
            errors.add(path(field), "должно быть от " + min + " до " + max + ", а здесь " + number.longValue());
            return null;
        }
        return number.intValue();
    }

    public Boolean bool(String field) {
        JsonElement value = object.get(field);
        if (value instanceof JsonPrimitive primitive && primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        errors.add(path(field), value == null ? "нет обязательного true/false" : "ожидалось true или false");
        return null;
    }

    public boolean bool(String field, boolean fallback) {
        if (!has(field)) {
            return fallback;
        }
        Boolean value = bool(field);
        return value != null ? value : fallback;
    }

    public String string(String field) {
        JsonElement value = object.get(field);
        if (value instanceof JsonPrimitive primitive && primitive.isString() && !primitive.getAsString().isBlank()) {
            return primitive.getAsString();
        }
        errors.add(path(field), value == null ? "нет обязательной строки" : "ожидалась непустая строка");
        return null;
    }

    public Json object(String field) {
        JsonElement value = object.get(field);
        if (value instanceof JsonObject child) {
            return new Json(child, path(field), errors);
        }
        errors.add(path(field), value == null ? "нет обязательного объекта" : "ожидался объект {…}");
        return null;
    }

    public JsonArray array(String field) {
        JsonElement value = object.get(field);
        if (value instanceof JsonArray array) {
            return array;
        }
        errors.add(path(field), value == null ? "нет обязательного списка" : "ожидался список […]");
        return null;
    }

    /** Тройка чисел {@code [x, y, z]}. */
    public double[] triple(String field) {
        JsonArray array = array(field);
        return array == null ? null : triple(array, path(field), errors);
    }

    public static double[] triple(JsonElement element, String path, ConfigErrors errors) {
        if (!(element instanceof JsonArray array) || array.size() != 3) {
            errors.add(path, "ожидались три числа [x, y, z]");
            return null;
        }
        double[] out = new double[3];
        for (int i = 0; i < 3; i++) {
            JsonElement item = array.get(i);
            if (!(item instanceof JsonPrimitive primitive) || !primitive.isNumber()
                    || !Double.isFinite(primitive.getAsDouble())) {
                errors.add(path + "[" + i + "]", "ожидалось число");
                return null;
            }
            out[i] = primitive.getAsDouble();
        }
        return out;
    }
}
