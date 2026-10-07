package com.aresstack.enterpriseai.domain.knowledge;

/** Siehe {@link KnowledgeTokenCounter#wordsAndSymbols()}. Ohne Regex, um lange Texte linear zu zählen. */
final class WordAndSymbolTokenCounter implements KnowledgeTokenCounter {

    @Override
    public int count(String text) {
        if (text == null) {
            return 0;
        }
        int tokens = 0;
        boolean inWord = false;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            i += Character.charCount(codePoint);
            if (Character.isLetterOrDigit(codePoint) || isWordJoiner(codePoint)) {
                if (!inWord) {
                    tokens++;
                    inWord = true;
                }
            } else {
                inWord = false;
                if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint)) {
                    tokens++;
                }
            }
        }
        return tokens;
    }

    /** Unterstrich und kombinierende Zeichen (z. B. nicht normalisierte Umlaute) gehören zum Wort. */
    private static boolean isWordJoiner(int codePoint) {
        int type = Character.getType(codePoint);
        return codePoint == '_' || type == Character.NON_SPACING_MARK || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }
}
