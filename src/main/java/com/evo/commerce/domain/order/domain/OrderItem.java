package com.evo.commerce.domain.order.domain;

import com.evo.commerce.domain.product.domain.Product;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Embedded
    private ProductSnapshot productSnapshot;

    private int quantity;

    @Builder
    public OrderItem(Product product, int quantity) {
        this.product = product;
        this.quantity = quantity;
        this.productSnapshot = new ProductSnapshot(product.getName(), product.getPrice());
    }

    void assignOrder(Order order) {
        this.order = order;
    }
}
