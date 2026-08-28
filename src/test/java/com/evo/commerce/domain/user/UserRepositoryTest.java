package com.evo.commerce.domain.user;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    @Test
    void 이메일로_저장된_사용자를_조회한다() throws Exception {
        User user = new User(null, "tester@evo-commerce.com", "plain1234!", "테스터", UserRole.USER, null, null);

        User saved = userRepository.save(user);
        log.info("회원가입 저장 완료: {}", saved);

        User found = userRepository.findByEmail("tester@evo-commerce.com").orElseThrow();

        assertThat(found.getEmail()).isEqualTo("tester@evo-commerce.com");
        assertThat(found.getRole()).isEqualTo(UserRole.USER);
    }

    @Test
    void 이메일_존재_여부를_확인한다() throws Exception {
        userRepository.save(new User(null, "exists@evo-commerce.com", "plain1234!", "테스터", UserRole.USER, null, null));

        boolean exists = userRepository.existsByEmail("exists@evo-commerce.com");

        assertThat(exists).isTrue();
    }
}
