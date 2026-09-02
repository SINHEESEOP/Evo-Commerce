package com.evo.commerce.domain.order.domain;

import com.evo.commerce.domain.product.domain.Product;
import com.evo.commerce.domain.product.domain.ProductRepository;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.assertj.core.api.Assertions.assertThat;
import static com.evo.commerce.domain.order.domain.OrderTestFixtures.newProduct;
import static com.evo.commerce.domain.order.domain.OrderTestFixtures.newUser;

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
        User user = userRepository.save(newUser());

        Product product = productRepository.save(newProduct());

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
