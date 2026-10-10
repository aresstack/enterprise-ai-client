package com.aresstack.enterpriseai.localruntime.speech;

/**
 * The inference backend that turns a voice's token ids into audio. The HTTP layer, the voice store and the
 * voice formats only know this interface; which engine runs the graph is a detail of the implementation.
 * {@link OnnxSpeechRuntime} is the pilot backend (ONNX Runtime, CPU); a later in-house inference engine replaces
 * it here without touching voices, server or the client.
 */
public interface LocalSpeechRuntime extends AutoCloseable {

    /** Short name of the backend for logs and {@code /api/tags} ({@code details.runtime}). */
    String name();

    /**
     * @return a mono 16-bit PCM WAV file with the spoken text
     * @throws IllegalArgumentException when the text yields nothing the voice can speak
     * @throws SpeechRuntimeException   when the backend cannot load or run the voice
     */
    byte[] synthesizeWav(LocalVoice voice, String text) throws SpeechRuntimeException;

    @Override
    void close();
}
