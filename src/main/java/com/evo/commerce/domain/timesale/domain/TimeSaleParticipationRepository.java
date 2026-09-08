package com.evo.commerce.domain.timesale.domain;

import com.evo.commerce.domain.user.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeSaleParticipationRepository extends JpaRepository<TimeSaleParticipation, Long> {

    long countByTimeSaleEvent(TimeSaleEvent timeSaleEvent);

    boolean existsByTimeSaleEventAndUser(TimeSaleEvent timeSaleEvent, User user);
}
