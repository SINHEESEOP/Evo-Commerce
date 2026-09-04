package com.evo.commerce.domain.payment.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    @Transactional
    void deleteByOrder_Id(Long orderId);
}
