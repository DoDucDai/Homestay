checkLogin();
let pendingCheckins = new Map();
let checkinListVersion = 0;
let currentCheckin = null;
let checkinSubmitting = false;
let checkinNeedsReload = false;
const checkinBusy = new Set();

function checkinReason(booking) {
    const today = vietnamToday();
    if (booking.status !== 'Confirmed') return 'Booking không còn ở trạng thái chờ nhận phòng.';
    if (!booking.checkinDate || !booking.checkoutDate) return 'Thiếu ngày nhận/trả phòng. Vui lòng tải lại.';
    if (today < booking.checkinDate.slice(0, 10)) return 'Chưa đến ngày nhận phòng.';
    if (today >= booking.checkoutDate.slice(0, 10)) return 'Đã hết kỳ lưu trú dự kiến. Cần xử lý booking trước khi nhận phòng.';
    return '';
}
async function loadPendingBookings() {
    const version = ++checkinListVersion;
    tableState('checkin-tbody', 8, 'Đang tải booking chờ nhận phòng...');
    try {
        const bookings = expectList(await apiGet('/bookings?status=Confirmed'));
        if (version !== checkinListVersion) return;
        renderTable(bookings);
    } catch (err) {
        if (version !== checkinListVersion) return;
        pendingCheckins = new Map();
        tableState('checkin-tbody', 8, err.message || 'Không tải được danh sách chờ check-in.');
        showRequestError(err, 'Không tải được danh sách chờ check-in.');
    }
}
function renderTable(bookings) {
    pendingCheckins = new Map(bookings.map(b => [b.bookingId, b]));
    const body = document.getElementById('checkin-tbody'); if (!body) return;
    body.replaceChildren();
    if (!bookings.length) { tableState('checkin-tbody', 8, 'Không có khách hàng nào chờ Check-in'); return; }
    bookings.forEach(b => {
        const reason = checkinReason(b);
        const button = actionButton(checkinBusy.has(b.bookingId) ? 'Đang nhận phòng...' : 'Xác nhận Check-in', 'btn btn-success',
            () => doCheckin(b.bookingId, b.roomCode, b.customerName));
        button.disabled = !!reason || checkinBusy.has(b.bookingId); button.title = reason;
        const actions = [button];
        if (reason) actions.push(textNode('p', reason));
        appendTableRow(body, [textNode('strong', 'BK' + b.bookingId), b.customerName || '', b.phone || '', textNode('strong', b.roomCode || ''),
            formatDate(b.checkinDate), formatDate(b.checkoutDate), b.note || ''], actions);
    });
}
function updateCheckinControls() {
    document.getElementById('checkin-confirm-btn').disabled = checkinSubmitting || checkinNeedsReload || !currentCheckin || !!checkinReason(currentCheckin);
    document.getElementById('checkin-confirm-btn').textContent = checkinSubmitting ? 'Đang nhận phòng...' : '✅ Xác nhận Check-in';
    document.getElementById('checkin-close-btn').disabled = checkinSubmitting;
}
function doCheckin(bookingId, roomCode, customerName) {
    if (checkinSubmitting || checkinBusy.has(bookingId)) return;
    const booking = pendingCheckins.get(bookingId);
    if (!booking) { showAlert('Booking không còn trong danh sách. Vui lòng tải lại.', 'error'); return; }
    const reason = checkinReason(booking);
    if (reason) { showAlert(reason, 'error'); return; }
    currentCheckin = booking; checkinNeedsReload = false;
    document.getElementById('checkin-success').hidden = true;
    const info = document.getElementById('checkin-info'); info.replaceChildren();
    for (const [label, value] of [['Booking', 'BK' + bookingId], ['Khách hàng', booking.customerName], ['Phòng', booking.roomCode],
        ['Ngày nhận', formatDate(booking.checkinDate)], ['Ngày trả', formatDate(booking.checkoutDate)], ['Ghi chú', booking.note || '--']]) {
        const line = document.createElement('p'); line.append(textNode('strong', label + ': '), textNode('span', value)); info.appendChild(line);
    }
    document.getElementById('checkin-status').textContent = '';
    updateCheckinControls(); openModal('checkin-modal');
}
function closeCheckinModal() {
    if (checkinSubmitting) return;
    currentCheckin = null; checkinNeedsReload = false; updateCheckinControls();
    document.getElementById('checkin-info').replaceChildren();
    document.getElementById('checkin-modal').classList.remove('show');
}
async function confirmCheckin() {
    if (!currentCheckin || checkinSubmitting || checkinNeedsReload) return;
    const reason = checkinReason(currentCheckin);
    if (reason) { document.getElementById('checkin-status').textContent = reason; updateCheckinControls(); return; }
    const booking = currentCheckin;
    checkinSubmitting = true; checkinBusy.add(booking.bookingId); ++checkinListVersion;
    updateCheckinControls(); renderTable([...pendingCheckins.values()]);
    document.getElementById('checkin-status').textContent = 'Đang xác nhận nhận phòng...';
    try {
        const result = await apiPut('/checkin/' + booking.bookingId, {});
        if (result.success !== true) throw new Error(result.message || 'Chưa xác nhận được việc nhận phòng.');
        checkinSubmitting = false; checkinBusy.delete(booking.bookingId); closeCheckinModal();
        document.getElementById('checkin-success-message').textContent = `Đã nhận phòng cho ${booking.customerName || ''}, phòng ${booking.roomCode || ''}. Quản lý dịch vụ tại trang khách đang ở.`;
        document.getElementById('checkin-success').hidden = false;
        showAlert(result.message || 'Nhận phòng thành công!');
        await loadPendingBookings();
    } catch (err) {
        checkinNeedsReload = true;
        document.getElementById('checkin-status').textContent = (err.message || 'Chưa xác nhận được kết quả nhận phòng.') + ' Vui lòng đóng hộp thoại và chọn lại từ danh sách đã tải mới.';
        showRequestError(err, 'Không thể nhận phòng.');
        await loadPendingBookings();
    } finally {
        checkinSubmitting = false; checkinBusy.delete(booking.bookingId);
        updateCheckinControls();
        if (pendingCheckins.size) renderTable([...pendingCheckins.values()]);
    }
}
document.addEventListener('DOMContentLoaded', async () => {
    if (!await checkLogin()) return;
    loadPendingBookings(); updateCheckinControls();
});
