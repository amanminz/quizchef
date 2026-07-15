package io.quizchef.quiz.domain;

import jakarta.persistence.Embeddable;

/**
 * A scripture reference attached to a question.
 *
 * @param verseEnd    optional; null means a single verse
 * @param translation optional (for example "ESV"); null means unspecified
 */
@Embeddable
public record BibleReference(
        String book,
        int chapter,
        int verseStart,
        Integer verseEnd,
        String translation
) {

    public BibleReference {
        if (book == null || book.isBlank()) {
            throw new IllegalArgumentException("book must not be blank");
        }
        book = book.strip();
        if (chapter < 1) {
            throw new IllegalArgumentException("chapter must be positive");
        }
        if (verseStart < 1) {
            throw new IllegalArgumentException("verseStart must be positive");
        }
        if (verseEnd != null && verseEnd < verseStart) {
            throw new IllegalArgumentException("verseEnd must not precede verseStart");
        }
        if (translation != null && translation.isBlank()) {
            translation = null;
        }
    }

    public static BibleReference verse(String book, int chapter, int verse) {
        return new BibleReference(book, chapter, verse, null, null);
    }

    public static BibleReference range(String book, int chapter, int verseStart, int verseEnd) {
        return new BibleReference(book, chapter, verseStart, verseEnd, null);
    }
}
