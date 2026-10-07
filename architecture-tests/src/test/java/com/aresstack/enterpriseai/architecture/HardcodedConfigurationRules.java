package com.aresstack.enterpriseai.architecture;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Nachtrag 1 und 18: Base-URL, Modellnamen und API-Keys sind Konfiguration und werden nicht hart codiert;
 * Nachtrag 5: MCP bindet nur an 127.0.0.1. Geprüft werden die String-Literale im Konstantenpool jeder
 * Produktionsklasse.
 */
final class HardcodedConfigurationRules {

    /** Host als Gruppe 1: entweder eine IPv6-Adresse in eckigen Klammern oder alles bis Port, Pfad oder Query. */
    private static final Pattern URL = Pattern.compile("https?://(\\[[0-9a-fA-F:.]+]|[^/\\s:\"'?#\\[]+)");
    private static final Pattern MODEL_NAME = Pattern.compile(
            "(?i)(gpt-oss|e5-base-sts|text-embedding-|gpt-4|gpt-3\\.5|claude-\\d|llama-?\\d|mistral-)");
    private static final Pattern API_KEY = Pattern.compile("(?i)(sk-[A-Za-z0-9]{20,}|Bearer\\s+[A-Za-z0-9._~+/=-]{16,})");
    private static final Pattern BIND_ALL = Pattern.compile("(^|[^0-9.])0\\.0\\.0\\.0([^0-9.]|$)");

    private HardcodedConfigurationRules() {
    }

    static boolean isLoopback(String host) {
        String normalized = host.toLowerCase(Locale.ROOT);
        return normalized.equals("localhost") || normalized.equals("127.0.0.1") || normalized.equals("::1")
                || normalized.equals("[::1]");
    }

    static List<String> findings(ClassFileInfo info) {
        List<String> findings = new ArrayList<String>();
        for (String constant : info.stringConstants()) {
            Matcher url = URL.matcher(constant);
            while (url.find()) {
                if (!isLoopback(url.group(1))) {
                    findings.add(info.className() + ": hart codierte URL \"" + constant + "\" (Base-URLs sind Konfiguration)");
                    break;
                }
            }
            if (MODEL_NAME.matcher(constant).find()) {
                findings.add(info.className() + ": hart codierter Modellname \"" + constant + "\" (Modelle sind Konfiguration)");
            }
            if (API_KEY.matcher(constant).find()) {
                findings.add(info.className() + ": Literal sieht aus wie ein API-Key oder Bearer-Token");
            }
            if (BIND_ALL.matcher(constant).find()) {
                findings.add(info.className() + ": Bind-All-Adresse \"" + constant + "\" (nur 127.0.0.1 ist erlaubt)");
            }
        }
        return findings;
    }

    static List<String> violations(List<File> classFiles) {
        List<String> violations = new ArrayList<String>();
        for (File classFile : classFiles) {
            violations.addAll(findings(ClassFileInfo.read(classFile)));
        }
        return violations;
    }
}
