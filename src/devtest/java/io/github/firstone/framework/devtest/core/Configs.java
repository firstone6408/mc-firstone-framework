package io.github.firstone.framework.devtest.core;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Resets live feature configs to their defaults between tests, without saving them
 *
 * <p>Feature configs are plain objects with public fields and a no-argument constructor that sets the defaults (the
 * same objects the config files are read into); their fields are copied, so the features keep their config
 * objects.</p>
 */
final class Configs {

    private Configs() {}

    /** Sets every field of each config to its default value */
    static void resetAll(List<Object> configs) {
        for (Object config : configs) {
            copyFields(newInstance(config.getClass()), config);
        }
    }

    /** Returns a copy of each config, to put back with {@link #restoreAll} */
    static List<Object> copyAll(List<Object> configs) {
        List<Object> copies = new ArrayList<>();
        for (Object config : configs) {
            Object copy = newInstance(config.getClass());
            copyFields(config, copy);
            copies.add(copy);
        }
        return copies;
    }

    /** Puts back the values copied by {@link #copyAll} */
    static void restoreAll(List<Object> configs, List<Object> copies) {
        for (int i = 0; i < configs.size(); i++) {
            copyFields(copies.get(i), configs.get(i));
        }
    }

    private static Object newInstance(Class<?> type) {
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Config " + type.getName() + " needs a no-argument constructor", e);
        }
    }

    private static void copyFields(Object from, Object to) {
        for (Field field : from.getClass().getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)) {
                continue;
            }
            try {
                field.setAccessible(true);
                field.set(to, field.get(from));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Cannot copy config field " + field, e);
            }
        }
    }
}
