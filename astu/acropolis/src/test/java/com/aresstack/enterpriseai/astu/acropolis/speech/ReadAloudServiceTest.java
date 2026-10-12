package com.aresstack.enterpriseai.astu.acropolis.speech;

import com.aresstack.enterpriseai.speech.api.SpeechSynthesisException;
import com.aresstack.enterpriseai.speech.api.SpeechSynthesisPort;
import com.aresstack.enterpriseai.speech.api.SynthesizedSpeech;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Ein Vorlesevorgang spricht jedes Stück mit dem einen Modell, das beim Start gewählt war, auch mit mehreren Workern. */
public class ReadAloudServiceTest {

    private static final String ORPHEUS = "orpheus-tts-3b-de";
    private static final String FIVE_PARAGRAPHS = "Erster Absatz mit etwas Text.\n\n"
            + "Zweiter Absatz, etwas länger als der erste.\n\n"
            + "Dritter Absatz.\n\n"
            + "Vierter Absatz mit **Fettdruck** im Markdown.\n\n"
            + "Fünfter und letzter Absatz.";

    @Test
    public void everyParagraphOfOneAnswerUsesTheModelAndVoiceSelectedAtStart() {
        RecordingPort port = new RecordingPort(20);
        RecordingPlayback playback = new RecordingPlayback();
        ReadAloudService service = new ReadAloudService(port, ORPHEUS, "Thomas", playback,
                SpeechChunking.PARAGRAPHS, 4, null);
        final List<ReadAloudService.ChunkTrace> requested = new CopyOnWriteArrayList<ReadAloudService.ChunkTrace>();
        final List<ReadAloudService.ChunkTrace> synthesized = new CopyOnWriteArrayList<ReadAloudService.ChunkTrace>();

        assertTrue(service.speak(FIVE_PARAGRAPHS, new ReadAloudService.Listener() {
            @Override
            public void failed(String message) {
                throw new AssertionError(message);
            }

            @Override
            public void chunkRequested(ReadAloudService.ChunkTrace trace) {
                requested.add(trace);
            }

            @Override
            public void chunkSynthesized(ReadAloudService.ChunkTrace trace, ReadAloudService.WavFormat wav) {
                synthesized.add(trace);
            }
        }));

        assertEquals(5, port.models.size());
        assertEquals(Collections.singleton(ORPHEUS), new HashSet<String>(port.models));
        assertEquals(Collections.singleton("Thomas"), new HashSet<String>(port.voices));
        assertTrue("mehrere Worker parallel: " + port.workers, port.workers.size() > 1);
        assertEquals(5, playback.played.size());
        for (String played : playback.played) {
            assertTrue(played, played.startsWith(ORPHEUS + ":"));
        }
        assertEquals(5, requested.size());
        assertEquals(5, synthesized.size());
        Set<Integer> indices = new HashSet<Integer>();
        Set<Long> generations = new HashSet<Long>();
        for (ReadAloudService.ChunkTrace trace : requested) {
            assertEquals(ORPHEUS, trace.modelId());
            assertEquals("Thomas", trace.voice());
            assertEquals(5, trace.count());
            assertTrue(trace.worker(), trace.worker().startsWith("enterprise-ai-tts-worker-" + trace.generation()));
            indices.add(trace.index());
            generations.add(trace.generation());
        }
        assertEquals(new HashSet<Integer>(java.util.Arrays.asList(1, 2, 3, 4, 5)), indices);
        assertEquals(1, generations.size());
    }

    @Test
    public void stoppedRunPlaysNothingFurtherAndRequestsNothingNew() throws Exception {
        final CountDownLatch firstPlayed = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        BlockingPort port = new BlockingPort(release);
        RecordingPlayback playback = new RecordingPlayback() {
            @Override
            public void play(byte[] wav) {
                super.play(wav);
                firstPlayed.countDown();
            }
        };
        final ReadAloudService service = new ReadAloudService(port, ORPHEUS, playback, SpeechChunking.PARAGRAPHS, 1);
        Thread reader = new Thread(new Runnable() {
            @Override
            public void run() {
                service.speak(FIVE_PARAGRAPHS, null);
            }
        });
        reader.start();
        assertTrue(firstPlayed.await(5, TimeUnit.SECONDS));
        service.stop();
        release.countDown();
        reader.join(5000);

        assertEquals(1, playback.played.size());
        assertEquals(1, playback.stops);
        assertTrue("höchstens das laufende Stück nach dem ersten: " + port.calls, port.calls.size() <= 2);
    }

