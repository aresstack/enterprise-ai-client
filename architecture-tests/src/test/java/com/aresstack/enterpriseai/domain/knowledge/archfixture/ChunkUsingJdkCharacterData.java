package com.aresstack.enterpriseai.domain.knowledge.archfixture;

/**
 * Absichtlicher Verstoß: eine Wissensklasse klassifiziert Zeichen über die Unicode-Daten des JDK statt über
 * {@code UnicodeClasses} (KnowledgeBoundaryTest).
 */
public final class ChunkUsingJdkCharacterData {

    public boolean startsWord(int codePoint) {
        return Character.isLetter(codePoint) || Character.getType(codePoint) == Character.NON_SPACING_MARK;
    }
}
