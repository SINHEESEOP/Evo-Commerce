package com.evo.commerce.global.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Bean
    @ConfigurationProperties("spring.datasource.master")
    public DataSourceProperties masterDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource masterDataSource(@Qualifier("masterDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    @ConfigurationProperties("spring.datasource.slave")
    public DataSourceProperties slaveDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    public DataSource slaveDataSource(@Qualifier("slaveDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Primary
    @Bean
    public DataSource dataSource(DataSource masterDataSource, DataSource slaveDataSource) {
        RoutingDataSource routingDataSource = new RoutingDataSource();

        Map<Object, Object> dataSourceMap = new HashMap<>();
        dataSourceMap.put(DataSourceType.MASTER, masterDataSource);
        dataSourceMap.put(DataSourceType.SLAVE, slaveDataSource);

        routingDataSource.setTargetDataSources(dataSourceMap);
        routingDataSource.setDefaultTargetDataSource(masterDataSource);
        // RoutingDataSource 자체는 더 이상 빈으로 직접 반환되지 않으므로, afterPropertiesSet()을
        // 스프링이 대신 호출해주지 않는다 — targetDataSources를 실제로 사용하려면 직접 호출해야 한다.
        routingDataSource.afterPropertiesSet();

        // JpaTransactionManager.doBegin()은 prepareSynchronization()보다 먼저 실행되어,
        // readOnly 플래그가 세팅되기 전에 커넥션을 요청한다. LazyConnectionDataSourceProxy로 감싸면
        // 실제 커넥션 획득이 첫 SQL 실행 시점까지 미뤄져, 그 시점엔 readOnly 플래그가 이미 반영되어 있다.
        return new LazyConnectionDataSourceProxy(routingDataSource);
    }
}
