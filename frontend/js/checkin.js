// ============================================
// checkin.js - XU LY TRANG CHECK-IN
// ============================================

checkLogin();

document.addEventListener('DOMContentLoaded', () => {
    loadPendingBookings();
});

async function loadPendingBookings() {
    try {
        // Lay cac booking dang cho check-in (Confirmed)
        const bookings = await apiGet('/bookings?status=Confirmed');
        renderTable(bookings);
    } catch (err) {
        showAlert('Lỗi tải danh sách chờ check-in', 'error');
    }
}

function renderTable(bookings) {
    const tbody = document.getElementById('checkin-tbody');
    if (!tbody) return;
    tbody.innerHTML = '';
    
    if (bookings.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;">Không có khách hàng nào chờ Check-in</td></tr>';
        return;
    }
    
    bookings.forEach(b => {
        const tr = document.createElement('tr');
        tr.innerHTML = `
            <td><strong>BK${b.bookingId}</strong></td>
            <td>${b.customerName || ''}</td>
            <td>${b.phone || ''}</td>
            <td><strong>${b.roomCode || ''}</strong></td>
            <td>${formatDate(b.checkinDate)}</td>
            <td>${b.note || ''}</td>
            <td>
                <button class="btn btn-success" onclick="doCheckin(${b.bookingId}, '${b.roomCode}', '${b.customerName}')">Xác nhận Check-in</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

async function doCheckin(bookingId, roomCode, customerName) {
    if (!confirm(`Xác nhận Check-in cho khách [${customerName}] vào phòng [${roomCode}]?`)) return;
    
    try {
        const res = await apiPut('/checkin/' + bookingId, {});
        if (res.success !== false) {
            showAlert(res.message || 'Check-in thành công!', 'success');
            loadPendingBookings();
        } else {
            showAlert(res.message, 'error');
        }
    } catch (err) {
        showAlert('Có lỗi khi check-in!', 'error');
    }
}
