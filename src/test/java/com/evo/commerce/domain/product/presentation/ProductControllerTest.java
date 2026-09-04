package com.evo.commerce.domain.product.presentation;

import com.evo.commerce.domain.product.application.ProductService;
import com.evo.commerce.domain.product.dto.ProductCreateRequest;
import com.evo.commerce.domain.product.dto.ProductResponse;
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

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProductController.class)
@Import({JwtAuthenticationFilter.class, AuthInterceptor.class, WebMvcConfig.class, JwtTokenProvider.class})
@TestPropertySource(properties = "jwt.secret=test-jwt-secret-key-for-product-controller-test-only")
class ProductControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    ProductService productService;

    @Test
    void 로그인한_사용자는_상품을_등록할_수_있다() throws Exception {
        String token = jwtTokenProvider.createToken(1L, UserRole.USER);
        ProductCreateRequest request = new ProductCreateRequest("새 상품", 15000, 20);
        ProductResponse response = new ProductResponse(1L, request.name(), request.price(), request.stock());

        given(productService.registerProduct(request)).willReturn(response);

        mockMvc.perform(post("/api/products")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value(request.name()));
    }
}
