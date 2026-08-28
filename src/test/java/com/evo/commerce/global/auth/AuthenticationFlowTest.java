package com.evo.commerce.global.auth;

import com.evo.commerce.domain.user.UserRole;
import com.evo.commerce.global.auth.support.ProtectedSampleController;
import com.evo.commerce.global.config.WebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProtectedSampleController.class)
@Import({JwtAuthenticationFilter.class, AuthInterceptor.class, WebMvcConfig.class, JwtTokenProvider.class})
class AuthenticationFlowTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @Test
    void 유효한_토큰으로_요청하면_보호된_API에_접근할_수_있다() throws Exception {
        String token = jwtTokenProvider.createToken(1L, UserRole.USER);

        mockMvc.perform(get("/api/sample/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));
    }

    @Test
    void 토큰_없이_요청하면_보호된_API에_접근할_수_없다() throws Exception {
        mockMvc.perform(get("/api/sample/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }
}
