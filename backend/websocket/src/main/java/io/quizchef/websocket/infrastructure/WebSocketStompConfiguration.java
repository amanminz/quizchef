package io.quizchef.websocket.infrastructure;

import io.quizchef.security.infrastructure.CorsProperties;
import io.quizchef.websocket.api.Topics;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP-over-WebSocket wiring — endpoint, broker, and destination prefixes.
 * Configuration only; no business logic, no message handling.
 *
 * <ul>
 *   <li>Clients connect at {@code /ws} (SockJS fallback for older browsers).
 *       The handshake honors the same cross-origin allowlist as REST CORS
 *       ({@link CorsProperties}) — Spring's WebSocket/SockJS handshake
 *       enforces same-origin by default, which silently breaks the
 *       split-domain production topology (RFC-008: frontend and backend on
 *       separate domains) while local same-origin dev keeps working.</li>
 *   <li>Outbound broadcasts flow through a simple in-memory broker on the
 *       {@link Topics#BROKER_PREFIX} prefix — no external broker (ADR-005:
 *       reactive without Kafka/RabbitMQ).</li>
 *   <li>Inbound client sends are prefixed {@code /app} — reserved for the
 *       command handlers that arrive with Session APIs; none exist yet.</li>
 * </ul>
 *
 * <p>The simple broker suits single-instance church-scale deployments; a
 * relay to an external broker is a deployment change (RFC-008), not a code
 * one.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketStompConfiguration implements WebSocketMessageBrokerConfigurer {

    static final String STOMP_ENDPOINT = "/ws";
    static final String APPLICATION_PREFIX = "/app";

    private final StompDestinationValidationInterceptor destinationValidationInterceptor;
    private final CorsProperties corsProperties;

    public WebSocketStompConfiguration(StompDestinationValidationInterceptor destinationValidationInterceptor,
                                       CorsProperties corsProperties) {
        this.destinationValidationInterceptor = destinationValidationInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(STOMP_ENDPOINT)
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(Topics.BROKER_PREFIX);
        registry.setApplicationDestinationPrefixes(APPLICATION_PREFIX);
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(destinationValidationInterceptor);
    }
}
