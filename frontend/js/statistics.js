// ============================================
// statistics.js - XU LY TRANG THONG KE
// ============================================

checkLogin();

document.addEventListener('DOMContentLoaded', () => {
    // Mac dinh set thang hien tai
    const now = new Date();
    document.getElementById('stat-month').value = now.getMonth() + 1;
    document.getElementById('stat-year').value = now.getFullYear();
    
    loadOverview();
    loadRevenue();
});

async function loadOverview() {
    try {
        const data = await apiGet('/statistics/overview');
        
        document.getElementById('stat-total-rooms').textContent = data.totalRooms || 0;
        document.getElementById('stat-total-customers').textContent = data.totalCustomers || 0;
        document.getElementById('stat-total-bookings').textContent = data.bookingsThisMonth || 0;
        document.getElementById('stat-revenue').textContent = formatMoney(data.revenueThisMonth || 0);
        
        // Hien thi trang thai phong
        const tbody = document.getElementById('room-status-tbody');
        if (tbody) {
            tbody.innerHTML = `
                <tr><td>Trống</td><td><strong>${data.availableRooms || 0}</strong></td></tr>
                <tr><td>Đã đặt</td><td><strong>${data.bookedRooms || 0}</strong></td></tr>
                <tr><td>Đang sử dụng</td><td><strong>${data.occupiedRooms || 0}</strong></td></tr>
                <tr><td>Bảo trì</td><td><strong>${data.maintenanceRooms || 0}</strong></td></tr>
            `;
        }
    } catch (err) {
        // Ignored
    }
}

async function loadRevenue() {
    const month = document.getElementById('stat-month').value;
    const year = document.getElementById('stat-year').value;
    
    try {
        const list = await apiGet(`/statistics/revenue?month=${month}&year=${year}`);
        const tbody = document.getElementById('revenue-tbody');
        if (!tbody) return;
        
        tbody.innerHTML = '';
        
        if (!list || list.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" style="text-align:center;">Không có doanh thu trong tháng này</td></tr>';
            return;
        }
        
        let sum = 0;
        list.forEach(inv => {
            sum += (inv.grandTotal || 0);
            const tr = document.createElement('tr');
            tr.innerHTML = `
                <td>HD${inv.paymentId || inv.bookingId}</td>
                <td>${formatDate(inv.paidAt)}</td>
                <td>${inv.customerName || ''}</td>
                <td>${inv.roomCode || ''}</td>
                <td>${formatMoney(inv.roomTotal)}</td>
                <td>${formatMoney(inv.serviceTotal)}</td>
                <td><strong>${formatMoney(inv.grandTotal)}</strong></td>
            `;
            tbody.appendChild(tr);
        });
        
        // Hien thi tong cong
        const trSum = document.createElement('tr');
        trSum.style.backgroundColor = '#f1f1f1';
        trSum.innerHTML = `
            <td colspan="6" style="text-align:right;"><strong>TỔNG CỘNG TRONG THÁNG:</strong></td>
            <td><strong style="color:red; font-size:16px;">${formatMoney(sum)}</strong></td>
        `;
        tbody.appendChild(trSum);
        
    } catch (err) {
        showAlert('Lỗi khi tải báo cáo doanh thu', 'error');
    }
}
