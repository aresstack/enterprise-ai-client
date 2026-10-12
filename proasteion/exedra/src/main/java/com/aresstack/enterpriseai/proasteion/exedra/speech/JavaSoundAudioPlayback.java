package com.aresstack.enterpriseai.proasteion.exedra.speech;

import com.aresstack.enterpriseai.astu.acropolis.speech.AudioPlayback;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Spielt WAV über eine {@link SourceDataLine} des Standardausgabegeräts ab (wie {@code playWav} in askai arch, ohne
 * Audio-DSP). Aufeinanderfolgende Stücke gleichen Formats laufen über dieselbe offene Leitung: kein Öffnen, Leerlaufen
 * und Schließen je Stück, also keine Lücke zwischen Absätzen oder Sätzen. {@link #finish()} lässt die Folge
 * ausklingen und schließt die Leitung, {@link #stop()} bricht ab und verwirft den Puffer. Ein Stopp gilt bis zum
 * nächsten {@link #begin()}: ein Stück, dessen Wiedergabe erst nach dem Stopp beginnen wollte, erklingt nicht mehr
 * (sonst spräche nach einem Modellwechsel noch ein Absatz der alten Stimme neben der neuen).
 */
public final class JavaSoundAudioPlayback implements AudioPlayback {

    private final Object lock = new Object();
    private SourceDataLine line;
    private AudioFormat lineFormat;
    /** Die Leitung, die {@link #finish()} gerade ausklingen lässt; {@link #stop()} hält auch sie an. */
    private SourceDataLine draining;
    private boolean stopped;

    @Override
    public void begin() {
        synchronized (lock) {
            stopped = false;
        }
    }

    @Override
    public void play(byte[] wav) throws IOException {
        if (isStopped()) {
            return;
        }
        AudioInputStream audio;
        try {
            audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav));
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Audioformat nicht lesbar", e);
        }
        try {
            AudioFormat format = audio.getFormat();
            SourceDataLine target = open(format);
            byte[] buffer = new byte[Math.max(4096, format.getFrameSize() * 1024)];
            int read;
            while ((read = audio.read(buffer)) > 0) {
                if (isStopped()) {
                    return;
                }
                target.write(buffer, 0, read); // blockiert nur, solange der Puffer der Leitung voll ist
            }
        } finally {
            audio.close();
        }
    }

    /** Die offene Leitung, wenn das Format passt; sonst wird die alte ausgespielt und eine neue geöffnet. */
    private SourceDataLine open(AudioFormat format) throws IOException {
        synchronized (lock) {
            if (line != null && line.isOpen() && lineFormat != null && lineFormat.matches(format)) {
                if (!stopped) {
                    line.start();
                }
                return line;
            }
        }
        finish();
        SourceDataLine opened;
        try {
            opened = AudioSystem.getSourceDataLine(format);
            opened.open(format);
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new IOException("Kein Audioausgabegerät verfügbar", e);
        }
        synchronized (lock) {
            line = opened;
            lineFormat = format;
            if (stopped) {
                opened.stop(); // während des Öffnens gestoppt: nichts mehr ausgeben
            } else {
                opened.start();
            }
        }
        return opened;
    }

    private boolean isStopped() {
        synchronized (lock) {
            return stopped;
        }
    }

    @Override
    public void finish() {
        SourceDataLine current;
        boolean drain;
        synchronized (lock) {
            current = line;
            drain = !stopped;
            line = null;
            lineFormat = null;
            draining = drain ? current : null;
        }
        if (current == null) {
            return;
        }
        try {
            if (drain) {
                current.drain(); // endet auch, wenn stop() die Leitung anhält
            }
        } finally {
            synchronized (lock) {
                draining = null;
            }
            current.stop();
            current.close();
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            stopped = true;
            if (line != null) {
                line.stop();
                line.flush();
            }
            if (draining != null) {
                draining.stop();
                draining.flush();
            }
        }
    }
}
