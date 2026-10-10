package com.aresstack.enterpriseai.localruntime.speech;

import java.util.Map;

/** Lenient access to the parsed voice configuration files. */
final class VoiceJson {

    private VoiceJson() {
    }

    static int intValue(Object value, int fallback) {
        return value instanceof Number number && number.intValue() > 0 ? number.intValue() : fallback;
    }

    static float floatValue(Object value, float fallback) {
        return value instanceof Number number ? number.floatValue() : fallback;
    }

    static boolean boolValue(Object value, boolean fallback) {
        return value instanceof Boolean flag ? flag : fallback;
    }

    static String text(Object value) {
        return value instanceof String string ? string : "";
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }
}
