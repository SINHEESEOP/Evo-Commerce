package com.evo.commerce.domain.order.domain;

import com.evo.commerce.domain.product.domain.Product;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRole;

public final class OrderTestFixtures {

    private OrderTestFixtures() {
    }

    public static User newUser() {
        return User.builder()
                .email("tester@evo-commerce.com")
                .password("encoded-password")
                .name("테스터")
                .role(UserRole.USER)
                .build();
    }

    public static Product newProduct() {
        return Product.builder()
                .name("테스트 상품")
                .price(10000)
                .stock(10)
                .build();
    }
}
