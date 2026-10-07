package com.aresstack.enterpriseai.app.config;

import java.util.ArrayList;
import java.util.List;

/**
 * Zerlegt eine Argumentzeile wie eine Shell: Leerzeichen trennen, doppelte oder einfache Anführungszeichen
 * fassen zusammen, Backslash maskiert das nächste Zeichen. Für {@code agent.args}.
 */
final class CommandLine {

    private CommandLine() {
    }

    static List<String> split(String line) {
        List<String> args = new ArrayList<String>();
        if (line == null) {
            return args;
        }
        StringBuilder current = new StringBuilder();
        boolean inArgument = false;
        char quote = 0;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (quote != 0) {
                if (c == quote) {
                    quote = 0;
                } else if (c == '\\' && i + 1 < line.length() && line.charAt(i + 1) == quote) {
                    current.append(quote);
                    i++;
                } else {
                    current.append(c);
                }
                continue;
            }
            if (c == '"' || c == '\'') {
                quote = c;
                inArgument = true;
            } else if (c == '\\' && i + 1 < line.length()) {
                current.append(line.charAt(++i));
                inArgument = true;
            } else if (Character.isWhitespace(c)) {
                if (inArgument) {
                    args.add(current.toString());
                    current.setLength(0);
                    inArgument = false;
                }
            } else {
                current.append(c);
                inArgument = true;
            }
        }
        if (inArgument) {
            args.add(current.toString());
        }
        return args;
    }
}
