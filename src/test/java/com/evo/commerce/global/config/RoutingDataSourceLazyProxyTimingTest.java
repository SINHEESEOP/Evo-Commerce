package com.evo.commerce.global.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoutingDataSourceLazyProxyTimingTest {

    @Mock
    private DataSource masterDataSource;
    @Mock
    private DataSource slaveDataSource;
    @Mock
    private Connection masterConnection;
    @Mock
    private Connection slaveConnection;

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
    }

    private RoutingDataSource buildRoutingDataSource() {
        RoutingDataSource routingDataSource = new RoutingDataSource();
        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put(DataSourceType.MASTER, masterDataSource);
        dataSourceMap.put(DataSourceType.SLAVE, slaveDataSource);
        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);
        routingDataSource.afterPropertiesSet();
        return routingDataSource;
    }

    @Test
    void LazyConnectionDataSourceProxy로_감싸면_readOnly_플래그가_세팅되기_전엔_실제_커넥션을_요청하지_않는다() throws SQLException {
        given(slaveDataSource.getConnection()).willReturn(slaveConnection);

        LazyConnectionDataSourceProxy lazyProxy = new LazyConnectionDataSourceProxy(buildRoutingDataSource());
        // defaultAutoCommit/defaultTransactionIsolation을 모르면 프록시가 최초 1회 진짜 커넥션을 열어
        // 드라이버 기본값을 확인하려 한다(checkDefaultConnectionProperties). 이 테스트가 검증하려는
        // "지연된 라우팅"과 무관한 부수 동작이므로, 값을 미리 알려줘서 그 동작 자체를 건너뛰게 한다.
        lazyProxy.setDefaultAutoCommit(true);
        lazyProxy.setDefaultTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        DataSource lazyDataSource = lazyProxy;

        // doBegin() 시점 흉내: readOnly 플래그가 아직 세팅되지 않은 상태에서 커넥션을 요청
        Connection lazyConnection = lazyDataSource.getConnection();
        lazyConnection.setAutoCommit(false); // Hibernate가 doBegin()에서 실제로 호출하는 메서드

        // 이 시점까지는 실제 master/slave 커넥션이 아직 요청되지 않아야 한다 (라우팅 판단이 지연됨)
        verify(masterDataSource, never()).getConnection();
        verify(slaveDataSource, never()).getConnection();

        // prepareSynchronization() 시점 흉내: readOnly 플래그가 이제 세팅됨
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        // 실제 SQL 실행 시점 흉내: 진짜 커넥션이 필요한 메서드를 처음 호출
        lazyConnection.prepareStatement("SELECT 1");

        // 이 시점에야 라우팅이 확정되고, readOnly가 true였으므로 slave가 선택된다
        verify(slaveDataSource, times(1)).getConnection();
        verify(masterDataSource, never()).getConnection();
    }

    @Test
    void LazyConnectionDataSourceProxy없이_라우팅데이터소스를_직접_쓰면_readOnly_플래그세팅_전에_라우팅이_확정돼_마스터로_고정된다() throws SQLException {
        given(masterDataSource.getConnection()).willReturn(masterConnection);
        RoutingDataSource routingDataSourceOnly = buildRoutingDataSource();

        // doBegin() 시점 흉내: readOnly 플래그가 아직 세팅되지 않은 상태에서 커넥션을 요청.
        // Lazy 프록시가 없으므로 이 호출 즉시 determineCurrentLookupKey()가 실행되어 라우팅이 확정된다.
        routingDataSourceOnly.getConnection();

        // prepareSynchronization() 시점 흉내: 이제서야 readOnly 플래그가 true로 세팅됨 - 이미 늦었다.
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);

        // 라우팅은 이미 첫 getConnection() 호출 시점(readOnly=false)에 끝났으므로 master로 고정된다.
        verify(masterDataSource, times(1)).getConnection();
        verify(slaveDataSource, never()).getConnection();
    }
}
