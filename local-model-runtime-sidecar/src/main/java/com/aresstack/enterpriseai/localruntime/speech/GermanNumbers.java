package com.aresstack.enterpriseai.localruntime.speech;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Spells out the numbers in German text ({@code 1.250} → {@code eintausendzweihundertfünfzig}). */
final class GermanNumbers {

    private static final Pattern GROUPED = Pattern.compile("\\d{1,3}(\\.\\d{3})+(?!\\d)");
    private static final Pattern DECIMAL = Pattern.compile("(\\d+),(\\d+)");
    private static final Pattern INTEGER = Pattern.compile("\\d+");
    private static final String[] ONES = {"null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht",
            "neun", "zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn", "sechzehn", "siebzehn", "achtzehn",
            "neunzehn"};
    private static final String[] TENS = {"", "", "zwanzig", "dreißig", "vierzig", "fünfzig", "sechzig", "siebzig",
            "achtzig", "neunzig"};

    private GermanNumbers() {
    }

    static String expand(String text) {
        Matcher grouped = GROUPED.matcher(text);
        StringBuilder withoutGroups = new StringBuilder();
        while (grouped.find()) {
            grouped.appendReplacement(withoutGroups, grouped.group().replace(".", ""));
        }
        grouped.appendTail(withoutGroups);

        Matcher decimal = DECIMAL.matcher(withoutGroups);
        StringBuilder withoutDecimals = new StringBuilder();
        while (decimal.find()) {
            StringBuilder spoken = new StringBuilder(" ").append(number(decimal.group(1))).append(" komma");
            for (char digit : decimal.group(2).toCharArray()) {
                spoken.append(' ').append(ONES[digit - '0']);
            }
            decimal.appendReplacement(withoutDecimals, Matcher.quoteReplacement(spoken.append(' ').toString()));
        }
        decimal.appendTail(withoutDecimals);

        Matcher integer = INTEGER.matcher(withoutDecimals);
        StringBuilder result = new StringBuilder();
        while (integer.find()) {
            integer.appendReplacement(result, Matcher.quoteReplacement(" " + number(integer.group()) + " "));
        }
        integer.appendTail(result);
        return result.toString().replace("%", " prozent ");
    }

    private static String number(String digits) {
        if (digits.length() > 9) {
            StringBuilder single = new StringBuilder();
            for (char digit : digits.toCharArray()) {
                single.append(ONES[digit - '0']).append(' ');
            }
            return single.toString().trim();
        }
        return spell(Long.parseLong(digits));
    }

    static String spell(long n) {
        if (n < 1000000) {
            return n == 0 ? ONES[0] : belowMillion((int) n);
        }
        int millions = (int) (n / 1000000);
        int rest = (int) (n % 1000000);
        String head = millions == 1 ? "eine million" : belowThousand(millions) + " millionen";
        return rest == 0 ? head : head + " " + belowMillion(rest);
    }

    private static String belowMillion(int n) {
        int thousands = n / 1000;
        int rest = n % 1000;
        if (thousands == 0) {
            return belowThousand(rest);
        }
        String head = (thousands == 1 ? "ein" : belowThousand(thousands)) + "tausend";
        return rest == 0 ? head : head + belowThousand(rest);
    }

    private static String belowThousand(int n) {
        int hundreds = n / 100;
        int rest = n % 100;
        if (hundreds == 0) {
            return belowHundred(rest);
        }
        String head = (hundreds == 1 ? "ein" : ONES[hundreds]) + "hundert";
        return rest == 0 ? head : head + belowHundred(rest);
    }

    private static String belowHundred(int n) {
        if (n < 20) {
            return ONES[n];
        }
        int ones = n % 10;
        if (ones == 0) {
            return TENS[n / 10];
        }
        return (ones == 1 ? "ein" : ONES[ones]) + "und" + TENS[n / 10];
    }
}
