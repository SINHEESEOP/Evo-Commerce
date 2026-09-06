package com.evo.commerce.global.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;

class RoutingDataSourceTest {

    private final RoutingDataSource routingDataSource = new RoutingDataSource();

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    @Test
    void 읽기_전용_트랜잭션이면_슬레이브_데이터소스로_라우팅된다() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        Object lookupKey = routingDataSource.determineCurrentLookupKey();

        assertThat(lookupKey).isEqualTo(DataSourceType.SLAVE);
    }

    @Test
    void 쓰기_트랜잭션이면_마스터_데이터소스로_라우팅된다() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);

        Object lookupKey = routingDataSource.determineCurrentLookupKey();

        assertThat(lookupKey).isEqualTo(DataSourceType.MASTER);
    }
}
