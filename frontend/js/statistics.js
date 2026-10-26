checkLogin();
document.addEventListener('DOMContentLoaded', async () => {
    if (!await checkLogin()) return;
    const now = new Date();
    document.getElementById('stat-month').value = now.getMonth() + 1;
    document.getElementById('stat-year').value = now.getFullYear();
    loadOverview(); loadRevenue();
});
async function loadOverview() {
    const ids = ['stat-total-rooms', 'stat-total-customers', 'stat-total-bookings', 'stat-revenue'];
    ids.forEach(id => { document.getElementById(id).textContent = '--'; });
    tableState('room-status-tbody', 2, 'Đang tải dữ liệu...');
    try {
        const data = await apiGet('/statistics/overview');
        for (const key of ['totalRooms', 'totalCustomers', 'totalBookingsThisMonth', 'revenueThisMonth', 'availableRooms', 'bookedRooms', 'occupiedRooms', 'maintenanceRooms']) {
            if (!Number.isFinite(data[key]) || data[key] < 0) throw new Error('Dữ liệu thống kê không hợp lệ.');
        }
        document.getElementById('stat-total-rooms').textContent = data.totalRooms;
        document.getElementById('stat-total-customers').textContent = data.totalCustomers;
        document.getElementById('stat-total-bookings').textContent = data.totalBookingsThisMonth;
        document.getElementById('stat-revenue').textContent = formatMoney(data.revenueThisMonth);
        const body = document.getElementById('room-status-tbody');
        body.replaceChildren();
        for (const [label, key] of [['Trống','availableRooms'],['Đã đặt','bookedRooms'],['Đang sử dụng','occupiedRooms'],['Bảo trì','maintenanceRooms']]) {
            appendTableRow(body, [label, textNode('strong', data[key])]);
        }
    } catch (err) {
        ids.forEach(id => { document.getElementById(id).textContent = '--'; });
        tableState('room-status-tbody', 2, err.message || 'Không tải được thống kê.');
        showRequestError(err, 'Không tải được thống kê.');
    }
}
async function loadRevenue() {
    tableState('revenue-tbody', 7, 'Đang tải dữ liệu...');
    try {
        const month = Number(document.getElementById('stat-month').value);
        const year = Number(document.getElementById('stat-year').value);
        if (!Number.isInteger(month) || month < 1 || month > 12 || !Number.isInteger(year) || year < 1 || year > 9999) throw new Error('Tháng cần từ 1–12, năm cần từ 1–9999.');
        const list = expectList(await apiGet(`/statistics/revenue?month=${month}&year=${year}`));
        const body = document.getElementById('revenue-tbody');
        body.replaceChildren();
        if (!list.length) { tableState('revenue-tbody', 7, 'Không có doanh thu trong tháng này'); return; }
        let sum = 0n;
        list.forEach(inv => {
            if (!Number.isSafeInteger(inv.grandTotal) || inv.grandTotal < 0) throw new Error('Số tiền báo cáo không hợp lệ.');
            sum += BigInt(inv.grandTotal);
            appendTableRow(body, ['HD' + (inv.paymentId || inv.bookingId), formatDate(inv.paidAt), inv.customerName || '', inv.roomCode || '',
                formatMoney(inv.roomTotal), formatMoney(inv.serviceTotal), textNode('strong', formatMoney(inv.grandTotal))]);
        });
        const total = appendTableRow(body, [textNode('strong', 'TỔNG CỘNG TRONG THÁNG:'), textNode('strong', formatMoney(sum))]);
        total.firstElementChild.colSpan = 6;
    } catch (err) {
        tableState('revenue-tbody', 7, err.message || 'Không tải được báo cáo doanh thu.');
        showRequestError(err, 'Không tải được báo cáo doanh thu.');
    }
}
