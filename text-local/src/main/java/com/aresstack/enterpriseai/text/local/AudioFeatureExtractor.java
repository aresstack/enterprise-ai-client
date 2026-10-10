package com.aresstack.enterpriseai.text.local;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Log-Mel-Merkmale wie {@code Gemma4AudioFeatureExtractor} aus transformers (Universal Speech Model): Audio auf ein
 * Vielfaches von 128 Abtastwerten auffüllen, links {@code frame_length/2} Nullen (semikausal), Rahmen der Länge
 * {@code frame_length + 1} im Abstand {@code hop_length}, optional Preemphase, periodisches Hann-Fenster, FFT der Länge
 * {@code fft_length}, Betrag, Mel-Filterbank (HTK-Skala, ohne Normierung), {@code log(mel + mel_floor)}, optional
 * Normierung je Band. Ein Rahmen gilt nur, wenn sein letzter Abtastwert echtes Audio ist; ungültige Rahmen sind null.
 * Werte aus {@code processor_config.json} ({@code feature_extractor}) bzw. {@code preprocessor_config.json}, sonst die
 * Vorgaben von transformers.
 */
final class AudioFeatureExtractor {

    private static final int PAD_TO_MULTIPLE_OF = 128;

    private final int sampleRate;
    private final int bins;
    private final int frameLength;
    private final int hopLength;
    private final int fftLength;
    private final double preemphasis;
    private final boolean preemphasisHtk;
    private final double melFloor;
    private final double inputScale;
    private final double[] perBinMean;
    private final double[] perBinStddev;
    private final double[] window;
    /** Filterbank je Frequenz-Bin und Mel-Band ({@code (fft/2 + 1) × bins}). */
    private final double[][] filters;
    private final double[] cos;
    private final double[] sin;

    AudioFeatureExtractor(Map<String, Object> config) {
        sampleRate = Json.integer(config.get("sampling_rate"), 16000);
        bins = Json.integer(config.get("feature_size"), 128);
        double frameMillis = Json.number(config.get("frame_length_ms"), 20.0);
        double hopMillis = Json.number(config.get("hop_length_ms"), 10.0);
        frameLength = config.get("frame_length_ms") == null && config.get("frame_length") instanceof Number
                ? Json.integer(config.get("frame_length"), 0) : (int) Math.round(sampleRate * frameMillis / 1000.0);
        hopLength = config.get("hop_length_ms") == null && config.get("hop_length") instanceof Number
                ? Json.integer(config.get("hop_length"), 0) : (int) Math.round(sampleRate * hopMillis / 1000.0);
        int fft = 1;
        while (fft < frameLength) {
            fft <<= 1;
        }
        if (Json.bool(config.get("fft_overdrive"), false)) {
            fft <<= 1;
        }
        fftLength = fft;
        preemphasis = Json.number(config.get("preemphasis"), 0.0);
        preemphasisHtk = Json.bool(config.get("preemphasis_htk_flavor"), true);
        melFloor = scalar(config.get("mel_floor"), 1e-3);
        inputScale = Json.number(config.get("input_scale_factor"), 1.0);
        perBinMean = vector(config.get("per_bin_mean"), bins);
        perBinStddev = vector(config.get("per_bin_stddev"), bins);
        if (frameLength <= 0 || hopLength <= 0 || bins <= 0) {
            throw new IllegalArgumentException("Ungültige Audio-Merkmale: frame_length " + frameLength
                    + ", hop_length " + hopLength + ", feature_size " + bins);
        }
        window = new double[frameLength];
        for (int i = 0; i < frameLength; i++) {
            window[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / frameLength);
        }
        filters = melFilters(fftLength / 2 + 1, bins, Json.number(config.get("min_frequency"), 0.0),
                Json.number(config.get("max_frequency"), 8000.0), sampleRate);
        cos = new double[fftLength / 2];
        sin = new double[fftLength / 2];
        for (int i = 0; i < fftLength / 2; i++) {
            cos[i] = Math.cos(-2 * Math.PI * i / fftLength);
            sin[i] = Math.sin(-2 * Math.PI * i / fftLength);
        }
    }

    int sampleRate() {
        return sampleRate;
    }

    int bins() {
        return bins;
    }

