package com.yliu22520.iotota.knowledge;

import java.util.ArrayList;
import java.util.List;

/** Deterministic approximate-token windows for local Markdown knowledge. */
final class KnowledgeChunker {

    static final int MAX_TOKENS = 500;
    static final int OVERLAP_TOKENS = 50;

    List<String> split(String text) {
        List<String> tokens = tokenize(text.strip());
        if (tokens.size() <= MAX_TOKENS) {
            return text.isBlank() ? List.of() : List.of(text.strip());
        }
        List<String> windows = new ArrayList<>();
        int start = 0;
        while (start < tokens.size()) {
            int end = Math.min(tokens.size(), start + MAX_TOKENS);
            windows.add(String.join("", tokens.subList(start, end)).strip());
            if (end == tokens.size()) {
                break;
            }
            start = end - OVERLAP_TOKENS;
        }
        return List.copyOf(windows);
    }

    int approximateTokenCount(String text) {
        return tokenize(text).size();
    }

    private List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder latin = new StringBuilder();
        text.codePoints().forEach(codePoint -> {
            if (Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN) {
                flush(latin, tokens);
                tokens.add(new String(Character.toChars(codePoint)));
            } else if (Character.isLetterOrDigit(codePoint)) {
                latin.appendCodePoint(codePoint);
            } else {
                flush(latin, tokens);
                if (!Character.isWhitespace(codePoint)) {
                    tokens.add(new String(Character.toChars(codePoint)));
                } else if (!tokens.isEmpty()) {
                    tokens.add(" ");
                }
            }
        });
        flush(latin, tokens);
        return tokens;
    }

    private void flush(StringBuilder token, List<String> tokens) {
        if (!token.isEmpty()) {
            tokens.add(token.toString());
            token.setLength(0);
        }
    }
}
