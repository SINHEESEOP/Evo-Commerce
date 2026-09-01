package com.evo.commerce.domain.order;

import com.evo.commerce.domain.order.dto.TossWebhookRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/webhooks/toss")
@RequiredArgsConstructor
public class TossWebhookController {

    private final OrderFacade orderFacade;

    @PostMapping
    public void handleWebhook(@RequestBody TossWebhookRequest request) {
        orderFacade.handlePaymentWebhook(request);
    }
}
