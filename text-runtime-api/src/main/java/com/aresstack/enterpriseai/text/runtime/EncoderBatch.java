package com.aresstack.enterpriseai.text.runtime;

/**
 * Eingabe eines Encoder-Graphen: Token-Ids je Text, auf gleiche Länge aufgefüllt, mit Maske (1 = Token, 0 = Füllung)
 * und Segment-Ids (bei einem Text alles 0). Zeilen sind Texte, Spalten Positionen.
 */
public final class EncoderBatch {

    private final long[][] inputIds;
    private final long[][] attentionMask;

    public EncoderBatch(long[][] inputIds, long[][] attentionMask) {
        if (inputIds == null || attentionMask == null || inputIds.length == 0
                || inputIds.length != attentionMask.length) {
            throw new IllegalArgumentException("inputIds and attentionMask must have the same non-empty row count");
        }
        int width = inputIds[0].length;
        for (int row = 0; row < inputIds.length; row++) {
            if (inputIds[row].length != width || attentionMask[row].length != width || width == 0) {
                throw new IllegalArgumentException("all rows must have the same non-zero length");
            }
        }
        this.inputIds = inputIds;
        this.attentionMask = attentionMask;
    }

    public int rows() {
        return inputIds.length;
    }

    public int length() {
        return inputIds[0].length;
    }

    /** Zeilenweise flach (Zeile mal Länge). */
    public long[] flatInputIds() {
        return flatten(inputIds);
    }

    /** Zeilenweise flach (Zeile mal Länge). */
    public long[] flatAttentionMask() {
        return flatten(attentionMask);
    }

    public long mask(int row, int position) {
        return attentionMask[row][position];
    }

    private static long[] flatten(long[][] rows) {
        int width = rows[0].length;
        long[] flat = new long[rows.length * width];
        for (int row = 0; row < rows.length; row++) {
            System.arraycopy(rows[row], 0, flat, row * width, width);
        }
        return flat;
    }
}
