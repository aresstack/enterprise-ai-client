package com.aresstack.enterpriseai.application.passage;

import com.aresstack.enterpriseai.domain.knowledge.SentenceSplitter;

import java.util.List;

/**
 * Satzgrenzen für die Passagenbildung (askai arch {@code SentenceSegmentationPort}): produktiv das OpenNLP-Satzmodell
 * der Sprache, sonst der regelbasierte {@link SentenceSplitter}. {@link #id()} geht in den Fingerabdruck der
 * Zerlegung ein: ein anderes Satzmodell ergibt andere Passagen, der Index baut dann neu.
 */
public interface SentenceSegmenter {

    /** Die Sätze eines Absatzes in Lesereihenfolge (nie {@code null}). */
    List<String> sentences(String paragraph);

    /** Kennung des Verfahrens, z. B. {@code opennlp-de} oder {@code splitter}. */
    String id();

    /** Der regelbasierte Splitter der Domäne (ohne Modell). */
    static SentenceSegmenter splitter() {
        final SentenceSplitter splitter = new SentenceSplitter();
        return new SentenceSegmenter() {
            @Override
            public List<String> sentences(String paragraph) {
                return splitter.split(paragraph);
            }

            @Override
            public String id() {
                return "splitter";
            }
        };
    }
}
