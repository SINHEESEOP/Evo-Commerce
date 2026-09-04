(function () {
    const token = localStorage.getItem('accessToken');
    const role = localStorage.getItem('role');
    const loggedIn = !!token;
    const isMaster = role === 'MASTER';

    document.querySelectorAll('#nav-login, #nav-signup').forEach(function (el) {
        el.hidden = loggedIn;
    });
    document.querySelectorAll('#nav-logout').forEach(function (el) {
        el.hidden = !loggedIn;
    });
    document.querySelectorAll('#nav-register').forEach(function (el) {
        el.hidden = !isMaster;
    });

    const logoutButton = document.getElementById('nav-logout');
    if (logoutButton) {
        logoutButton.addEventListener('click', function () {
            localStorage.removeItem('accessToken');
            localStorage.removeItem('role');
            window.location.href = '/login.html';
        });
    }

    window.handleUnauthorized = function (response) {
        if (response.status !== 401) {
            return false;
        }
        localStorage.removeItem('accessToken');
        localStorage.removeItem('role');
        window.location.href = '/login.html';
        return true;
    };
})();