    @Test
    public void wavFormatReadsTheRiffHeader() {
        byte[] wav = wav(24000, 1, 16);
        ReadAloudService.WavFormat format = ReadAloudService.WavFormat.of(wav);
        assertEquals(24000, format.sampleRate());
        assertEquals(1, format.channels());
        assertEquals(16, format.bitsPerSample());
        assertEquals(wav.length, format.bytes());
        assertEquals(0, ReadAloudService.WavFormat.of("kein wav".getBytes(StandardCharsets.UTF_8)).sampleRate());
    }

    static byte[] wav(int sampleRate, int channels, int bits) {
        byte[] data = new byte[48];
        put(data, 0, "RIFF");
        le(data, 4, 40, 4);
        put(data, 8, "WAVE");
        put(data, 12, "fmt ");
        le(data, 16, 16, 4);
        le(data, 20, 1, 2);
        le(data, 22, channels, 2);
        le(data, 24, sampleRate, 4);
        le(data, 28, sampleRate * channels * bits / 8, 4);
        le(data, 32, channels * bits / 8, 2);
        le(data, 34, bits, 2);
        put(data, 36, "data");
        le(data, 40, 4, 4);
        return data;
    }

    private static void put(byte[] data, int offset, String ascii) {
        for (int i = 0; i < ascii.length(); i++) {
            data[offset + i] = (byte) ascii.charAt(i);
        }
    }

    private static void le(byte[] data, int offset, int value, int length) {
        for (int i = 0; i < length; i++) {
            data[offset + i] = (byte) (value >>> (8 * i));
        }
    }

    /** Zeichnet Modell und Worker jedes Aufrufs auf; das „Audio“ nennt Modell und Text. */
    static final class RecordingPort implements SpeechSynthesisPort {

        final List<String> models = new CopyOnWriteArrayList<String>();
        final List<String> voices = new CopyOnWriteArrayList<String>();
        final Set<String> workers = Collections.synchronizedSet(new HashSet<String>());
        private final long delayMillis;

        RecordingPort(long delayMillis) {
            this.delayMillis = delayMillis;
        }

        @Override
        public String catalogId() {
            return "kipitz";
        }

        @Override
        public SynthesizedSpeech synthesize(String modelId, String voice, String text)
                throws SpeechSynthesisException {
            voices.add(String.valueOf(voice));
            return synthesize(modelId, text);
        }

        @Override
        public SynthesizedSpeech synthesize(String modelId, String text) throws SpeechSynthesisException {
            models.add(modelId);
            workers.add(Thread.currentThread().getName());
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new SynthesizedSpeech((modelId + ":" + text).getBytes(StandardCharsets.UTF_8));
        }
    }

    /** Das erste Stück kommt sofort, alle weiteren erst nach {@code release}. */
    static final class BlockingPort implements SpeechSynthesisPort {

        final List<String> calls = new CopyOnWriteArrayList<String>();
        private final CountDownLatch release;

        BlockingPort(CountDownLatch release) {
            this.release = release;
        }

        @Override
        public String catalogId() {
            return "kipitz";
        }

        @Override
        public SynthesizedSpeech synthesize(String modelId, String text) {
            calls.add(modelId);
            if (calls.size() > 1) {
                try {
                    release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return new SynthesizedSpeech((modelId + ":" + text).getBytes(StandardCharsets.UTF_8));
        }
    }

    static class RecordingPlayback implements AudioPlayback {

        final List<String> played = new CopyOnWriteArrayList<String>();
        volatile int stops;

        @Override
        public void play(byte[] wav) {
            played.add(new String(wav, StandardCharsets.UTF_8));
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
