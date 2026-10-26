const loginForm = document.getElementById('login-form');
let loginSubmitting = false;
if (loginForm) loginForm.addEventListener('submit', async event => {
    event.preventDefault(); if (loginSubmitting) return;
    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value;
    if (!username || !password) { showAlert('Vui lòng nhập tên đăng nhập và mật khẩu!', 'error'); return; }
    loginSubmitting = true;
    loginForm.querySelector('[type="submit"]').disabled = true;
    try {
        await apiPost('/auth/login', { username, password });
        document.getElementById('password').value = '';
        window.location.replace('rooms.html');
    } catch (error) { showAlert(error.message || 'Không thể đăng nhập. Vui lòng thử lại!', 'error'); }
    finally { loginSubmitting = false; loginForm.querySelector('[type="submit"]').disabled = false; }
});
document.addEventListener('DOMContentLoaded', async () => {
    if (!loginForm) return;
    if (new URLSearchParams(window.location.search).get('changed') === '1') showAlert('Đã đổi mật khẩu. Vui lòng đăng nhập lại!');
    if (await checkLogin()) window.location.replace('rooms.html');
});
