package com.evo.commerce.domain.user;

import com.evo.commerce.domain.user.dto.LoginRequest;
import com.evo.commerce.domain.user.dto.LoginResponse;
import com.evo.commerce.domain.user.dto.SignUpRequest;
import com.evo.commerce.domain.user.dto.UserResponse;
import com.evo.commerce.global.auth.JwtTokenProvider;
import com.evo.commerce.global.exception.AuthErrorCode;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.UserErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public UserResponse signUp(SignUpRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(UserErrorCode.DUPLICATE_EMAIL);
        }

        User user = User.builder()
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .name(request.name())
                .role(UserRole.USER)
                .build();

        User saved = userRepository.save(user);
        return UserMapper.toResponse(saved);
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new BusinessException(AuthErrorCode.LOGIN_FAILED);
        }

        String token = jwtTokenProvider.createToken(user.getId(), user.getRole());
        return new LoginResponse(token, UserMapper.toResponse(user));
    }
}
