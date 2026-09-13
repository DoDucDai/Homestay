// ============================================
// api.js - HAM FETCH DUNG CHUNG CHO TOAN BO PROJECT
// NGUOI LAM: BAN (LEADER)
// ============================================

const API_BASE = 'http://localhost:8080/api';

// Ham GET
async function apiGet(endpoint) {
    const res = await fetch(API_BASE + endpoint);
    if (!res.ok) throw new Error('Loi ket noi server');
    return res.json();
}

// Ham POST
async function apiPost(endpoint, data) {
    const res = await fetch(API_BASE + endpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    });
    if (!res.ok) throw new Error('Loi ket noi server');
    return res.json();
}

// Ham PUT (cap nhat)
async function apiPut(endpoint, data) {
    const res = await fetch(API_BASE + endpoint, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(data)
    });
    if (!res.ok) throw new Error('Loi ket noi server');
    return res.json();
}

// Ham DELETE
async function apiDelete(endpoint) {
    const res = await fetch(API_BASE + endpoint, { method: 'DELETE' });
    if (!res.ok) throw new Error('Loi ket noi server');
    return res.json();
}

// Hien thi thong bao
function showAlert(message, type = 'success') {
    const el = document.getElementById('alert-message');
    if (!el) return;
    el.textContent = message;
    el.className = `alert alert-${type}`;
    el.style.display = 'block';
    setTimeout(() => { el.style.display = 'none'; }, 3000);
}

// Mo / dong modal
function openModal(modalId) {
    document.getElementById(modalId).classList.add('show');
}
function closeModal() {
    document.querySelectorAll('.modal-overlay').forEach(m => m.classList.remove('show'));
}

// Dinh dang tien VND
function formatMoney(amount) {
    return Number(amount).toLocaleString('vi-VN') + ' VNĐ';
}

// Dinh dang ngay
function formatDate(dateStr) {
    if (!dateStr) return '--';
    return new Date(dateStr).toLocaleDateString('vi-VN');
}

// Kiem tra dang nhap
function checkLogin() {
    const user = sessionStorage.getItem('currentUser');
    if (!user) {
        window.location.href = 'login.html';
    }
    return JSON.parse(user);
}

// Dang xuat
document.addEventListener('DOMContentLoaded', () => {
    const logoutBtn = document.getElementById('logout-btn');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', (e) => {
            e.preventDefault();
            sessionStorage.removeItem('currentUser');
            window.location.href = 'login.html';
        });
    }
});
