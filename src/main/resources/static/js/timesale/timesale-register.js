const token = localStorage.getItem('accessToken');
const role = localStorage.getItem('role');

if (!token) {
    window.location.href = '/templates/user/login.html';
} else if (role !== 'MASTER') {
    document.getElementById('register-form').hidden = true;
    document.getElementById('permission-notice').hidden = false;
}

document.getElementById('register-form').addEventListener('submit', async function (event) {
    event.preventDefault();

    const response = await fetch('/api/timesales', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${token}`
        },
        body: JSON.stringify({
            productId: Number(document.getElementById('productId').value),
            discountPrice: Number(document.getElementById('discountPrice').value),
            participantLimit: Number(document.getElementById('participantLimit').value),
            startAt: document.getElementById('startAt').value,
            endAt: document.getElementById('endAt').value
        })
    });

    if (handleUnauthorized(response)) {
        return;
    }

    const body = await response.json();
    const result = document.getElementById('result');

    if (response.ok && body.success) {
        result.textContent = `${body.data.productName} 타임세일이 등록됐습니다. 타임세일 목록으로 이동합니다.`;
        result.classList.remove('error');
        setTimeout(function () {
            window.location.href = '/templates/timesale/timesales.html';
        }, 1000);
    } else {
        result.textContent = `등록 실패: ${body.message}`;
        result.classList.add('error');
    }
});
