// Cookie sessions and CSRF. Set HOMESTAY_API_BASE before this file only for explicit dev CORS.
const API_BASE = window.HOMESTAY_API_BASE || '/api';
let authPromise = null;
let currentUser = null;
let csrfPromise = null;
let redirectingToLogin = false;
let authVersion = 0;
try { sessionStorage.removeItem('currentUser'); } catch { /* Storage is optional; the server owns the session. */ }

function resetAuth() {
    authVersion++;
    currentUser = null;
    authPromise = null;
    csrfPromise = null;
}
function goToLogin() {
    if (!window.location.pathname.endsWith('/login.html') && !redirectingToLogin) {
        redirectingToLogin = true;
        window.location.replace('login.html');
    }
}
async function responseJson(response) {
    let data;
    try { data = await response.json(); }
    catch {
        const error = new Error('Phản hồi không hợp lệ từ máy chủ.');
        error.status = response.ok ? 502 : response.status;
        throw error;
    }
    if (!response.ok) {
        const error = new Error(data.message || 'Không thể thực hiện yêu cầu.');
        error.status = response.status;
        error.code = data.code;
        throw error;
    }
    if (data && data.success === false) {
        const error = new Error(data.message || 'Không thể thực hiện yêu cầu.');
        error.status = 409; error.code = data.code; throw error;
    }
    return data;
}
async function checkLogin(force = false) {
    if (force) { authVersion++; authPromise = null; csrfPromise = null; }
    if (authPromise && !force) return authPromise;
    const version = authVersion;
    authPromise = (async () => {
        try {
            const data = await responseJson(await fetch(API_BASE + '/auth/me', {
                credentials: 'include', cache: 'no-store'
            }));
            if (version !== authVersion) return null;
            if (!data.success || !data.user || !['admin','staff'].includes(data.user.role)) throw new Error('Không thể xác nhận tài khoản.');
            currentUser = data.user;
            return currentUser;
        } catch (error) {
            if (version !== authVersion) return null;
            currentUser = null;
            authPromise = null;
            if (error.status === 401) goToLogin();
            else showAlert(error.message || 'Không thể kiểm tra đăng nhập.', 'error');
            return null;
        }
    })();
    return authPromise;
}
async function getCsrfToken() {
    if (!csrfPromise) {
        const version = authVersion;
        csrfPromise = (async () => {
            const data = await responseJson(await fetch(API_BASE + '/auth/csrf', {
                credentials: 'include', cache: 'no-store'
            }));
            if (version !== authVersion) throw new Error('Phiên đã thay đổi. Vui lòng tải lại trang.');
            if (!data.token || data.headerName !== 'X-CSRF-TOKEN') throw new Error('Không thể xác nhận phiên thao tác.');
            return data;
        })().catch(error => {
            if (version !== authVersion) throw new Error('Phiên đã thay đổi. Vui lòng tải lại trang.');
            csrfPromise = null;
            if (error.status === 401) { resetAuth(); goToLogin(); }
            throw error;
        });
    }
    return csrfPromise;
}
async function apiRequest(endpoint, method = 'GET', data) {
    const isLogin = endpoint === '/auth/login';
    if (!isLogin && !await checkLogin()) throw new Error('Vui lòng đăng nhập để tiếp tục.');
    const version = authVersion;
    const options = { method, credentials: 'include', cache: 'no-store', headers: {} };
    if (method !== 'GET') {
        const csrf = await getCsrfToken();
        if (version !== authVersion) throw new Error('Phiên đã thay đổi. Vui lòng tải lại trang.');
        options.headers[csrf.headerName] = csrf.token;
        if (data !== undefined) {
            options.headers['Content-Type'] = 'application/json';
            options.body = JSON.stringify(data);
        }
    }
    try {
        const result = await responseJson(await fetch(API_BASE + endpoint, options));
        if (version !== authVersion) throw new Error('Phiên đã thay đổi. Vui lòng tải lại trang.');
        if (result.success && ['/auth/login', '/auth/logout', '/auth/change-password'].includes(endpoint)) resetAuth();
        return result;
    } catch (error) {
        if (version !== authVersion) throw new Error('Phiên đã thay đổi. Vui lòng tải lại trang.');
        if (error.code === 'CSRF_INVALID') csrfPromise = null;
        if (error.status === 401 && !isLogin) { resetAuth(); goToLogin(); }
        // Do not retry writes automatically: a response can be lost after commit.
        throw error;
    }
}
function apiGet(endpoint) { return apiRequest(endpoint); }
function apiPost(endpoint, data) { return apiRequest(endpoint, 'POST', data); }
function apiPut(endpoint, data) { return apiRequest(endpoint, 'PUT', data); }
function apiDelete(endpoint) { return apiRequest(endpoint, 'DELETE'); }

