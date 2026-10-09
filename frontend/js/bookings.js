// ============================================
// bookings.js - XU LY TRANG BOOKING
// ============================================

checkLogin();

document.addEventListener('DOMContentLoaded', () => {
    loadBookings();
    
    // Khi mo modal thi can load ds khach hang va ds phong trong
    document.getElementById('booking-room').addEventListener('change', calculateEstimate);
    document.getElementById('booking-checkin-date').addEventListener('change', calculateEstimate);
    document.getElementById('booking-checkout-date').addEventListener('change', calculateEstimate);
});

async function loadBookings() {
    try {
        const bookings = await apiGet('/bookings');
        renderTable(bookings);
    } catch (err) {
        showAlert('Lỗi khi tải danh sách booking!', 'error');
    }
}

function renderTable(bookings) {
    const tbody = document.getElementById('bookings-tbody');
    if (!tbody) return;
    
    tbody.innerHTML = '';
    
    if (bookings.length === 0) {
        tbody.innerHTML = '<tr><td colspan="8" style="text-align:center;">Không có booking nào</td></tr>';
        return;
    }
    
    bookings.forEach(b => {
        const tr = document.createElement('tr');
        
        let statusBadge = 'bg-secondary';
        if (b.status === 'Confirmed') statusBadge = 'bg-primary';
        if (b.status === 'CheckedIn') statusBadge = 'bg-warning';
        if (b.status === 'CheckedOut') statusBadge = 'bg-success';
        
        tr.innerHTML = `
            <td><strong>BK${b.bookingId}</strong></td>
            <td>${b.customerName || b.fullName || ''}</td>
            <td>${b.roomCode || ''}</td>
            <td>${formatDate(b.checkinDate)}</td>
            <td>${formatDate(b.checkoutDate)}</td>
            <td><span class="badge ${statusBadge}">${b.status}</span></td>
            <td>${b.note || ''}</td>
            <td>
                <!-- Chi booking nao chua check-in moi duoc phep Huy/Xoa -->
            </td>
        `;
        tbody.appendChild(tr);
    });
}

async function openAddModal() {
    document.getElementById('booking-customer').innerHTML = '<option value="">-- Đang tải --</option>';
    document.getElementById('booking-room').innerHTML = '<option value="">-- Đang tải --</option>';
    
    openModal('booking-modal');
    
    // Load dropdown
    try {
        const customers = await apiGet('/customers');
        const rooms = await apiGet('/rooms');
        
        // Render customers
        let custHtml = '<option value="">-- Chọn khách hàng --</option>';
        customers.forEach(c => {
            custHtml += `<option value="${c.customerId}">${c.fullName} - ${c.cccd}</option>`;
        });
        document.getElementById('booking-customer').innerHTML = custHtml;
        
        // Render rooms (chi hien thi phong trong)
        let roomHtml = '<option value="" data-price="0">-- Chọn phòng --</option>';
        rooms.filter(r => r.status === 'Trống').forEach(r => {
            roomHtml += `<option value="${r.roomId}" data-price="${r.price}">${r.roomCode} - ${r.roomName} (${formatMoney(r.price)})</option>`;
        });
        document.getElementById('booking-room').innerHTML = roomHtml;
        
        // Reset form
        const today = new Date().toISOString().split('T')[0];
        document.getElementById('booking-checkin-date').value = today;
        
        const tomorrow = new Date();
        tomorrow.setDate(tomorrow.getDate() + 1);
        document.getElementById('booking-checkout-date').value = tomorrow.toISOString().split('T')[0];
        
        document.getElementById('booking-note').value = '';
        document.getElementById('price-preview').style.display = 'none';
        
    } catch (err) {
        showAlert('Lỗi tải dữ liệu cho form đặt phòng!', 'error');
    }
}

function calculateEstimate() {
    const roomSelect = document.getElementById('booking-room');
    const checkin = document.getElementById('booking-checkin-date').value;
    const checkout = document.getElementById('booking-checkout-date').value;
    
    if (!roomSelect.value || !checkin || !checkout) {
        document.getElementById('price-preview').style.display = 'none';
        return;
    }
    
    const d1 = new Date(checkin);
    const d2 = new Date(checkout);
    
    let nights = (d2 - d1) / (1000 * 60 * 60 * 24);
    if (nights <= 0) nights = 1; // It nhat 1 dem
    
    const option = roomSelect.options[roomSelect.selectedIndex];
    const price = Number(option.getAttribute('data-price') || 0);
    
    document.getElementById('estimated-nights').textContent = nights;
    document.getElementById('room-price-display').textContent = formatMoney(price);
    document.getElementById('total-price-display').textContent = formatMoney(nights * price);
    document.getElementById('price-preview').style.display = 'block';
}

async function saveBooking() {
    const customerId = document.getElementById('booking-customer').value;
    const roomId = document.getElementById('booking-room').value;
    const checkinDate = document.getElementById('booking-checkin-date').value;
    const checkoutDate = document.getElementById('booking-checkout-date').value;
    const note = document.getElementById('booking-note').value.trim();
    
    if (!customerId || !roomId || !checkinDate || !checkoutDate) {
        showAlert('Vui lòng nhập đầy đủ thông tin bắt buộc!', 'error');
        return;
    }
    
    if (new Date(checkinDate) >= new Date(checkoutDate)) {
        showAlert('Ngày trả phòng phải sau ngày nhận phòng!', 'error');
        return;
    }
    
    const data = {
        customerId: Number(customerId),
        roomId: Number(roomId),
        checkinDate,
        checkoutDate,
        note
    };
    
    try {
        const res = await apiPost('/bookings', data);
        if (res.success !== false) {
            showAlert('Đặt phòng thành công!', 'success');
            closeModal();
            loadBookings();
        } else {
            showAlert(res.message, 'error');
        }
    } catch (err) {
        showAlert('Có lỗi xảy ra khi tạo booking!', 'error');
    }
}
