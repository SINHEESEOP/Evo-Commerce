package com.evo.commerce.domain.order.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

@Component
public class TossPaymentClient {

    private final RestClient restClient;
    private final String secretKey;

    public TossPaymentClient(@Value("${toss.secret-key}") String secretKey) {
        this.secretKey = secretKey;
        this.restClient = RestClient.create("https://api.tosspayments.com");
    }

    public void confirm(String paymentKey, Long orderId, int amount) {
        String encodedAuth = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());

        restClient.post()
                .uri("/v1/payments/confirm")
                .header("Authorization", "Basic " + encodedAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "paymentKey", paymentKey,
                        "orderId", String.valueOf(orderId),
                        "amount", amount
                ))
                .retrieve()
                .toBodilessEntity();
    }
}
