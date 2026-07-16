package io.quizchef.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.quizchef.websocket.api.ProtocolMessage;
import io.quizchef.websocket.api.ProtocolMessageType;
import io.quizchef.websocket.api.ProtocolVersion;
import io.quizchef.websocket.api.event.ParticipantPayload;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProtocolMessageSerializationTest {

    // Mirrors Spring Boot's Jackson defaults: ISO-8601 instants, not epoch
    // numbers (CODING_STANDARDS: dates are ISO-8601).
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();

    @Test
    void serializesTheStableWireVocabularyNotClassNames() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID participantId = UUID.randomUUID();
        ProtocolMessage message = ProtocolMessage.of(sessionId,
                ProtocolMessageType.PARTICIPANT_RECONNECTED,
                Instant.parse("2026-07-16T10:00:00Z"), new ParticipantPayload(participantId));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

        assertThat(json.get("type").asText()).isEqualTo("participant.reconnected");
        assertThat(json.get("protocolVersion").asInt()).isEqualTo(ProtocolVersion.CURRENT);
        assertThat(json.get("sessionId").asText()).isEqualTo(sessionId.toString());
        assertThat(json.get("messageId").isNull()).isFalse();
        assertThat(json.get("occurredAt").asText()).startsWith("2026-07-16T10:00:00");
        assertThat(json.get("payload").get("participantId").asText()).isEqualTo(participantId.toString());
    }

    @Test
    void everyMessageTypeHasADottedLowercaseWireName() {
        for (ProtocolMessageType type : ProtocolMessageType.values()) {
            assertThat(type.wireName())
                    .matches("[a-z]+\\.[a-z]+")
                    .as("wire name for %s", type);
        }
    }

    @Test
    void envelopeOnlyMessagesSerializeWithoutAPayload() throws Exception {
        ProtocolMessage message = ProtocolMessage.of(UUID.randomUUID(),
                ProtocolMessageType.LOBBY_OPENED, Instant.parse("2026-07-16T10:00:00Z"));

        JsonNode json = objectMapper.readTree(objectMapper.writeValueAsString(message));

        assertThat(json.get("type").asText()).isEqualTo("lobby.opened");
        assertThat(json.get("payload").isNull()).isTrue();
    }
}
