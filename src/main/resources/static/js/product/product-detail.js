(async function () {
    const token = localStorage.getItem('accessToken');
    const result = document.getElementById('result');

    if (!token) {
        window.location.href = '/templates/user/login.html';
        return;
    }

    const id = new URLSearchParams(window.location.search).get('id');

    const response = await fetch(`/api/products/${id}`, {
        headers: {'Authorization': `Bearer ${token}`}
    });

    if (handleUnauthorized(response)) {
        return;
    }

    const body = await response.json();

    if (!response.ok || !body.success) {
        result.textContent = `조회 실패: ${body.message}`;
        result.classList.add('error');
        return;
    }

    document.getElementById('product-detail').innerHTML = `
        <dt>상품명</dt><dd>${body.data.name}</dd>
        <dt>가격</dt><dd>${body.data.price}원</dd>
        <dt>재고</dt><dd>${body.data.stock}개</dd>
    `;

    const orderButton = document.getElementById('order-button');

    orderButton.addEventListener('click', async function () {
        orderButton.disabled = true;

        const quantity = Number(document.getElementById('quantity').value);

        const orderResponse = await fetch('/api/orders', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': `Bearer ${token}`
            },
            body: JSON.stringify({
                items: [{productId: Number(id), quantity: quantity}]
            })
        });

        if (handleUnauthorized(orderResponse)) {
            return;
        }

        const orderBody = await orderResponse.json();

        if (orderResponse.ok && orderBody.success) {
            window.location.href = `/templates/payment/checkout.html?orderId=${orderBody.data.id}`;
        } else {
            result.textContent = `주문 실패: ${orderBody.message}`;
            result.classList.add('error');
            orderButton.disabled = false;
        }
    });
})();
