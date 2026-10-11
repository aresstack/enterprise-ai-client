package com.aresstack.enterpriseai.research.runtime.support;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/**
 * application/x-www-form-urlencoded in UTF-8, ohne {@code java.net} (der Kern kennt keine Netzwerktypen):
 * dieselbe Ausgabe wie {@code URLEncoder.encode(v, "UTF-8")}, dieselbe Eingabe wie {@code URLDecoder}.
 */
public final class FormCoding {

    private static final String HEX = "0123456789ABCDEF";

    private FormCoding() {
    }

    public static String encode(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length() + 16);
        for (byte b : value.getBytes(StandardCharsets.UTF_8)) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '*' || c == '_') {
                out.append((char) c);
            } else if (c == ' ') {
                out.append('+');
            } else {
                out.append('%').append(HEX.charAt(c >> 4)).append(HEX.charAt(c & 0xf));
            }
        }
        return out.toString();
    }

    /** @throws IllegalArgumentException bei einer kaputten %-Sequenz (wie URLDecoder) */
    public static String decode(String value) {
        if (value == null) {
            return "";
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '+') {
                bytes.write(' ');
            } else if (c == '%') {
                if (i + 2 >= value.length()) {
                    throw new IllegalArgumentException("incomplete escape");
                }
                int hi = Character.digit(value.charAt(i + 1), 16);
                int lo = Character.digit(value.charAt(i + 2), 16);
                if (hi < 0 || lo < 0) {
                    throw new IllegalArgumentException("illegal escape");
                }
                bytes.write((hi << 4) | lo);
                i += 2;
            } else {
                byte[] raw = String.valueOf(c).getBytes(StandardCharsets.UTF_8);
                bytes.write(raw, 0, raw.length);
            }
        }
        return new String(bytes.toByteArray(), StandardCharsets.UTF_8);
    }
}
