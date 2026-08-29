package com.evo.commerce.domain.user;

import com.evo.commerce.domain.user.dto.LoginRequest;
import com.evo.commerce.domain.user.dto.LoginResponse;
import com.evo.commerce.domain.user.dto.SignUpRequest;
import com.evo.commerce.domain.user.dto.UserResponse;
import com.evo.commerce.global.auth.JwtTokenProvider;
import com.evo.commerce.global.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    AuthService authService;

    @Test
    void 이메일이_중복되지_않으면_회원가입에_성공한다() throws Exception {
        SignUpRequest request = new SignUpRequest("tester@evo-commerce.com", "plain1234!", "테스터");

        given(userRepository.existsByEmail(request.email())).willReturn(false);
        given(passwordEncoder.encode(request.password())).willReturn("encoded-password");
        given(userRepository.save(org.mockito.ArgumentMatchers.any(User.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.signUp(request);

        assertThat(response.email()).isEqualTo(request.email());
        assertThat(response.name()).isEqualTo(request.name());
    }

    @Test
    void 이미_가입된_이메일로_가입하면_예외가_발생한다() throws Exception {
        SignUpRequest request = new SignUpRequest("exists@evo-commerce.com", "plain1234!", "테스터");

        given(userRepository.existsByEmail(request.email())).willReturn(true);

        assertThatThrownBy(() -> authService.signUp(request))
                .isInstanceOf(BusinessException.class);

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 이메일과_비밀번호가_일치하면_로그인에_성공한다() throws Exception {
        LoginRequest request = new LoginRequest("tester@evo-commerce.com", "plain1234!");
        User user = User.builder()
                .email(request.email())
                .password("encoded-password")
                .name("테스터")
                .role(UserRole.USER)
                .build();

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(true);
        given(jwtTokenProvider.createToken(user.getId(), user.getRole())).willReturn("issued-token");

        LoginResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("issued-token");
        assertThat(response.user().email()).isEqualTo(request.email());
    }

    @Test
    void 비밀번호가_일치하지_않으면_로그인에_실패한다() throws Exception {
        LoginRequest request = new LoginRequest("tester@evo-commerce.com", "wrong-password");
        User user = User.builder()
                .email(request.email())
                .password("encoded-password")
                .name("테스터")
                .role(UserRole.USER)
                .build();

        given(userRepository.findByEmail(request.email())).willReturn(Optional.of(user));
        given(passwordEncoder.matches(request.password(), user.getPassword())).willReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class);
    }
}
