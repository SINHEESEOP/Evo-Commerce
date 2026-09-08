(async function () {
    const clientKey = 'test_ck_4yKeq5bgrpeKP029mLPZ8GX0lzW6';
    const token = localStorage.getItem('accessToken');
    const result = document.getElementById('result');

    if (!token) {
        window.location.href = '/templates/user/login.html';
        return;
    }

    const orderId = new URLSearchParams(window.location.search).get('orderId');

    const response = await fetch(`/api/orders/${orderId}`, {
        headers: {'Authorization': `Bearer ${token}`}
    });

    if (handleUnauthorized(response)) {
        return;
    }

    const body = await response.json();

    if (!response.ok || !body.success) {
        result.textContent = `주문 조회 실패: ${body.message}`;
        result.classList.add('error');
        return;
    }

    const order = body.data;
    const orderName = order.items.map(item => item.productName).join(', ');

    document.getElementById('order-detail').innerHTML = `
        <dt>주문번호</dt><dd>${order.id}</dd>
        <dt>주문상태</dt><dd>${order.status}</dd>
        <dt>결제금액</dt><dd>${order.totalAmount}원</dd>
    `;

    document.getElementById('pay-button').addEventListener('click', () => {
        const tossPayments = TossPayments(clientKey);
        tossPayments.requestPayment('카드', {
            amount: order.totalAmount,
            orderId: `ORDER-${order.id}`,
            orderName: orderName,
            successUrl: `${window.location.origin}/templates/payment/checkout-result.html`,
            failUrl: `${window.location.origin}/templates/payment/checkout-result.html`
        });
    });
})();
