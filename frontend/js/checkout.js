// ============================================
// checkout.js - XU LY TRANG CHECK-OUT VA THANH TOAN
// ============================================

checkLogin();

let currentCheckoutBookingId = null;

document.addEventListener('DOMContentLoaded', () => {
    loadCheckedInBookings();
});

async function loadCheckedInBookings() {
    try {
        const bookings = await apiGet('/bookings?status=CheckedIn');
        renderTable(bookings);
    } catch (err) {
        showAlert('Lỗi tải danh sách khách đang lưu trú', 'error');
    }
}

function renderTable(bookings) {
    const tbody = document.getElementById('checkout-tbody');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    if (bookings.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;">Không có khách nào đang lưu trú</td></tr>';
        return;
    }
    
    bookings.forEach(b => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>BK${b.bookingId}</strong></td>
            <td>${b.customerName || ''}</td>
            <td><strong>${b.roomCode || ''}</strong></td>
            <td>${formatDate(b.checkinDate)}</td>
            <td>${formatDate(b.checkoutDate)}</td>
            <td>
                <!-- Nut them dich vu (mo rong neu can) -->
                <button class="btn btn-primary" onclick="openCheckoutModal(${b.bookingId})">Thanh toán & Check-out</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

async function openCheckoutModal(bookingId) {
    currentCheckoutBookingId = bookingId;
    openModal('checkout-modal');
    
    // Reset tam thoi
    document.getElementById('inv-id').textContent = 'Đang tính toán...';
    
    try {
        const preview = await apiGet('/checkout/preview/' + bookingId);
        
        document.getElementById('inv-id').textContent = 'BK' + preview.bookingId;
        document.getElementById('inv-customer').textContent = preview.customerName || '';
        document.getElementById('inv-room').textContent = preview.roomCode || '';
        document.getElementById('inv-checkin').textContent = formatDate(preview.checkinDate);
        document.getElementById('inv-checkout').textContent = formatDate(preview.checkoutDate);
        document.getElementById('inv-nights').textContent = preview.nights || 1;
        document.getElementById('inv-price-per-night').textContent = formatMoney(preview.roomPrice || 0);
        document.getElementById('inv-room-total').textContent = formatMoney(preview.roomTotal || 0);
        document.getElementById('inv-service-total').textContent = formatMoney(preview.serviceTotal || 0);
        
        document.getElementById('inv-grand-total').textContent = formatMoney(preview.grandTotal || 0);
        
    } catch (err) {
        showAlert('Lỗi khi tải dữ liệu hóa đơn tạm tính!', 'error');
        closeModal();
    }
}

async function confirmCheckout() {
    if (!currentCheckoutBookingId) return;
    
    const paymentMethod = document.getElementById('payment-method').value;
    
    if (!confirm('Bạn có chắc chắn muốn xuất hóa đơn và trả phòng không?')) return;
    
    try {
        const res = await apiPost('/checkout/' + currentCheckoutBookingId, { paymentMethod });
        if (res.success !== false) {
            showAlert('Thanh toán thành công! Phòng đã được giải phóng.', 'success');
            closeModal();
            loadCheckedInBookings();
        } else {
            showAlert(res.message, 'error');
        }
    } catch (err) {
        showAlert('Có lỗi khi Check-out!', 'error');
    }
}
