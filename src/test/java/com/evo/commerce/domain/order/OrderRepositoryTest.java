package com.evo.commerce.domain.order;

import com.evo.commerce.domain.product.Product;
import com.evo.commerce.domain.product.ProductRepository;
import com.evo.commerce.domain.user.User;
import com.evo.commerce.domain.user.UserRepository;
import com.evo.commerce.domain.user.UserRole;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class OrderRepositoryTest {

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    UserRepository userRepository;

    @Autowired
    ProductRepository productRepository;

    @Autowired
    EntityManager em;

    @Test
    void 주문에_담은_상품은_영속성_컨텍스트를_비우고_다시_조회해도_유지된다() {
        User user = userRepository.save(User.builder()
                .email("tester@evo-commerce.com")
                .password("plain1234!")
                .name("테스터")
                .role(UserRole.USER)
                .build());

        Product product = productRepository.save(Product.builder()
                .name("테스트 상품")
                .price(10000)
                .stock(10)
                .build());

        Order order = Order.builder().user(user).build();
        order.addItem(OrderItem.builder().product(product).quantity(2).build());

        Order saved = orderRepository.save(order);

        em.flush();
        em.clear();

        Order reloaded = orderRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getOrderItems()).hasSize(1);
        assertThat(reloaded.getOrderItems().get(0).getQuantity()).isEqualTo(2);
    }
}
