// ============================================
// auth.js - XU LY DANG NHAP / DANG XUAT
// NGUOI LAM: DUC DAI (LEADER) (LEADER)
// API BACKEND: DUC DAI -> POST /api/auth/login
// ============================================

document.getElementById('login-form').addEventListener('submit', async (e) => {
    e.preventDefault();

    const username = document.getElementById('username').value.trim();
    const password = document.getElementById('password').value.trim();

    if (!username || !password) {
        showLoginAlert('Vui lòng nhập đầy đủ thông tin!', 'error');
        return;
    }

    try {
        // TODO: Duc Dai tao API POST /api/auth/login
        // Gui { username, password } -> nhan ve { success, user }
        const result = await apiPost('/auth/login', { username, password });

        if (result.success) {
            sessionStorage.setItem('currentUser', JSON.stringify(result.user));
            window.location.href = 'rooms.html'; // Chuyen sang trang chinh
        } else {
            showLoginAlert('Sai tên đăng nhập hoặc mật khẩu!', 'error');
        }
    } catch (err) {
        showLoginAlert('Không thể kết nối server. Kiểm tra lại backend!', 'error');
    }
});

function showLoginAlert(msg, type) {
    const el = document.getElementById('alert-message');
    el.textContent = msg;
    el.className = `alert alert-${type}`;
    el.style.display = 'block';
}
