package com.evo.commerce.domain.product.application;

import com.evo.commerce.domain.product.domain.Product;
import com.evo.commerce.domain.product.domain.ProductRepository;
import com.evo.commerce.domain.product.dto.ProductCreateRequest;
import com.evo.commerce.domain.product.dto.ProductResponse;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.ProductErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static com.evo.commerce.domain.product.domain.ProductTestFixtures.newProduct;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository productRepository;

    @InjectMocks
    ProductService productService;

    @Test
    void 등록된_상품_목록을_조회한다() {
        Product product = newProduct(5);

        given(productRepository.findAll()).willReturn(List.of(product));

        List<ProductResponse> responses = productService.getProducts();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).name()).isEqualTo("테스트 상품");
    }

    @Test
    void 상품_아이디로_상세_정보를_조회한다() {
        Product product = newProduct(5);

        given(productRepository.findById(1L)).willReturn(Optional.of(product));

        ProductResponse response = productService.getProduct(1L);

        assertThat(response.name()).isEqualTo("테스트 상품");
        assertThat(response.price()).isEqualTo(10000);
        assertThat(response.stock()).isEqualTo(5);
    }

    @Test
    void 존재하지_않는_상품_아이디로_조회하면_예외가_발생한다() {
        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void 상품을_등록하면_저장된_상품_정보를_반환한다() {
        ProductCreateRequest request = new ProductCreateRequest("새 상품", 15000, 20);

        given(productRepository.save(ArgumentMatchers.any(Product.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.registerProduct(request);

        assertThat(response.name()).isEqualTo("새 상품");
        assertThat(response.price()).isEqualTo(15000);
        assertThat(response.stock()).isEqualTo(20);
    }
}
