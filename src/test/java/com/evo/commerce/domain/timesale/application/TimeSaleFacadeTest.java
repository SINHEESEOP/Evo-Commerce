package com.evo.commerce.domain.timesale.application;

import com.evo.commerce.domain.order.domain.OrderRepository;
import com.evo.commerce.domain.product.domain.Product;
import com.evo.commerce.domain.product.domain.ProductRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleEvent;
import com.evo.commerce.domain.timesale.domain.TimeSaleEventRepository;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipation;
import com.evo.commerce.domain.timesale.domain.TimeSaleParticipationRepository;
import com.evo.commerce.domain.timesale.dto.TimeSaleEventCreateRequest;
import com.evo.commerce.domain.timesale.dto.TimeSaleEventResponse;
import com.evo.commerce.domain.timesale.dto.TimeSaleParticipationResponse;
import com.evo.commerce.domain.user.domain.User;
import com.evo.commerce.domain.user.domain.UserRepository;
import com.evo.commerce.global.exception.BusinessException;
import com.evo.commerce.global.exception.ProductErrorCode;
import com.evo.commerce.global.exception.TimeSaleErrorCode;
import com.evo.commerce.global.exception.UserErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static com.evo.commerce.domain.order.domain.OrderTestFixtures.newUser;
import static com.evo.commerce.domain.timesale.domain.TimeSaleTestFixtures.newEvent;
import static com.evo.commerce.domain.timesale.domain.TimeSaleTestFixtures.newProduct;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TimeSaleFacadeTest {

    @Mock
    TimeSaleEventRepository timeSaleEventRepository;

    @Mock
    TimeSaleParticipationRepository timeSaleParticipationRepository;

    @Mock
    ProductRepository productRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    OrderRepository orderRepository;

    @InjectMocks
    TimeSaleFacade timeSaleFacade;

    @Test
    void 진행_중인_이벤트에_참여하면_할인가로_주문이_생성되고_참여_기록이_저장된다() {
        LocalDateTime now = LocalDateTime.now();
        Product product = newProduct();
        TimeSaleEvent event = newEvent(product, now.minusMinutes(10), now.plusMinutes(10));
        User user = newUser();

        given(timeSaleEventRepository.findById(1L)).willReturn(Optional.of(event));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(timeSaleParticipationRepository.existsByTimeSaleEventAndUser(event, user)).willReturn(false);
        given(timeSaleParticipationRepository.countByTimeSaleEvent(event)).willReturn(3L);
        given(orderRepository.save(ArgumentMatchers.any())).willAnswer(invocation -> invocation.getArgument(0));
        given(timeSaleParticipationRepository.save(ArgumentMatchers.any())).willAnswer(invocation -> invocation.getArgument(0));

        TimeSaleParticipationResponse response = timeSaleFacade.participate(1L, 1L);

        assertThat(response).isNotNull();

        ArgumentCaptor<TimeSaleParticipation> captor = ArgumentCaptor.forClass(TimeSaleParticipation.class);
        verify(timeSaleParticipationRepository).save(captor.capture());
        TimeSaleParticipation savedParticipation = captor.getValue();
        assertThat(savedParticipation.getUser()).isEqualTo(user);
        assertThat(savedParticipation.getTimeSaleEvent()).isEqualTo(event);
        assertThat(savedParticipation.getOrder().getOrderItems().get(0).getProductSnapshot().unitPrice()).isEqualTo(59000);
    }

    @Test
    void 존재하지_않는_이벤트에_참여하면_예외가_발생한다() {
        given(timeSaleEventRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> timeSaleFacade.participate(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", TimeSaleErrorCode.TIME_SALE_EVENT_NOT_FOUND);
    }

    @Test
    void 시작_전인_이벤트에_참여하면_예외가_발생한다() {
        LocalDateTime now = LocalDateTime.now();
        TimeSaleEvent event = newEvent(newProduct(), now.plusHours(1), now.plusHours(2));

        given(timeSaleEventRepository.findById(1L)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> timeSaleFacade.participate(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", TimeSaleErrorCode.TIME_SALE_NOT_IN_PROGRESS);
    }

    @Test
    void 종료된_이벤트에_참여하면_예외가_발생한다() {
        LocalDateTime now = LocalDateTime.now();
        TimeSaleEvent event = newEvent(newProduct(), now.minusHours(2), now.minusHours(1));

        given(timeSaleEventRepository.findById(1L)).willReturn(Optional.of(event));

        assertThatThrownBy(() -> timeSaleFacade.participate(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", TimeSaleErrorCode.TIME_SALE_NOT_IN_PROGRESS);
    }

    @Test
    void 이미_참여한_이벤트에_다시_참여하면_예외가_발생한다() {
        LocalDateTime now = LocalDateTime.now();
        TimeSaleEvent event = newEvent(newProduct(), now.minusMinutes(10), now.plusMinutes(10));
        User user = newUser();

        given(timeSaleEventRepository.findById(1L)).willReturn(Optional.of(event));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(timeSaleParticipationRepository.existsByTimeSaleEventAndUser(event, user)).willReturn(true);

        assertThatThrownBy(() -> timeSaleFacade.participate(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", TimeSaleErrorCode.ALREADY_PARTICIPATED);
    }

    @Test
    void 선착순_인원이_모두_찼으면_예외가_발생한다() {
        LocalDateTime now = LocalDateTime.now();
        TimeSaleEvent event = newEvent(newProduct(), now.minusMinutes(10), now.plusMinutes(10));
        User user = newUser();

        given(timeSaleEventRepository.findById(1L)).willReturn(Optional.of(event));
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(timeSaleParticipationRepository.existsByTimeSaleEventAndUser(event, user)).willReturn(false);
        given(timeSaleParticipationRepository.countByTimeSaleEvent(event)).willReturn(100L);

        assertThatThrownBy(() -> timeSaleFacade.participate(1L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", TimeSaleErrorCode.PARTICIPANT_LIMIT_EXCEEDED);
    }

    @Test
    void 존재하는_상품으로_이벤트를_등록하면_등록된_이벤트_정보를_반환한다() {
        Product product = newProduct();
        TimeSaleEventCreateRequest request = new TimeSaleEventCreateRequest(
                1L, 59000, 100, LocalDateTime.now(), LocalDateTime.now().plusHours(1));

        given(productRepository.findById(1L)).willReturn(Optional.of(product));
        given(timeSaleEventRepository.save(ArgumentMatchers.any()))
                .willAnswer(invocation -> invocation.getArgument(0));

        TimeSaleEventResponse response = timeSaleFacade.createEvent(request);

        assertThat(response.productName()).isEqualTo("무선 이어폰");
        assertThat(response.discountPrice()).isEqualTo(59000);
        assertThat(response.participantLimit()).isEqualTo(100);
        assertThat(response.currentParticipants()).isZero();
    }

    @Test
    void 존재하지_않는_상품으로_이벤트를_등록하면_예외가_발생한다() {
        TimeSaleEventCreateRequest request = new TimeSaleEventCreateRequest(
                999L, 59000, 100, LocalDateTime.now(), LocalDateTime.now().plusHours(1));

        given(productRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> timeSaleFacade.createEvent(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ProductErrorCode.PRODUCT_NOT_FOUND);
    }

    @Test
    void 이벤트_목록을_조회하면_참여_인원수와_함께_반환된다() {
        LocalDateTime now = LocalDateTime.now();
        TimeSaleEvent event = newEvent(newProduct(), now.minusMinutes(10), now.plusMinutes(10));

        given(timeSaleEventRepository.findAll()).willReturn(List.of(event));
        given(timeSaleParticipationRepository.countByTimeSaleEvent(event)).willReturn(7L);

        List<TimeSaleEventResponse> responses = timeSaleFacade.getEvents();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).currentParticipants()).isEqualTo(7);
    }

    @Test
    void 존재하지_않는_사용자로_참여하면_예외가_발생한다() {
        LocalDateTime now = LocalDateTime.now();
        TimeSaleEvent event = newEvent(newProduct(), now.minusMinutes(10), now.plusMinutes(10));

        given(timeSaleEventRepository.findById(1L)).willReturn(Optional.of(event));
        given(userRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> timeSaleFacade.participate(999L, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", UserErrorCode.USER_NOT_FOUND);
    }
}