    /** Merkmale einer Aufnahme (mono, {@link #sampleRate()} Hz). */
    Features extract(float[] samples) {
        int original = samples.length;
        int padded = original % PAD_TO_MULTIPLE_OF == 0 ? original
                : original + PAD_TO_MULTIPLE_OF - original % PAD_TO_MULTIPLE_OF;
        int padLeft = frameLength / 2;
        double[] signal = new double[padLeft + padded];
        for (int i = 0; i < original; i++) {
            signal[padLeft + i] = samples[i] * inputScale;
        }
        int frameSize = frameLength + 1;
        int frames = signal.length < frameSize ? 0 : (signal.length - frameSize) / hopLength + 1;
        float[] features = new float[frames * bins];
        boolean[] mask = new boolean[frames];
        double[] real = new double[fftLength];
        double[] imaginary = new double[fftLength];
        double[] magnitude = new double[fftLength / 2 + 1];
        for (int frame = 0; frame < frames; frame++) {
            int start = frame * hopLength;
            int last = start + frameSize - 1;
            mask[frame] = last >= padLeft && last < padLeft + original;
            if (!mask[frame]) {
                continue;
            }
            Arrays.fill(real, 0);
            Arrays.fill(imaginary, 0);
            for (int k = 0; k < frameLength; k++) {
                double value;
                if (preemphasis > 0) {
                    if (preemphasisHtk) {
                        value = k == 0 ? signal[start] * (1 - preemphasis)
                                : signal[start + k] - preemphasis * signal[start + k - 1];
                    } else {
                        value = signal[start + k + 1] - preemphasis * signal[start + k];
                    }
                } else {
                    value = signal[start + k];
                }
                real[k] = value * window[k];
            }
            fft(real, imaginary);
            for (int k = 0; k < magnitude.length; k++) {
                magnitude[k] = Math.sqrt(real[k] * real[k] + imaginary[k] * imaginary[k]);
            }
            for (int band = 0; band < bins; band++) {
                double sum = 0;
                for (int k = 0; k < magnitude.length; k++) {
                    double weight = filters[k][band];
                    if (weight != 0) {
                        sum += magnitude[k] * weight;
                    }
                }
                double value = Math.log(sum + melFloor);
                if (perBinMean != null) {
                    value -= perBinMean[band];
                }
                if (perBinStddev != null) {
                    value /= perBinStddev[band];
                }
                features[frame * bins + band] = (float) value;
            }
        }
        return new Features(features, frames, bins, mask);
    }

    /** Iterative Radix-2-FFT an Ort und Stelle; Länge ist eine Zweierpotenz. */
    private void fft(double[] real, double[] imaginary) {
        int n = real.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = real[i];
                real[i] = real[j];
                real[j] = t;
                t = imaginary[i];
                imaginary[i] = imaginary[j];
                imaginary[j] = t;
            }
        }
        for (int size = 2; size <= n; size <<= 1) {
            int half = size >> 1;
            int step = n / size;
            for (int start = 0; start < n; start += size) {
                for (int k = 0; k < half; k++) {
                    double wr = cos[k * step];
                    double wi = sin[k * step];
                    int a = start + k;
                    int b = a + half;
                    double xr = real[b] * wr - imaginary[b] * wi;
                    double xi = real[b] * wi + imaginary[b] * wr;
                    real[b] = real[a] - xr;
                    imaginary[b] = imaginary[a] - xi;
                    real[a] += xr;
                    imaginary[a] += xi;
                }
            }
        }
    }

    /** Wie {@code transformers.audio_utils.mel_filter_bank(..., norm=None, mel_scale="htk")}. */
    static double[][] melFilters(int frequencyBins, int melBands, double minFrequency, double maxFrequency,
                                 int sampleRate) {
        double melMin = hertzToMel(minFrequency);
        double melMax = hertzToMel(maxFrequency);
        double[] filterFrequencies = new double[melBands + 2];
        for (int i = 0; i < filterFrequencies.length; i++) {
            filterFrequencies[i] = melToHertz(melMin + (melMax - melMin) * i / (melBands + 1));
        }
        double nyquist = sampleRate / 2;
        double[][] result = new double[frequencyBins][melBands];
        for (int k = 0; k < frequencyBins; k++) {
            double frequency = frequencyBins == 1 ? 0 : nyquist * k / (frequencyBins - 1);
            for (int band = 0; band < melBands; band++) {
                double down = (frequency - filterFrequencies[band])
                        / (filterFrequencies[band + 1] - filterFrequencies[band]);
                double up = (filterFrequencies[band + 2] - frequency)
                        / (filterFrequencies[band + 2] - filterFrequencies[band + 1]);
                result[k][band] = Math.max(0, Math.min(down, up));
            }
        }
        return result;
    }

    private static double hertzToMel(double hertz) {
        return 2595.0 * Math.log10(1.0 + hertz / 700.0);
    }

    private static double melToHertz(double mel) {
        return 700.0 * (Math.pow(10.0, mel / 2595.0) - 1.0);
    }

    private static double scalar(Object value, double fallback) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        List<Object> values = Json.list(value);
        return values.size() == 1 ? Json.number(values.get(0), fallback) : fallback;
    }

    private static double[] vector(Object value, int length) {
        List<Object> values = Json.list(value);
        if (values.size() != length) {
            return null;
        }
        double[] result = new double[length];
        for (int i = 0; i < length; i++) {
            result[i] = Json.number(values.get(i), 0);
        }
        return result;
    }

    /** Merkmale zeilenweise ({@code frames × bins}) und je Rahmen, ob er gültig ist. */
    static final class Features {
        final float[] values;
        final int frames;
        final int bins;
        final boolean[] mask;

        Features(float[] values, int frames, int bins, boolean[] mask) {
            this.values = values;
            this.frames = frames;
            this.bins = bins;
            this.mask = mask;
        }
    }
}
