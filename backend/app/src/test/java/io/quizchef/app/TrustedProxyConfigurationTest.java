package io.quizchef.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.io.IOException;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

/**
 * Reads the trusted-proxy pattern out of {@code application-prod.yml} as
 * deployed and checks it actually behaves.
 *
 * <p>Not a copy of the regex in a test — the copy is what hides the bug.
 * This value passes through YAML, where a plain scalar keeps backslashes
 * literal, so a pattern that reads correctly in the file can arrive at
 * Tomcat as something else entirely. It did: an earlier revision of this
 * config shipped {@code 10\\.} (a literal backslash followed by any
 * character), which matches no address at all. Nothing would have failed —
 * Tomcat would simply have trusted no proxy, ignored the forwarded chain,
 * and keyed every caller in the world on the one shared bucket.
 *
 * <p>That is the quiet direction of this failure and the reason it needs a
 * test: it fails closed, so it produces no error, no warning, and no
 * spoofing bypass — just rate limiting that silently stops being
 * per-client.
 */
class TrustedProxyConfigurationTest {

    private static final String PLACEHOLDER_PREFIX = "${TRUSTED_PROXY_REGEX:";

    @Test
    void theShippedProxyPatternTrustsPrivateHopsAndNobodyElse() throws IOException {
        Pattern trusted = Pattern.compile(shippedInternalProxies());

        // Where a platform's edge proxy reaches a container from.
        assertThat(trusted.matcher("10.1.2.3").matches()).isTrue();
        assertThat(trusted.matcher("172.20.0.5").matches()).isTrue();
        assertThat(trusted.matcher("192.168.1.7").matches()).isTrue();
        assertThat(trusted.matcher("127.0.0.1").matches()).isTrue();

        // A caller arriving from anywhere on the internet is a client, never
        // a hop to be believed about who the client is.
        assertThat(trusted.matcher("203.0.113.7").matches()).isFalse();
        assertThat(trusted.matcher("8.8.8.8").matches()).isFalse();
        assertThat(trusted.matcher("198.51.100.42").matches()).isFalse();

        // 172.32 is outside the private range and must not be swept in by a
        // loose digit class — the classic off-by-one in this particular regex.
        assertThat(trusted.matcher("172.32.0.1").matches()).isFalse();
    }

    @Test
    void theShippedPatternSurvivesTheSafetyCheckItWillFace() {
        // The prod-profile startup check refuses catch-alls; the shipped
        // default must pass its own gate, or the first prod boot fails.
        assertThatCode(() -> {
            Pattern pattern = Pattern.compile(shippedInternalProxies());
            assertThat(pattern.matcher("203.0.113.7").matches()).isFalse();
        }).doesNotThrowAnyException();
    }

    /** The default baked into the placeholder, as Tomcat would receive it. */
    private static String shippedInternalProxies() throws IOException {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application-prod.yml"));
        Properties properties = yaml.getObject();
        assertThat(properties).isNotNull();

        String configured = properties.getProperty("server.tomcat.remoteip.internal-proxies");
        assertThat(configured)
                .as("production must configure which peers count as proxies")
                .isNotNull()
                .startsWith(PLACEHOLDER_PREFIX)
                .endsWith("}");
        return configured.substring(PLACEHOLDER_PREFIX.length(), configured.length() - 1);
    }
}
