(async function () {
    const token = localStorage.getItem('accessToken');
    const result = document.getElementById('result');

    if (!token) {
        window.location.href = '/templates/user/login.html';
        return;
    }

    const response = await fetch('/api/products', {
        headers: {'Authorization': `Bearer ${token}`}
    });

    if (handleUnauthorized(response)) {
        return;
    }

    const body = await response.json();

    if (response.ok && body.success) {
        const list = document.getElementById('product-list');

        if (body.data.length === 0) {
            result.textContent = '등록된 상품이 없습니다.';
        }

        body.data.forEach(function (product) {
            const item = document.createElement('li');
            item.innerHTML = `
                <a href="/templates/product/product-detail.html?id=${product.id}">
                    ${product.name}
                    <span class="price">${product.price.toLocaleString()}원</span>
                    <span class="stock">재고 ${product.stock}개</span>
                </a>
            `;
            list.appendChild(item);
        });
    } else {
        result.textContent = `조회 실패: ${body.message}`;
        result.classList.add('error');
    }
})();
