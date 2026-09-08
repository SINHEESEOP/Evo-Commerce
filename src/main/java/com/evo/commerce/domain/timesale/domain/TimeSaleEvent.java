package com.evo.commerce.domain.timesale.domain;

import com.evo.commerce.domain.product.domain.Product;
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
@Table(name = "time_sale_events")
@EntityListeners(AuditingEntityListener.class)
public class TimeSaleEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    private int discountPrice;

    private int participantLimit;

    private LocalDateTime startAt;

    private LocalDateTime endAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @Builder
    public TimeSaleEvent(Product product, int discountPrice, int participantLimit, LocalDateTime startAt, LocalDateTime endAt) {
        this.product = product;
        this.discountPrice = discountPrice;
        this.participantLimit = participantLimit;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TimeSaleEvent other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return "TimeSaleEvent(id=" + id + ", discountPrice=" + discountPrice + ", participantLimit=" + participantLimit + ")";
    }
}
