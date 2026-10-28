package com.kirbornu.gimpanum.magic.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Ошибки разбора настроек магии, каждая с путём до места.
 *
 * <p>Разбор не останавливается на первой ошибке: правят файлы руками, и
 * узнавать о пяти опечатках по одной, перезагружая пять раз, — мучение.
 */
public final class ConfigErrors {

    private final List<String> messages = new ArrayList<>();

    public void add(String path, String message) {
        messages.add(path + ": " + message);
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public List<String> messages() {
        return List.copyOf(messages);
    }

    public int count() {
        return messages.size();
    }
}
