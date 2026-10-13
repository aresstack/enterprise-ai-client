package com.aresstack.enterpriseai.proasteion.exedra.speech;

import com.aresstack.enterpriseai.astu.acropolis.speech.AudioPlayback;
import com.aresstack.enterpriseai.astu.acropolis.speech.ReadAloudService;
import com.aresstack.enterpriseai.astu.acropolis.speech.SpeechChunking;
import com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort;
import com.aresstack.enterpriseai.speech.api.SynthesizedSpeech;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Der neueste Satz gewinnt: hängt die Synthese eines alten Satzes noch an der Remote-API, startet ein neuer Satz
 * sofort statt hinter ihm anzustehen, und der alte erklingt nie mehr (Merlin im Einrichtungsassistenten).
 */
public class ReadAloudBindingTest {

    @Test
    public void newSentenceStartsAtOnceWhileTheOldRequestStillHangs() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        Port port = new Port(release);
        Playback playback = new Playback();
        ReadAloudBinding binding = ReadAloudBinding.available(new ReadAloudService(port, "orpheus-tts-3b-de",
                "Thomas", playback, SpeechChunking.SENTENCES, 2, null), "Merlins Stimme", false);
        CountDownLatch oldFinished = new CountDownLatch(1);
        try {
            binding.speak("Alter Satz.", oldFinished::countDown);
            assertTrue("alter Request läuft", port.oldRequested.await(5, TimeUnit.SECONDS));

            binding.speak("Neuer Satz.");
            assertTrue("neuer Request startet ohne Freigabe des alten", port.newRequested.await(5, TimeUnit.SECONDS));
            assertTrue("neuer Satz erklingt, obwohl der alte Request noch hängt", playback.awaitPlay("Neuer Satz."));
            assertTrue("die abgelöste Ausgabe meldet ihr Ende", oldFinished.await(5, TimeUnit.SECONDS));

            release.countDown(); // die alte Antwort kommt doch noch
            Thread.sleep(200);
            assertEquals(1, playback.played.size());
            assertFalse(playback.played.contains("Alter Satz."));
        } finally {
            release.countDown();
            binding.close();
        }
    }

    @Test
    public void stopEndsAHangingRequestAndReportsTheEnd() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        Port port = new Port(release);
        Playback playback = new Playback();
        ReadAloudBinding binding = ReadAloudBinding.available(new ReadAloudService(port, "orpheus-tts-3b-de",
                "Thomas", playback, SpeechChunking.SENTENCES, 2, null), "Merlins Stimme", false);
        CountDownLatch finished = new CountDownLatch(1);
        try {
            binding.speak("Alter Satz.", finished::countDown);
            assertTrue(port.oldRequested.await(5, TimeUnit.SECONDS));
            binding.stop();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            release.countDown();
            Thread.sleep(200);
            assertTrue(playback.played.isEmpty());
        } finally {
            release.countDown();
            binding.close();
        }
    }

    /** Der alte Satz hängt wie ein HTTP-Request ohne Abbruch-Handle: Interrupts helfen ihm nicht. */
    private static final class Port implements SpeechSynthesisPort {

        final CountDownLatch oldRequested = new CountDownLatch(1);
        final CountDownLatch newRequested = new CountDownLatch(1);
        private final CountDownLatch release;

        Port(CountDownLatch release) {
            this.release = release;
        }

        @Override
        public String catalogId() {
            return "kipitz";
        }

        @Override
        public SynthesizedSpeech synthesize(String modelId, String voice, String text) {
            if (text.startsWith("Alter")) {
                oldRequested.countDown();
                awaitIgnoringInterrupts();
            } else {
                newRequested.countDown();
            }
            return new SynthesizedSpeech(text.getBytes(StandardCharsets.UTF_8));
        }

        private void awaitIgnoringInterrupts() {
            boolean interrupted = false;
            while (true) {
                try {
                    if (release.await(10, TimeUnit.SECONDS)) {
                        break;
                    }
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private static final class Playback implements AudioPlayback {

        final List<String> played = new CopyOnWriteArrayList<String>();

        @Override
        public void play(byte[] wav) {
            played.add(new String(wav, StandardCharsets.UTF_8));
        }

        boolean awaitPlay(String text) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5000;
            while (!played.contains(text) && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            return played.contains(text);
        }

        @Override
        public void finish() {
        }

        @Override
        public void stop() {
        }
    }
}
