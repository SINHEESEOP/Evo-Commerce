package com.evo.commerce.domain.notification.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Transactional
    void deleteByUser_Id(Long userId);
}
