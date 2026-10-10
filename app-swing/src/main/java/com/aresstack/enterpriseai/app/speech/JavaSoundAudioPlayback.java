package com.aresstack.enterpriseai.app.speech;

import com.aresstack.enterpriseai.application.speech.AudioPlayback;

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
 * Audio-DSP). {@link #stop()} bricht die laufende Wiedergabe ab und verwirft den Puffer.
 */
public final class JavaSoundAudioPlayback implements AudioPlayback {

    private final Object lock = new Object();
    private SourceDataLine current;
    private boolean stopped;

    @Override
    public void play(byte[] wav) throws IOException {
        AudioInputStream audio;
        try {
            audio = AudioSystem.getAudioInputStream(new ByteArrayInputStream(wav));
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Audioformat nicht lesbar", e);
        }
        try {
            AudioFormat format = audio.getFormat();
            SourceDataLine line;
            try {
                line = AudioSystem.getSourceDataLine(format);
                line.open(format);
            } catch (LineUnavailableException | IllegalArgumentException e) {
                throw new IOException("Kein Audioausgabegerät verfügbar", e);
            }
            synchronized (lock) {
                current = line;
                stopped = false;
            }
            try {
                line.start();
                byte[] buffer = new byte[Math.max(4096, format.getFrameSize() * 1024)];
                int read;
                while ((read = audio.read(buffer)) > 0) {
                    if (isStopped()) {
                        line.flush();
                        return;
                    }
                    line.write(buffer, 0, read);
                }
                if (!isStopped()) {
                    line.drain();
                }
            } finally {
                synchronized (lock) {
                    current = null;
                }
                line.stop();
                line.close();
            }
        } finally {
            audio.close();
        }
    }

    private boolean isStopped() {
        synchronized (lock) {
            return stopped;
        }
    }

    @Override
    public void stop() {
        synchronized (lock) {
            stopped = true;
            if (current != null) {
                current.stop();
                current.flush();
            }
        }
    }
}
