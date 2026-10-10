package com.aresstack.enterpriseai.localruntime.speech;

import java.nio.file.Path;

/**
 * One locally installed text-to-speech voice, independent of its file format and of the backend that runs it.
 * The virtual name is the directory name below the model root, exactly what {@code /api/tags} lists and
 * {@code /v1/audio/speech} expects as {@code model}.
 *
 * @param virtualName directory name below the model root
 * @param family      voice format family ({@code piper} or {@code vits})
 * @param directory   the voice directory
 * @param modelFile   the graph file the runtime loads
 * @param sampleRate  output sample rate in Hz
 * @param encoder     text to the token ids the graph expects
 * @param parameters  VITS sampling parameters and speaker
 */
public record LocalVoice(String virtualName, String family, Path directory, Path modelFile, int sampleRate,
                         VoiceTextEncoder encoder, VoiceParameters parameters) {
}
