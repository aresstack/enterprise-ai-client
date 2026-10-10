package com.aresstack.enterpriseai.text.runtime;

/**
 * Ausgabe eines Encoder-Graphen, flach: entweder schon ein Vektor je Text ({@link #pooled()}: Zeilen mal Breite)
 * oder ein Vektor je Token (Zeilen mal Länge mal Breite), dann poolt der Aufrufer nach der Modellkonfiguration.
 */
public final class EncoderOutput {

    private final float[] values;
    private final int rows;
    private final int length;
    private final int width;
    private final boolean pooled;

    private EncoderOutput(float[] values, int rows, int length, int width, boolean pooled) {
        if (values == null || rows <= 0 || width <= 0 || values.length != (long) rows * length * width) {
            throw new IllegalArgumentException("values do not match the shape");
        }
        this.values = values;
        this.rows = rows;
        this.length = length;
        this.width = width;
        this.pooled = pooled;
    }

    /** Ein Vektor je Text. */
    public static EncoderOutput pooled(float[] values, int rows, int width) {
        return new EncoderOutput(values, rows, 1, width, true);
    }

    /** Ein Vektor je Token. */
    public static EncoderOutput perToken(float[] values, int rows, int length, int width) {
        return new EncoderOutput(values, rows, length, width, false);
    }

    public boolean pooled() {
        return pooled;
    }

    public int rows() {
        return rows;
    }

    /** Positionen je Zeile; 1 bei {@link #pooled()}. */
    public int length() {
        return length;
    }

    public int width() {
        return width;
    }

    /** Wert an Zeile, Position, Dimension (Position 0 bei {@link #pooled()}). */
    public float value(int row, int position, int dimension) {
        return values[(row * length + position) * width + dimension];
    }
}
