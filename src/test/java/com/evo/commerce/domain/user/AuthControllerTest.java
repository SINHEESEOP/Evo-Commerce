package com.evo.commerce.domain.user;

import com.evo.commerce.domain.user.dto.LoginRequest;
import com.evo.commerce.domain.user.dto.LoginResponse;
import com.evo.commerce.domain.user.dto.SignUpRequest;
import com.evo.commerce.domain.user.dto.UserResponse;
import com.evo.commerce.global.auth.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    AuthService authService;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @Test
    void 회원가입에_성공하면_사용자_정보를_반환한다() throws Exception {
        SignUpRequest request = new SignUpRequest("tester@evo-commerce.com", "plain1234!", "테스터");
        UserResponse response = new UserResponse(1L, request.email(), request.name(), UserRole.USER);

        given(authService.signUp(request)).willReturn(response);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value(request.email()))
                .andExpect(jsonPath("$.data.name").value(request.name()));
    }

    @Test
    void 로그인에_성공하면_토큰을_반환한다() throws Exception {
        LoginRequest request = new LoginRequest("tester@evo-commerce.com", "plain1234!");
        UserResponse userResponse = new UserResponse(1L, request.email(), "테스터", UserRole.USER);
        LoginResponse response = new LoginResponse("issued-token", userResponse);

        given(authService.login(request)).willReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").value("issued-token"));
    }
}
