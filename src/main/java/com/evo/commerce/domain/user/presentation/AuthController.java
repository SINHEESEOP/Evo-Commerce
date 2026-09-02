package com.evo.commerce.domain.user.presentation;

import com.evo.commerce.domain.user.application.AuthService;
import com.evo.commerce.domain.user.dto.LoginRequest;
import com.evo.commerce.domain.user.dto.LoginResponse;
import com.evo.commerce.domain.user.dto.SignUpRequest;
import com.evo.commerce.domain.user.dto.UserResponse;
import com.evo.commerce.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ApiResponse<UserResponse> signUp(@Valid @RequestBody SignUpRequest request) {
        return ApiResponse.success(authService.signUp(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }
}
