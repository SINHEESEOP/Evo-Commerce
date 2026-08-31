package com.evo.commerce.domain.order;

import jakarta.persistence.Embeddable;

@Embeddable
public record ProductSnapshot(String productName, int unitPrice) {
}
