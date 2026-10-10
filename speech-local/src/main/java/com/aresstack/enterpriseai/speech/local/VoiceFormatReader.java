package com.aresstack.enterpriseai.speech.local;

import java.nio.file.Path;

/** Erkennt ein Stimmenformat in einem Ordner unter dem Modellverzeichnis. */
interface VoiceFormatReader {

    /** @return die Stimme, oder {@code null}, wenn der Ordner dieses Format nicht enthält (oder nicht sprechbar ist) */
    LocalVoice read(Path directory);
}
