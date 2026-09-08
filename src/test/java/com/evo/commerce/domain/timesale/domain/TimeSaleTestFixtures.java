package com.evo.commerce.domain.timesale.domain;

import com.evo.commerce.domain.product.domain.Product;

import java.time.LocalDateTime;

public final class TimeSaleTestFixtures {

    private TimeSaleTestFixtures() {
    }

    public static Product newProduct() {
        return Product.builder()
                .name("무선 이어폰")
                .price(89000)
                .stock(50)
                .build();
    }

    public static TimeSaleEvent newEvent(Product product, LocalDateTime startAt, LocalDateTime endAt) {
        return TimeSaleEvent.builder()
                .product(product)
                .discountPrice(59000)
                .participantLimit(100)
                .startAt(startAt)
                .endAt(endAt)
                .build();
    }
}
