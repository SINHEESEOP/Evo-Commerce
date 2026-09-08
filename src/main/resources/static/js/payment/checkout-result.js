(async function () {
    const token = localStorage.getItem('accessToken');
    const result = document.getElementById('result');
    const params = new URLSearchParams(window.location.search);

    const paymentKey = params.get('paymentKey');
    const tossOrderId = params.get('orderId');
    const amount = params.get('amount');
    const failMessage = params.get('message');

    if (!paymentKey) {
        result.textContent = `결제 실패: ${failMessage ?? '알 수 없는 오류'}`;
        result.classList.add('error');
        return;
    }

    const orderId = tossOrderId.replace('ORDER-', '');

    const response = await fetch(`/api/orders/${orderId}/payments/confirm`, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({paymentKey: paymentKey, amount: Number(amount)})
    });

    if (handleUnauthorized(response)) {
        return;
    }

    const body = await response.json();

    if (response.ok && body.success) {
        result.textContent = `결제 승인 완료. 주문 상태: ${body.data.status}`;
    } else {
        result.textContent = `결제 승인 실패: ${body.message}`;
        result.classList.add('error');
    }
})();
