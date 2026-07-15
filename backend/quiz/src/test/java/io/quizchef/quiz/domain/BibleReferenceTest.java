package io.quizchef.quiz.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class BibleReferenceTest {

    @Test
    void shouldCreateSingleVerseReference() {
        BibleReference reference = BibleReference.verse("John", 3, 16);

        assertThat(reference.book()).isEqualTo("John");
        assertThat(reference.chapter()).isEqualTo(3);
        assertThat(reference.verseStart()).isEqualTo(16);
        assertThat(reference.verseEnd()).isNull();
        assertThat(reference.translation()).isNull();
    }

    @Test
    void shouldCreateVerseRangeWithTranslation() {
        BibleReference reference = new BibleReference("Exodus", 3, 1, 10, "ESV");

        assertThat(reference.verseEnd()).isEqualTo(10);
        assertThat(reference.translation()).isEqualTo("ESV");
    }

    @Test
    void shouldRejectRangeEndingBeforeItStarts() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> BibleReference.range("Exodus", 3, 10, 5));
    }

    @Test
    void shouldRejectInvalidBookChapterOrVerse() {
        assertThatIllegalArgumentException().isThrownBy(() -> BibleReference.verse("  ", 3, 16));
        assertThatIllegalArgumentException().isThrownBy(() -> BibleReference.verse("John", 0, 16));
        assertThatIllegalArgumentException().isThrownBy(() -> BibleReference.verse("John", 3, 0));
    }

    @Test
    void shouldTreatBlankTranslationAsUnspecified() {
        assertThat(new BibleReference("John", 3, 16, null, "  ").translation()).isNull();
    }
}
