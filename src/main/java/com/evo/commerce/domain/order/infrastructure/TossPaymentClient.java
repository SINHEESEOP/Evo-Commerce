package com.evo.commerce.domain.order.infrastructure;

import com.evo.commerce.domain.order.dto.TossPaymentConfirmResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.Map;

@Component
public class TossPaymentClient {

    private static final String ORDER_ID_PREFIX = "ORDER-";

    private final RestClient restClient;
    private final String secretKey;

    public TossPaymentClient(@Value("${toss.secret-key}") String secretKey) {
        this.secretKey = secretKey;
        this.restClient = RestClient.create("https://api.tosspayments.com");
    }

    public TossPaymentConfirmResponse confirm(String paymentKey, Long orderId, int amount) {
        String encodedAuth = Base64.getEncoder().encodeToString((secretKey + ":").getBytes());

        return restClient.post()
                .uri("/v1/payments/confirm")
                .header("Authorization", "Basic " + encodedAuth)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "paymentKey", paymentKey,
                        "orderId", toTossOrderId(orderId),
                        "amount", amount
                ))
                .retrieve()
                .body(TossPaymentConfirmResponse.class);
    }

    public static String toTossOrderId(Long orderId) {
        return ORDER_ID_PREFIX + orderId;
    }

    public static Long parseOrderId(String tossOrderId) {
        return Long.valueOf(tossOrderId.replaceFirst("^" + ORDER_ID_PREFIX, ""));
    }
}
