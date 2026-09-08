(async function () {
    const token = localStorage.getItem('accessToken');
    const result = document.getElementById('result');

    if (!token) {
        window.location.href = '/templates/user/login.html';
        return;
    }

    const id = new URLSearchParams(window.location.search).get('id');

    const response = await fetch(`/api/timesales/${id}`, {
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

    const event = body.data;
    const startAt = new Date(event.startAt);
    const endAt = new Date(event.endAt);

    document.getElementById('time-sale-detail').innerHTML = `
        <dt>상품명</dt><dd>${event.productName}</dd>
        <dt>타임세일가</dt><dd>${event.discountPrice.toLocaleString()}원</dd>
        <dt>참여 현황</dt><dd>${event.currentParticipants} / ${event.participantLimit}명</dd>
    `;

    const countdownEl = document.getElementById('countdown');
    const participateButton = document.getElementById('participate-button');

    function formatRemaining(diffMs) {
        const totalSeconds = Math.floor(diffMs / 1000);
        const hours = Math.floor(totalSeconds / 3600);
        const minutes = Math.floor((totalSeconds % 3600) / 60);
        const seconds = totalSeconds % 60;
        return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
    }

    function tick() {
        const now = new Date();

        if (now < startAt) {
            countdownEl.textContent = `시작까지 남은 시간 ${formatRemaining(startAt - now)}`;
            participateButton.disabled = true;
        } else if (now < endAt) {
            countdownEl.textContent = `종료까지 남은 시간 ${formatRemaining(endAt - now)}`;
            participateButton.disabled = false;
        } else {
            countdownEl.textContent = '종료된 타임세일입니다.';
            participateButton.disabled = true;
        }
    }

    tick();
    setInterval(tick, 1000);

    participateButton.addEventListener('click', async function () {
        participateButton.disabled = true;

        const participateResponse = await fetch(`/api/timesales/${id}/participate`, {
            method: 'POST',
            headers: {'Authorization': `Bearer ${token}`}
        });

        if (handleUnauthorized(participateResponse)) {
            return;
        }

        const participateBody = await participateResponse.json();

        if (participateResponse.ok && participateBody.success) {
            window.location.href = `/templates/payment/checkout.html?orderId=${participateBody.data.orderId}`;
        } else {
            result.textContent = `참여 실패: ${participateBody.message}`;
            result.classList.add('error');
            participateButton.disabled = false;
        }
    });
})();
