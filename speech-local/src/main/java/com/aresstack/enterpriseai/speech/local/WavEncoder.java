package com.aresstack.enterpriseai.speech.local;

import java.io.ByteArrayOutputStream;

/** Float-Samples in [-1, 1] zu einer Mono-WAV-Datei, 16 Bit PCM (RIFF, little endian). */
final class WavEncoder {

    private WavEncoder() {
    }

    static byte[] encode(float[] samples, int sampleRate) {
        int dataBytes = samples.length * 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream(44 + dataBytes);
        ascii(out, "RIFF");
        int32(out, 36 + dataBytes);
        ascii(out, "WAVE");
        ascii(out, "fmt ");
        int32(out, 16);
        int16(out, 1);              // PCM
        int16(out, 1);              // mono
        int32(out, sampleRate);
        int32(out, sampleRate * 2); // Bytes je Sekunde
        int16(out, 2);              // Blockgröße
        int16(out, 16);             // Bits je Sample
        ascii(out, "data");
        int32(out, dataBytes);
        for (float sample : samples) {
            float clipped = Math.max(-1f, Math.min(1f, Float.isNaN(sample) ? 0f : sample));
            int16(out, Math.round(clipped * 32767f));
        }
        return out.toByteArray();
    }

    private static void ascii(ByteArrayOutputStream out, String text) {
        for (int i = 0; i < text.length(); i++) {
            out.write(text.charAt(i));
        }
    }

    private static void int16(ByteArrayOutputStream out, int value) {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    private static void int32(ByteArrayOutputStream out, int value) {
        int16(out, value & 0xffff);
        int16(out, (value >>> 16) & 0xffff);
    }
}
