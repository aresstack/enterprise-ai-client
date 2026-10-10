package com.aresstack.enterpriseai.localruntime.speech;

import java.nio.file.Path;

/** Recognises one on-disk voice format in a directory below the model root. */
interface VoiceFormatReader {

    /** @return the voice, or {@code null} when the directory does not hold this format (or cannot be spoken) */
    LocalVoice read(Path directory);
}
