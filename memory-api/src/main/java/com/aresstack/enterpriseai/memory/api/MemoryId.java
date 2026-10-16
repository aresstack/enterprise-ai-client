package com.aresstack.enterpriseai.memory.api;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Short, durable and never reused id of a memory, shown as {@code M-0042}. The store hands out the numbers; a deleted
 * number is not given out again.
 */
public final class MemoryId {

    private static final Pattern VISIBLE = Pattern.compile("M-?0*(\\d{1,18})");

    private final long number;

    private MemoryId(long number) {
        if (number < 1) {
            throw new IllegalArgumentException("memory number must be positive: " + number);
        }
        this.number = number;
    }

    public static MemoryId of(long number) {
        return new MemoryId(number);
    }

    /** Parses {@code M-0042}, {@code m-42} or {@code M42}; {@code null} if {@code text} is not a memory id. */
    public static MemoryId parse(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = VISIBLE.matcher(text.trim().toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            return null;
        }
        try {
            long value = Long.parseLong(matcher.group(1));
            return value < 1 ? null : new MemoryId(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public long number() {
        return number;
    }

    /** The visible form, at least four digits: {@code M-0042}. */
    public String visible() {
        return String.format(Locale.ROOT, "M-%04d", number);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MemoryId && ((MemoryId) other).number == number;
    }

    @Override
    public int hashCode() {
        return (int) (number ^ (number >>> 32));
    }

    @Override
    public String toString() {
        return visible();
    }
}
