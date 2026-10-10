package com.aresstack.enterpriseai.localruntime;

import java.util.Map;

/** Public bridge to the package-private {@link LocalJson} parser for the speech package. */
public final class LocalJsonAccess {

    private LocalJsonAccess() {
    }

    public static Map<String, Object> object(String text) {
        return LocalJson.parseObject(text);
    }

    public static String write(Object value) {
        return LocalJson.write(value);
    }
}
