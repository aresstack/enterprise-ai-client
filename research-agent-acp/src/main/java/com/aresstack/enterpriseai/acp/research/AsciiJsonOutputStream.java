package com.aresstack.enterpriseai.acp.research;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * STDOUT des Agentenprozesses als reines ASCII: jedes Nicht-ASCII-Zeichen der UTF-8-kodierten JSON-RPC-Zeilen wird
 * zur JSON-Escape-Sequenz {@code \}{@code uXXXX} (Zeichen außerhalb der BMP als Surrogatpaar). Nicht-ASCII steht in
 * JSON nur innerhalb von Zeichenketten, dort ist das Escape gleichwertig. So liest der Client die Antwort richtig,
 * gleich mit welcher Kodierung er STDOUT dekodiert: das ACP-SDK liest es mit der Plattform-Standardkodierung
 * ({@code StdioAcpClientTransport}, unter Windows meist Windows-1252), und aus „ä“ würde sonst „Ã¤“.
 *
 * <p>Ungültige UTF-8-Folgen werden zu U+FFFD. Eine Folge, die über zwei {@code write}-Aufrufe geht, wird
 * zusammengesetzt.
 */
final class AsciiJsonOutputStream extends FilterOutputStream {

    private static final char[] HEX = "0123456789abcdef".toCharArray();

    private int codePoint;
    private int pending;

    AsciiJsonOutputStream(OutputStream out) {
        super(out);
    }

    @Override
    public synchronized void write(int value) throws IOException {
        int b = value & 0xFF;
        if (pending > 0) {
            if ((b & 0xC0) == 0x80) {
                codePoint = (codePoint << 6) | (b & 0x3F);
                if (--pending == 0) {
                    escape(codePoint);
                }
                return;
            }
            pending = 0;
            escape(0xFFFD);
        }
        if (b < 0x80) {
            out.write(b);
        } else if ((b & 0xE0) == 0xC0) {
            codePoint = b & 0x1F;
            pending = 1;
        } else if ((b & 0xF0) == 0xE0) {
            codePoint = b & 0x0F;
            pending = 2;
        } else if ((b & 0xF8) == 0xF0) {
            codePoint = b & 0x07;
            pending = 3;
        } else {
            escape(0xFFFD);
        }
    }

    @Override
    public synchronized void write(byte[] bytes, int offset, int length) throws IOException {
        for (int i = offset; i < offset + length; i++) {
            write(bytes[i]);
        }
    }

    private void escape(int value) throws IOException {
        if (value >= 0x10000 && value <= 0x10FFFF) {
            int shifted = value - 0x10000;
            unit(0xD800 + (shifted >> 10));
            unit(0xDC00 + (shifted & 0x3FF));
        } else {
            unit(value > 0xFFFF ? 0xFFFD : value);
        }
    }

    private void unit(int value) throws IOException {
        out.write('\\');
        out.write('u');
        out.write(HEX[(value >> 12) & 0xF]);
        out.write(HEX[(value >> 8) & 0xF]);
        out.write(HEX[(value >> 4) & 0xF]);
        out.write(HEX[value & 0xF]);
    }
}
