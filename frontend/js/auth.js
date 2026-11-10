// NOTE FRONTEND: Điều khiển form đăng nhập; api.js thực hiện yêu cầu và backend quyết định danh tính/quyền.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
const loginForm = document.getElementById('login-form');
let loginSubmitting = false, loginChecking = true, loginRedirecting = false, loginCheckVersion = 0;
// NOTE FRONTEND: Chỉ nhận URL next cùng origin và thuộc danh sách trang cho phép để quay lại sau đăng nhập.
function loginDestination(user) {
    const next=new URLSearchParams(window.location.search).get('next');
    if(!next) return 'landing.html';
    try {
        const url=new URL(next,window.location.href);
        const pages=['landing','explore','account','bookings','customers','services','checkin','checkout','rooms','statistics','assistant','support','policies','reserve','my-bookings','my-invoices','payment-review'];
        if(url.origin===window.location.origin && user?.role==='admin'){
            const replacement={'/pages/reserve.html':'bookings.html','/pages/my-bookings.html':'bookings.html','/pages/my-invoices.html':'checkout.html'}[url.pathname];
            if(replacement)return replacement;
        }
        if(url.origin===window.location.origin && user?.role==='customer') {
            const replacement={'/pages/bookings.html':'reserve.html','/pages/checkin.html':'my-bookings.html','/pages/checkout.html':'my-invoices.html'}[url.pathname];
            if(replacement)return replacement+url.search+url.hash;
            if(['/pages/customers.html','/pages/rooms.html','/pages/statistics.html','/pages/rag-documents.html','/pages/payment-review.html'].includes(url.pathname))return 'reserve.html';
        }
        if(url.origin===window.location.origin && pages.some(page=>url.pathname==='/pages/'+page+'.html'))
            return url.pathname+url.search+url.hash;
    } catch (_) {}
    return 'landing.html';
}
// NOTE FRONTEND: Hiện thông báo đăng nhập bằng textContent, chọn vai trò alert/status cho trình đọc màn hình.
function loginMessage(message = '', type = 'error') {
    const alert = document.getElementById('alert-message');
    if (!alert) return;
    alertMessageVersion++;
    alert.textContent = message; alert.className = 'alert alert-' + type;
    alert.style.display = message ? 'block' : 'none';
    alert.setAttribute('role', type === 'error' ? 'alert' : 'status');
}
// NOTE FRONTEND: Đánh dấu trường không hợp lệ và gắn thông báo ngay cạnh ô nhập.
function loginFieldError(id, message = '') {
    document.getElementById(id).setAttribute('aria-invalid', String(!!message));
    const error = document.getElementById(id + '-error');
    error.textContent = message; error.hidden = !message;
}
// NOTE FRONTEND: Khóa ô/nút khi đang kiểm tra phiên, gửi mật khẩu hoặc chuyển trang; hiển thị đúng trạng thái hiện tại.
function updateLoginControls() {
    if (!loginForm) return;
    const locked = loginSubmitting || loginChecking || loginRedirecting;
    ['username', 'password', 'password-toggle'].forEach(id => { document.getElementById(id).disabled = locked; });
    loginForm.querySelector('[type="submit"]').disabled = locked;
    loginForm.setAttribute('aria-busy', String(locked));
    document.getElementById('login-submit-label').textContent = loginChecking ? 'Đang kiểm tra...' : loginSubmitting ? 'Đang đăng nhập...' : loginRedirecting ? 'Đang chuyển trang...' : 'Đăng nhập';
    document.getElementById('login-status').textContent = loginChecking ? 'Đang kiểm tra phiên đăng nhập...' : loginSubmitting ? 'Đang xác nhận tài khoản...' : loginRedirecting ? 'Đăng nhập thành công. Đang mở trang chủ...' : '';
}
// NOTE FRONTEND: Kiểm tra phiên sẵn có, bỏ kết quả cũ nếu đã có lần kiểm tra mới; đăng nhập rồi thì chuyển đến trang đích.
async function initializeLoginSession(force = false) {
    if (!loginForm) return;
    const version = ++loginCheckVersion;
    loginChecking = true; updateLoginControls();
    const user = await checkLogin(force);
    if (version !== loginCheckVersion) return;
    loginChecking = false;
    if (user) { loginRedirecting = true; updateLoginControls(); window.location.replace(loginDestination(user)); }
    else updateLoginControls();
}
if (loginForm) {
    bindPasswordVisibility(document.getElementById('password'), document.getElementById('password-toggle'));
    for (const id of ['username', 'password']) {
        document.getElementById(id).addEventListener('input', () => { loginFieldError(id); loginMessage(); });
    }
    // NOTE FRONTEND: Chặn gửi form mặc định và gửi lặp, kiểm tra độ dài rồi POST thông tin đăng nhập.
    // Sau thành công, xóa mật khẩu trên form và đọc lại /auth/me trước khi chuyển trang; lỗi thì mở lại form.
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
            const user=await checkLogin(true);
            loginRedirecting = true; window.location.replace(loginDestination(user));
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
    if(['register','forgot-password','reset-password'].includes(new URLSearchParams(window.location.search).get('view')))return;
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
