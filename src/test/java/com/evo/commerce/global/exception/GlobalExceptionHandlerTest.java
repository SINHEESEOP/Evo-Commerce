package com.evo.commerce.global.exception;

import com.evo.commerce.global.auth.JwtTokenProvider;
import com.evo.commerce.global.exception.support.ThrowingController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ThrowingController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtTokenProvider jwtTokenProvider;

    @Test
    void 검증_예외_응답_구조를_확인한다() throws Exception {

        String json = """
        {"name": ""}
        """;

        mockMvc.perform(post("/test/validation-error")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andDo(print());
    }

    @Test
    void 검증_예외_응답_구조를_확인한다2() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andDo(print());
    }

    @Test
    void 비즈니스_예외가_발생하면_에러코드에_정의된_상태코드와_메시지를_반환한다() throws Exception {
        mockMvc.perform(get("/test/business-error"))
                .andDo(print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.INVALID_INPUT_VALUE.getMessage()));
    }

    @Test
    void 예상하지_못한_예외가_발생하면_500과_정제된_메시지를_반환한다() throws Exception {
        mockMvc.perform(get("/test/unexpected-error"))
                .andDo(print())
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }

}
