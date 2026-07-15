package io.quizchef.quiz.domain;

import jakarta.persistence.Embeddable;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A BCP-47 language tag, normalized to canonical case.
 *
 * <p>The domain never handles raw language strings — every language is a
 * LanguageCode with value equality. The accepted shape is the pragmatic
 * BCP-47 subset that covers real authoring needs (language, optional
 * script, optional region — {@code en}, {@code kn}, {@code en-IN},
 * {@code zh-Hant}); extension subtags can join when a real need appears.
 */
@Embeddable
public record LanguageCode(String value) {

    private static final Pattern FORMAT = Pattern.compile(
            "(?<language>[a-zA-Z]{2,3})"
                    + "(-(?<script>[a-zA-Z]{4}))?"
                    + "(-(?<region>[a-zA-Z]{2}|[0-9]{3}))?");

    public LanguageCode {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("language code must not be blank");
        }
        Matcher matcher = FORMAT.matcher(value.strip());
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "language code must be a BCP-47 tag such as 'en', 'kn' or 'en-IN': " + value);
        }
        value = normalize(matcher);
    }

    public static LanguageCode of(String value) {
        return new LanguageCode(value);
    }

    /**
     * Canonical BCP-47 case: lowercase language, titlecase script,
     * uppercase region.
     */
    private static String normalize(Matcher matcher) {
        StringBuilder tag = new StringBuilder(matcher.group("language").toLowerCase(Locale.ROOT));
        String script = matcher.group("script");
        if (script != null) {
            tag.append('-')
                    .append(Character.toUpperCase(script.charAt(0)))
                    .append(script.substring(1).toLowerCase(Locale.ROOT));
        }
        String region = matcher.group("region");
        if (region != null) {
            tag.append('-').append(region.toUpperCase(Locale.ROOT));
        }
        return tag.toString();
    }
}
