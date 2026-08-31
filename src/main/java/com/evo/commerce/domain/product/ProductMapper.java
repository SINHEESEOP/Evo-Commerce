package com.evo.commerce.domain.product;

import com.evo.commerce.domain.product.dto.ProductResponse;

public class ProductMapper {

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock()
        );
    }
}
