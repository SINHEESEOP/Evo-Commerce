package com.evo.commerce.global.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class RoutingDataSourceIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void 읽기_전용_트랜잭션은_실제로_read_only_슬레이브에_연결된다() {
        TransactionTemplate readOnlyTransaction = new TransactionTemplate(transactionManager);
        readOnlyTransaction.setReadOnly(true);

        Integer readOnlyFlag = readOnlyTransaction.execute(status ->
                new JdbcTemplate(dataSource).queryForObject("SELECT @@read_only", Integer.class));

        assertThat(readOnlyFlag).isEqualTo(1);
    }

    @Test
    void 쓰기_트랜잭션은_실제로_read_only가_아닌_마스터에_연결된다() {
        TransactionTemplate writeTransaction = new TransactionTemplate(transactionManager);
        writeTransaction.setReadOnly(false);

        Integer readOnlyFlag = writeTransaction.execute(status ->
                new JdbcTemplate(dataSource).queryForObject("SELECT @@read_only", Integer.class));

        assertThat(readOnlyFlag).isEqualTo(0);
    }
}
