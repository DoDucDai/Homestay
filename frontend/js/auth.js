const loginForm = document.getElementById('login-form');
let loginSubmitting = false, loginChecking = true, loginRedirecting = false, loginCheckVersion = 0;
function loginMessage(message = '', type = 'error') {
    const alert = document.getElementById('alert-message');
    if (!alert) return;
    alertMessageVersion++;
    alert.textContent = message; alert.className = 'alert alert-' + type;
    alert.style.display = message ? 'block' : 'none';
    alert.setAttribute('role', type === 'error' ? 'alert' : 'status');
}
function loginFieldError(id, message = '') {
    document.getElementById(id).setAttribute('aria-invalid', String(!!message));
    const error = document.getElementById(id + '-error');
    error.textContent = message; error.hidden = !message;
}
function updateLoginControls() {
    if (!loginForm) return;
    const locked = loginSubmitting || loginChecking || loginRedirecting;
    ['username', 'password', 'password-toggle'].forEach(id => { document.getElementById(id).disabled = locked; });
    loginForm.querySelector('[type="submit"]').disabled = locked;
    loginForm.setAttribute('aria-busy', String(locked));
    document.getElementById('login-submit-label').textContent = loginChecking ? 'Đang kiểm tra...' : loginSubmitting ? 'Đang đăng nhập...' : loginRedirecting ? 'Đang chuyển trang...' : 'Đăng nhập';
    document.getElementById('login-status').textContent = loginChecking ? 'Đang kiểm tra phiên đăng nhập...' : loginSubmitting ? 'Đang xác nhận tài khoản...' : loginRedirecting ? 'Đăng nhập thành công. Đang mở trang chủ...' : '';
}
async function initializeLoginSession(force = false) {
    if (!loginForm) return;
    const version = ++loginCheckVersion;
    loginChecking = true; updateLoginControls();
    const user = await checkLogin(force);
    if (version !== loginCheckVersion) return;
    loginChecking = false;
    if (user) { loginRedirecting = true; updateLoginControls(); window.location.replace('landing.html'); }
    else updateLoginControls();
}
if (loginForm) {
    bindPasswordVisibility(document.getElementById('password'), document.getElementById('password-toggle'));
    for (const id of ['username', 'password']) {
        document.getElementById(id).addEventListener('input', () => { loginFieldError(id); loginMessage(); });
    }
    loginForm.addEventListener('submit', async event => {
        event.preventDefault(); if (loginSubmitting || loginChecking || loginRedirecting) return;
        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;
        loginFieldError('username', !username || username.length > 50 ? 'Nhập tên đăng nhập, tối đa 50 ký tự.' : '');
        loginFieldError('password', !password || password.length > 256 ? 'Nhập mật khẩu, tối đa 256 ký tự.' : '');
        if (!username || username.length > 50 || !password || password.length > 256) {
            document.getElementById(!username || username.length > 50 ? 'username' : 'password').focus(); return;
        }
        loginMessage(); loginSubmitting = true; updateLoginControls();
        try {
            await apiPost('/auth/login', {username, password});
            document.getElementById('password').value = '';
            setPasswordVisibility(document.getElementById('password'), document.getElementById('password-toggle'));
            loginRedirecting = true; window.location.replace('landing.html');
        } catch (error) {
            setPasswordVisibility(document.getElementById('password'), document.getElementById('password-toggle'));
            loginMessage(error.message || 'Không thể đăng nhập. Vui lòng thử lại!');
        } finally {
            loginSubmitting = false; updateLoginControls();
            if (!loginRedirecting) document.getElementById('password').focus();
        }
    });
}
document.addEventListener('DOMContentLoaded', async () => {
    if (!loginForm) return;
    if (new URLSearchParams(window.location.search).get('changed') === '1') loginMessage('Đã đổi mật khẩu. Vui lòng đăng nhập lại!', 'success');
    await initializeLoginSession();
});
window.addEventListener('pagehide', () => {
    if (!loginForm) return;
    document.getElementById('password').value = '';
    setPasswordVisibility(document.getElementById('password'), document.getElementById('password-toggle'));
});
window.addEventListener('pageshow', event => {
    if (event.persisted && loginForm) { loginRedirecting = false; initializeLoginSession(true); }
});
