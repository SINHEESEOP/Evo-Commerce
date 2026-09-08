document.getElementById('signup-form').addEventListener('submit', async function (event) {
    event.preventDefault();

    const response = await fetch('/api/auth/signup', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
            email: document.getElementById('email').value,
            password: document.getElementById('password').value,
            name: document.getElementById('name').value
        })
    });

    const body = await response.json();
    const result = document.getElementById('result');

    if (response.ok && body.success) {
        result.textContent = `${body.data.name}님, 가입이 완료됐습니다. 로그인 페이지로 이동합니다.`;
        result.classList.remove('error');
        setTimeout(function () {
            window.location.href = '/templates/user/login.html';
        }, 1000);
    } else {
        result.textContent = `가입 실패: ${body.message}`;
        result.classList.add('error');
    }
});
