package com.evo.commerce.domain.product;

import com.evo.commerce.domain.product.dto.ProductResponse;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.ProductErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;

    public List<ProductResponse> getProducts() {
        return productRepository.findAll().stream()
                .map(ProductMapper::toResponse)
                .toList();
    }

    public ProductResponse getProduct(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ProductErrorCode.PRODUCT_NOT_FOUND));
        return ProductMapper.toResponse(product);
    }
}
