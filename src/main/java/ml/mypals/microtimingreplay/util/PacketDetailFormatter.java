package ml.mypals.microtimingreplay.util;

import net.minecraft.network.protocol.Packet;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;


public final class PacketDetailFormatter {

    public static final String LINE_SEPARATOR = "\n";
    public static final String KV_SEPARATOR = " = ";
    private static final int MAX_TOTAL = 1000;
    private static final int MAX_VALUE = 100;
    private static final int MAX_ELEMENTS = 10;
    private static final int MAX_DEPTH = 5;

    private static final Map<Class<?>, List<Accessor>> CACHE = new ConcurrentHashMap<>();

    private PacketDetailFormatter() {
    }

    public static String describe(Packet<?> packet) {
        if (packet == null) {
            return "";
        }
        try {
            List<Accessor> accessors = CACHE.computeIfAbsent(packet.getClass(), PacketDetailFormatter::buildAccessors);
            if (accessors.isEmpty()) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (Accessor accessor : accessors) {
                if (sb.length() >= MAX_TOTAL) {
                    sb.append("\n...");
                    break;
                }
                if (!sb.isEmpty()) {
                    sb.append(LINE_SEPARATOR);
                }
                sb.append(accessor.name()).append(KV_SEPARATOR);
                Object value;
                try {
                    value = accessor.get(packet);
                } catch (Throwable t) {
                    value = "<error>";
                }
                appendValue(sb, value, 0);
            }
            return sb.length() <= MAX_TOTAL ? sb.toString() : sb.substring(0, MAX_TOTAL) + "…";
        } catch (Throwable t) {
            return "";
        }
    }

    private static List<Accessor> buildAccessors(Class<?> type) {
        List<Accessor> out = new ArrayList<>();
        try {
            if (type.isRecord()) {
                for (RecordComponent component : type.getRecordComponents()) {
                    Method accessor = component.getAccessor();
                    try {
                        accessor.setAccessible(true);
                    } catch (Throwable ignored) {
                    }
                    out.add(new Accessor(component.getName(), accessor::invoke));
                }
            } else {
                for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                    for (Field field : current.getDeclaredFields()) {
                        if (Modifier.isStatic(field.getModifiers()) || field.isSynthetic()) {
                            continue;
                        }
                        if (field.getName().startsWith("mtr$")) {
                            continue;
                        }
                        try {
                            field.setAccessible(true);
                        } catch (Throwable ignored) {
                            continue;
                        }
                        out.add(new Accessor(field.getName(), field::get));
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void appendValue(StringBuilder sb, Object value, int depth) {
        switch (value) {
            case null -> {
                sb.append("null");
                return;
            }
            case Optional<?> optional -> {
                if (optional.isEmpty()) {
                    sb.append("empty");
                } else {
                    appendValue(sb, optional.get(), depth);
                }
                return;
            }
            case byte[] bytes -> {
                sb.append("byte[").append(bytes.length).append(']');
                return;
            }
            default -> {
            }
        }
        Class<?> type = value.getClass();
        if (type.isArray()) {
            int length = Array.getLength(value);
            sb.append('[');
            for (int i = 0; i < Math.min(length, MAX_ELEMENTS); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                appendValue(sb, Array.get(value, i), depth + 1);
            }
            if (length > MAX_ELEMENTS) {
                sb.append(", ...").append(length);
            }
            sb.append(']');
            return;
        }
        if (value instanceof Collection<?> collection) {
            sb.append('[');
            int i = 0;
            for (Object element : collection) {
                if (i >= MAX_ELEMENTS) {
                    sb.append(", ...").append(collection.size());
                    break;
                }
                if (i > 0) {
                    sb.append(", ");
                }
                appendValue(sb, element, depth + 1);
                i++;
            }
            sb.append(']');
            return;
        }
        if (value instanceof Map<?, ?> map) {
            sb.append('{');
            int i = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (i >= MAX_ELEMENTS) {
                    sb.append(", ...").append(map.size());
                    break;
                }
                if (i > 0) {
                    sb.append(", ");
                }
                appendValue(sb, entry.getKey(), depth + 1);
                sb.append('=');
                appendValue(sb, entry.getValue(), depth + 1);
                i++;
            }
            sb.append('}');
            return;
        }
        if (type.isRecord()) {
            if (depth >= MAX_DEPTH) {
                sb.append(type.getSimpleName()).append("{...}");
                return;
            }
            sb.append(type.getSimpleName()).append('{');
            List<Accessor> accessors = CACHE.computeIfAbsent(type, PacketDetailFormatter::buildAccessors);
            for (int i = 0; i < accessors.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                Accessor accessor = accessors.get(i);
                sb.append(accessor.name()).append('=');
                Object nested;
                try {
                    nested = accessor.get(value);
                } catch (Throwable t) {
                    nested = "<error>";
                }
                appendValue(sb, nested, depth + 1);
            }
            sb.append('}');
            return;
        }
        String text;
        try {
            text = String.valueOf(value);
        } catch (Throwable t) {
            text = type.getSimpleName();
        }
        sb.append(truncate(text, MAX_VALUE));
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String flat = text.indexOf('\n') < 0 ? text : text.replace('\n', ' ');
        return flat.length() <= max ? flat : flat.substring(0, max) + "…";
    }

    @FunctionalInterface
    private interface Getter {
        Object get(Object owner) throws Exception;
    }

    private record Accessor(String name, Getter getter) {
        Object get(Object owner) throws Exception {
            return getter.get(owner);
        }
    }
}
