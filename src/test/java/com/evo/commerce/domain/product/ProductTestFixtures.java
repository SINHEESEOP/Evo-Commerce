package com.evo.commerce.domain.product;

final class ProductTestFixtures {

    private ProductTestFixtures() {
    }

    static Product newProduct(int stock) {
        return Product.builder()
                .name("테스트 상품")
                .price(10000)
                .stock(stock)
                .build();
    }
}
