package com.evo.commerce.domain.payment.domain;

import com.evo.commerce.domain.order.domain.Order;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "payments")
@EntityListeners(AuditingEntityListener.class)
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    @Column(unique = true)
    private String paymentKey;

    private String method;

    private int amount;

    private LocalDateTime approvedAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @Builder
    public Payment(Order order, String paymentKey, String method, int amount, LocalDateTime approvedAt) {
        this.order = order;
        this.paymentKey = paymentKey;
        this.method = method;
        this.amount = amount;
        this.approvedAt = approvedAt;
    }
}
