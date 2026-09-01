package com.evo.commerce.domain.order;

import com.evo.commerce.domain.product.Product;
import com.evo.commerce.domain.user.User;
import com.evo.commerce.domain.user.UserRole;

final class OrderTestFixtures {

    private OrderTestFixtures() {
    }

    static User newUser() {
        return User.builder()
                .email("tester@evo-commerce.com")
                .password("encoded-password")
                .name("테스터")
                .role(UserRole.USER)
                .build();
    }

    static Product newProduct() {
        return Product.builder()
                .name("테스트 상품")
                .price(10000)
                .stock(10)
                .build();
    }
}
