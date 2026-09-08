(async function () {
    const token = localStorage.getItem('accessToken');
    const result = document.getElementById('result');

    if (!token) {
        window.location.href = '/templates/user/login.html';
        return;
    }

    const response = await fetch('/api/timesales', {
        headers: {'Authorization': `Bearer ${token}`}
    });

    if (handleUnauthorized(response)) {
        return;
    }

    const body = await response.json();

    if (response.ok && body.success) {
        const list = document.getElementById('time-sale-list');

        if (body.data.length === 0) {
            result.textContent = '진행 중인 타임세일이 없습니다.';
        }

        body.data.forEach(function (event) {
            const item = document.createElement('li');
            item.innerHTML = `
                <a href="/templates/timesale/timesale-detail.html?id=${event.id}">
                    ${event.productName}
                    <span class="price">${event.discountPrice.toLocaleString()}원</span>
                    <span class="stock">${event.currentParticipants} / ${event.participantLimit}명 참여</span>
                </a>
            `;
            list.appendChild(item);
        });
    } else {
        result.textContent = `조회 실패: ${body.message}`;
        result.classList.add('error');
    }
})();
