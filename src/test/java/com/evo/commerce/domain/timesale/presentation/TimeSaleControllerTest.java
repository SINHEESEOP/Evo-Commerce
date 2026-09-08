package com.evo.commerce.domain.timesale.presentation;

import com.evo.commerce.domain.timesale.application.TimeSaleFacade;
import com.evo.commerce.domain.timesale.dto.TimeSaleEventCreateRequest;
import com.evo.commerce.domain.timesale.dto.TimeSaleEventResponse;
import com.evo.commerce.domain.user.domain.UserRole;
import com.evo.commerce.global.auth.AuthInterceptor;
import com.evo.commerce.global.auth.JwtAuthenticationFilter;
import com.evo.commerce.global.auth.JwtTokenProvider;
import com.evo.commerce.global.config.WebMvcConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = TimeSaleController.class)
@Import({JwtAuthenticationFilter.class, AuthInterceptor.class, WebMvcConfig.class, JwtTokenProvider.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-key-for-time-sale-controller-test-only")
class TimeSaleControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    TimeSaleFacade timeSaleFacade;

    @Test
    void MASTER_권한이_있으면_타임세일_이벤트를_등록할_수_있다() throws Exception {
        String token = jwtTokenProvider.createToken(1L, UserRole.MASTER);
        LocalDateTime startAt = LocalDateTime.now();
        LocalDateTime endAt = startAt.plusHours(1);
        TimeSaleEventCreateRequest request = new TimeSaleEventCreateRequest(1L, 59000, 100, startAt, endAt);
        TimeSaleEventResponse response = new TimeSaleEventResponse(1L, 1L, "무선 이어폰", 59000, 100, 0, startAt, endAt);

        given(timeSaleFacade.createEvent(request)).willReturn(response);

        mockMvc.perform(post("/api/timesales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.productName").value("무선 이어폰"));
    }

    @Test
    void MASTER_권한이_없으면_타임세일_이벤트_등록에_실패한다() throws Exception {
        String token = jwtTokenProvider.createToken(1L, UserRole.USER);
        LocalDateTime startAt = LocalDateTime.now();
        TimeSaleEventCreateRequest request = new TimeSaleEventCreateRequest(1L, 59000, 100, startAt, startAt.plusHours(1));

        mockMvc.perform(post("/api/timesales")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }
}
