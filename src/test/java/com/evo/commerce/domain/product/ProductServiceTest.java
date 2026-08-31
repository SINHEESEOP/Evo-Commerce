package com.evo.commerce.domain.product;

import com.evo.commerce.domain.product.dto.ProductResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    ProductService productService;

    @Test
    void 등록된_상품_목록을_조회한다() {
        Product product = Product.builder()
                .name("테스트 상품")
                .price(10000)
                .stock(5)
                .build();

        given(productRepository.findAll()).willReturn(List.of(product));

        List<ProductResponse> responses = productService.getProducts();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("테스트 상품");
    }

    @Test
    void 상품_아이디로_상세_정보를_조회한다() {
        Product product = Product.builder()
                .name("테스트 상품")
                .price(10000)
                .stock(5)
                .build();

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        ProductResponse response = productService.getProduct(1L);

        assertThat(response.name()).isEqualTo("테스트 상품");
        assertThat(response.price()).isEqualTo(10000);
        assertThat(response.stock()).isEqualTo(5);
    }
}
