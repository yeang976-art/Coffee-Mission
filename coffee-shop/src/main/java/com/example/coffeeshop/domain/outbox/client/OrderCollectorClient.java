package com.example.coffeeshop.domain.outbox.client;

import com.example.coffeeshop.domain.outbox.config.OutboxProperties;
import com.example.coffeeshop.domain.outbox.dto.OrderTransmission;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@ConditionalOnProperty(name = "outbox.enabled", havingValue = "true")
public class OrderCollectorClient {
    private final RestClient client;
    private final String url;

    public OrderCollectorClient(OutboxProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.requestTimeoutMillis());
        factory.setReadTimeout(properties.requestTimeoutMillis());
        client = RestClient.builder().requestFactory(factory).build();
        url = properties.url();
    }

    public void send(OrderTransmission payload) {
        client.post().uri(url).contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", payload.eventId().toString())
                .body(payload).retrieve().toBodilessEntity();
    }
}
