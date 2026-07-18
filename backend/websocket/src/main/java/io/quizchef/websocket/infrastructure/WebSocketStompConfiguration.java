package io.quizchef.websocket.infrastructure;

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
 *   <li>Clients connect at {@code /ws} (SockJS fallback for older browsers).</li>
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

    public WebSocketStompConfiguration(StompDestinationValidationInterceptor destinationValidationInterceptor) {
        this.destinationValidationInterceptor = destinationValidationInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(STOMP_ENDPOINT).withSockJS();
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
