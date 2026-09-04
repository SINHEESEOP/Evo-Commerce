(function () {
    const token = localStorage.getItem('accessToken');
    const loggedIn = !!token;

    document.querySelectorAll('#nav-login, #nav-signup').forEach(function (el) {
        el.hidden = loggedIn;
    });
    document.querySelectorAll('#nav-logout, #nav-register').forEach(function (el) {
        el.hidden = !loggedIn;
    });

    const logoutButton = document.getElementById('nav-logout');
    if (logoutButton) {
        logoutButton.addEventListener('click', function () {
            localStorage.removeItem('accessToken');
            window.location.href = '/login.html';
        });
    }
})();
