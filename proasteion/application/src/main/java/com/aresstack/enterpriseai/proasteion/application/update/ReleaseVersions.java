package com.aresstack.enterpriseai.proasteion.application.update;

import java.util.regex.Pattern;

/**
 * The release numbers of the fat jar ({@code projectVersion} in gradle.properties): two to four numeric parts,
 * optionally followed by a {@code -} or {@code +} qualifier. Compared numerically part by part, never as text, so
 * 0.10.0 comes after 0.9.0.
 */
final class ReleaseVersions {

    private static final Pattern VERSION = Pattern.compile("\\d+(\\.\\d+){1,3}([-+][0-9A-Za-z.-]+)?");

    private ReleaseVersions() {
    }

    static boolean isValid(String version) {
        return version != null && VERSION.matcher(version).matches();
    }

    /** Negative, zero or positive as {@code a} is below, equal to or above {@code b} in its numeric parts. */
    static int compare(String a, String b) {
        String[] left = numbers(a);
        String[] right = numbers(b);
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            int order = compareNumber(i < left.length ? left[i] : "0", i < right.length ? right[i] : "0");
            if (order != 0) {
                return order;
            }
        }
        return 0;
    }

    /** Compares digit strings of any length without overflow. */
    private static int compareNumber(String a, String b) {
        String left = stripZeros(a);
        String right = stripZeros(b);
        if (left.length() != right.length()) {
            return left.length() < right.length() ? -1 : 1;
        }
        return Integer.signum(left.compareTo(right));
    }

    private static String stripZeros(String digits) {
        int start = 0;
        while (start < digits.length() - 1 && digits.charAt(start) == '0') {
            start++;
        }
        return digits.substring(start);
    }

    private static String[] numbers(String version) {
        String plain = version;
        for (char qualifier : new char[]{'-', '+'}) {
            int at = plain.indexOf(qualifier);
            if (at >= 0) {
                plain = plain.substring(0, at);
            }
        }
        return plain.split("\\.");
    }
}
