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
import static org.junit.Assert.assertTrue;

/**
 * TTS-Wechsel in den Einstellungen während einer laufenden Antwort: die alte Ausgabe endet, kein späterer Absatz der
 * Antwort erklingt mit dem alten Modell neben dem neuen, und das neue Modell spricht erst die nächste Antwort, dann
 * aber jeden ihrer Absätze.
 */
public class SwitchableReadAloudTest {

    private static final String ANSWER = "Erster Absatz.\n\nZweiter Absatz.\n\nDritter Absatz.\n\nVierter Absatz.";

    @Test
    public void switchingTheModelMidAnswerEndsTheOldOutputWithoutMixingVoices() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        Speaker orpheus = new Speaker("orpheus-tts-3b-de", release);
        Speaker qwen = new Speaker("qwen3-tts", null);
        SwitchableReadAloud readAloud = new SwitchableReadAloud(orpheus.binding());

        readAloud.speak(ANSWER);
        assertTrue("erster Absatz erklingt", orpheus.firstPlayed.await(5, TimeUnit.SECONDS));

        readAloud.replace(qwen.binding()); // Einstellungen gespeichert: neues TTS-Modell
        release.countDown(); // die übrigen Orpheus-Absätze wären jetzt fertig
        Thread.sleep(300);

        assertEquals("nach dem Wechsel kein weiterer Orpheus-Absatz", 1, orpheus.playback.played.size());
        assertTrue(orpheus.playback.stops > 0);
        assertTrue("die neue Stimme spricht nicht von selbst weiter", qwen.playback.played.isEmpty());

        readAloud.speak(ANSWER);
        assertTrue(qwen.playback.awaitPlays(4));
        for (String played : qwen.playback.played) {
            assertTrue(played, played.startsWith("qwen3-tts:"));
        }
        for (String model : orpheus.port.models) {
            assertEquals("orpheus-tts-3b-de", model);
        }
        for (String model : qwen.port.models) {
            assertEquals("qwen3-tts", model);
        }
        assertEquals(1, orpheus.playback.played.size());
        readAloud.close();
    }

    @Test
    public void diagnosticLinesNameChunkModelVoiceAndWorkerButNoText() {
        ReadAloudService.ChunkTrace[] seen = new ReadAloudService.ChunkTrace[1];
        Speaker speaker = new Speaker("orpheus-tts-3b-de", null);
        ReadAloudService service = new ReadAloudService(speaker.port, "orpheus-tts-3b-de", "Thomas",
                speaker.playback, SpeechChunking.PARAGRAPHS, 2, null);
        final List<String> lines = new CopyOnWriteArrayList<String>();
        service.speak("Geheimer Absatztext.", new ReadAloudService.Listener() {
            @Override
            public void failed(String message) {
            }

            @Override
            public void chunkRequested(ReadAloudService.ChunkTrace trace) {
                lines.add(ReadAloudBinding.requestLine(trace, "catalog=kipitz"));
            }

            @Override
            public void chunkSynthesized(ReadAloudService.ChunkTrace trace, ReadAloudService.WavFormat wav) {
                lines.add(ReadAloudBinding.audioLine(trace, wav));
            }
        });
        assertEquals(2, lines.size());
        String request = lines.get(0);
        assertTrue(request, request.matches("TTS request: generation=\\d+ chunk=1/1 catalog=kipitz "
                + "model=orpheus-tts-3b-de voice=Thomas worker=enterprise-ai-tts-worker-\\d+-1 chars=\\d+"));
        for (String line : lines) {
            assertTrue(line, !line.contains("Geheim"));
        }
    }

    /** Ein TTS-Modell mit Fake-Port und Fake-Wiedergabe; ab dem zweiten Absatz wartet der Port auf {@code release}. */
    private static final class Speaker {

        final CountDownLatch firstPlayed = new CountDownLatch(1);
        final Port port;
        final Playback playback;
        private final String model;

        Speaker(String model, CountDownLatch release) {
            this.model = model;
            this.port = new Port(release);
            this.playback = new Playback(firstPlayed);
        }

        ReadAloudBinding binding() {
            return ReadAloudBinding.available(new ReadAloudService(port, model, playback, SpeechChunking.PARAGRAPHS,
                    2), "Stimme: " + model, false, "catalog=kipitz");
        }
    }

    private static final class Port implements SpeechSynthesisPort {

        final List<String> models = new CopyOnWriteArrayList<String>();
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
            models.add(modelId);
            if (release != null && !text.startsWith("Erster")) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new SynthesizedSpeech((modelId + ":" + text).getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class Playback implements AudioPlayback {

        final List<String> played = new CopyOnWriteArrayList<String>();
        private final CountDownLatch firstPlayed;
        volatile int stops;

        Playback(CountDownLatch firstPlayed) {
            this.firstPlayed = firstPlayed;
        }

        @Override
        public void play(byte[] wav) {
            played.add(new String(wav, StandardCharsets.UTF_8));
            firstPlayed.countDown();
        }

        boolean awaitPlays(int count) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 5000;
            while (played.size() < count && System.currentTimeMillis() < deadline) {
                Thread.sleep(10);
            }
            return played.size() >= count;
        }

        @Override
        public void finish() {
        }

        @Override
        public void stop() {
            stops++;
        }
    }
}
