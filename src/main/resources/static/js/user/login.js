document.getElementById('login-form').addEventListener('submit', async function (event) {
    event.preventDefault();

    const response = await fetch('/api/auth/login', {
        method: 'POST',
        headers: {'Content-Type': 'application/json'},
        body: JSON.stringify({
            email: document.getElementById('email').value,
            password: document.getElementById('password').value
        })
    });

    const body = await response.json();
    const result = document.getElementById('result');

    if (response.ok && body.success) {
        localStorage.setItem('accessToken', body.data.token);
        localStorage.setItem('role', body.data.user.role);
        result.textContent = `${body.data.user.name}님, 환영합니다.`;
        result.classList.remove('error');
        setTimeout(function () {
            window.location.href = '/index.html';
        }, 600);
    } else {
        result.textContent = `로그인 실패: ${body.message}`;
        result.classList.add('error');
    }
});