function showAlert(message, type = 'success') {
    const el = document.getElementById('alert-message');
    if (!el) return;
    el.textContent = message;
    el.className = `alert alert-${type}`;
    el.style.display = 'block';
    setTimeout(() => { el.style.display = 'none'; }, 5000);
}
function openModal(modalId) { document.getElementById(modalId).classList.add('show'); }
function closeModal() { document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('show')); }
function formatMoney(amount) { return (typeof amount === 'bigint' ? amount : Number(amount)).toLocaleString('vi-VN') + ' VNĐ'; }
function formatDate(dateStr) { return dateStr ? new Date(dateStr).toLocaleDateString('vi-VN') : '--'; }

// Dynamic data is text; action handlers close over data rather than inline code.
function textNode(tag, value, className) {
    const node = document.createElement(tag);
    node.textContent = String(value ?? '');
    if (className) node.className = className;
    return node;
}
function actionButton(label, className, handler) {
    const button = textNode('button', label, className);
    button.type = 'button'; button.addEventListener('click', handler); return button;
}
function appendTableRow(body, values, actions = null) {
    const row = document.createElement('tr');
    for (const value of values) {
        const cell = document.createElement('td');
        if (value instanceof Node) cell.appendChild(value);
        else cell.textContent = String(value ?? '');
        row.appendChild(cell);
    }
    if (actions !== null) {
        const cell = document.createElement('td');
        actions.forEach(button => cell.appendChild(button)); row.appendChild(cell);
    }
    body.appendChild(row); return row;
}
function tableState(id, columns, message) {
    const body = document.getElementById(id); if (!body) return;
    body.replaceChildren();
    const row = appendTableRow(body, [message]);
    row.firstElementChild.colSpan = columns;
    row.firstElementChild.style.textAlign = 'center';
}
function expectList(data) {
    if (!Array.isArray(data)) throw new Error('Danh sách nhận được không hợp lệ.'); return data;
}
function selectOption(value, label, price) {
    const option = textNode('option', label); option.value = String(value);
    if (price !== undefined) option.dataset.price = String(price); return option;
}
function showRequestError(error, fallback) { showAlert(error.message || fallback, 'error'); }
function validText(value, label, max, required = true) {
    if (value.length > max || required && !value.trim()) throw new Error(`${label} trống hoặc vượt ${max} ký tự.`);
    return value;
}
function validMoney(value, label, minimum = 0) {
    const money = Number(value);
    if (String(value).trim() === '' || !Number.isSafeInteger(money) || money < minimum || money > 999999999999) {
        throw new Error(`${label} phải là số nguyên trong giới hạn cho phép.`);
    }
    return money;
}
function validEnum(value, allowed, label) {
    if (!allowed.includes(value)) throw new Error(`${label} không hợp lệ.`); return value;
}
function validBookingDates(checkin, checkout) {
    for (const value of [checkin, checkout]) {
        const date = new Date(value + 'T00:00:00Z');
        if (!/^[0-9]{4}-[0-9]{2}-[0-9]{2}$/.test(value) || value.startsWith('0000')
            || !Number.isFinite(date.getTime()) || date.toISOString().slice(0, 10) !== value) throw new Error('Ngày không hợp lệ.');
    }
    if (checkin < vietnamToday() || checkout <= checkin) throw new Error('Ngày nhận không trong quá khứ; ngày trả phải sau ngày nhận.');
}
function vietnamToday(offset = 0) {
    return new Date(Date.now() + 7 * 3600000 + offset * 86400000).toISOString().slice(0, 10);
}
