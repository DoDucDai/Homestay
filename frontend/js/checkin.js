// NOTE FRONTEND: Màn hình nhận phòng: phân nhóm lượt đặt và chỉ xác nhận khi trạng thái/thời điểm cho phép.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
checkLogin();
let pendingCheckins = new Map();
let checkinListVersion = 0;
let currentCheckin = null;
let checkinSubmitting = false;
let checkinNeedsReload = false;
const checkinBusy = new Set();
// NOTE FRONTEND: Phân booking vào nhóm ngày nhận để tạo các thẻ lọc nhanh trên trang.
function checkinGroup(booking) {
    const reason=checkinReason(booking);
    if(!reason)return 'ready';
    const start=booking.billingUnit==='Hour'?booking.checkinAt:booking.checkinDate;
    const now=booking.billingUnit==='Hour'?vietnamNow():vietnamToday();
    return start && now<start?'upcoming':'attention';
}
// NOTE FRONTEND: Kết hợp từ khóa và nhóm ngày/trạng thái trên danh sách cần nhận.
function filterPendingCheckins(bookings,search,group) {
    const query=search.trim().toLocaleLowerCase('vi');
    const rank={ready:0,upcoming:1,attention:2};
    return bookings.filter(b=>(!group || group==='all' || checkinGroup(b)===group) &&
        (!query || ['BK'+b.bookingId,b.customerName||'',b.phone||'',b.roomCode||''].some(value=>value.toLocaleLowerCase('vi').includes(query))))
        .sort((a,b)=>rank[checkinGroup(a)]-rank[checkinGroup(b)] || String(a.checkinAt||a.checkinDate).localeCompare(String(b.checkinAt||b.checkinDate)));
}
// NOTE FRONTEND: Dựng lại bảng từ Map booking đang có sau khi bộ lọc thay đổi.
function refreshCheckinView() { renderTable([...pendingCheckins.values()]); }

// NOTE FRONTEND: Trả lý do không thể nhận phòng, gồm trạng thái, bảo trì hoặc ngoài khung thời gian đã đặt.
function checkinReason(booking) {
    const today = vietnamToday();
    if (booking.status !== 'Confirmed') return 'Booking không còn ở trạng thái chờ nhận phòng.';
    if (booking.billingUnit === 'Hour') {
        if(!booking.checkinAt || !booking.checkoutAt) return 'Thiếu giờ nhận/trả phòng.';
        if(vietnamNow()<booking.checkinAt) return 'Chưa đến giờ nhận phòng.';
        if(vietnamNow()>=booking.checkoutAt) return 'Đã hết khung giờ dự kiến. Cần xử lý booking.';
        return '';
    }
    if (!booking.checkinDate || !booking.checkoutDate) return 'Thiếu ngày nhận/trả phòng. Vui lòng tải lại.';
    if (today < booking.checkinDate.slice(0, 10)) return 'Chưa đến ngày nhận phòng.';
    if (today >= booking.checkoutDate.slice(0, 10)) return 'Đã hết kỳ lưu trú dự kiến. Cần xử lý booking trước khi nhận phòng.';
    return '';
}
// NOTE FRONTEND: Tải dữ liệu booking cần xử lý, bỏ phản hồi cũ và cập nhật các nhóm trên màn hình.
async function loadPendingBookings() {
    const version = ++checkinListVersion;
    tableState('checkin-tbody', 6, 'Đang tải booking chờ nhận phòng...');
    document.getElementById('checkin-result').textContent = 'Đang tải danh sách…';
    try {
        const bookings = expectList(await apiGet('/bookings?status=Confirmed'));
        if (version !== checkinListVersion) return;
        renderTable(bookings);
    } catch (err) {
        if (version !== checkinListVersion) return;
        pendingCheckins = new Map();
        document.getElementById('checkin-result').textContent = 'Chưa tải được danh sách. Hãy thử tải lại.';
        for(const id of ['checkin-count-all','checkin-count-ready','checkin-count-upcoming','checkin-count-attention'])document.getElementById(id).textContent='—';
        tableState('checkin-tbody', 6, err.message || 'Không tải được danh sách chờ check-in.');
        showRequestError(err, 'Không tải được danh sách chờ check-in.');
    }
}
// NOTE FRONTEND: Dựng các lượt cần nhận và nút thao tác; nút không hợp lệ được vô hiệu kèm lý do.
function renderTable(bookings) {
    pendingCheckins = new Map(bookings.map(b => [b.bookingId, b]));
    const body = document.getElementById('checkin-tbody'); if (!body) return;
    body.replaceChildren();
    for(const group of ['all','ready','upcoming','attention'])document.getElementById('checkin-count-'+group).textContent=String(group==='all'?bookings.length:bookings.filter(b=>checkinGroup(b)===group).length);
    const rows=filterPendingCheckins(bookings,document.getElementById('checkin-search').value,document.getElementById('checkin-filter').value);
    document.getElementById('checkin-result').textContent = rows.length+'/'+bookings.length+' lượt chờ nhận phòng · Ngày '+formatDate(vietnamToday());
    if (!rows.length) { tableState('checkin-tbody', 6, bookings.length?'Không có lượt phù hợp với bộ lọc.':'Không có khách hàng nào chờ nhận phòng.'); return; }
    pageRows('checkin', rows, refreshCheckinView).forEach(b => {
        const reason = checkinReason(b);
        const button = actionButton(checkinBusy.has(b.bookingId) ? 'Đang nhận phòng...' : 'Nhận phòng', 'btn btn-primary',
            () => doCheckin(b.bookingId, b.roomCode, b.customerName));
        button.disabled = !!reason || checkinBusy.has(b.bookingId); button.title = reason;
        button.setAttribute('aria-label',button.textContent+' BK'+b.bookingId);
        const actions = [button];
        const state=textNode('div','');
        const group=checkinGroup(b);
        state.append(textNode('span',({ready:'Đến lịch nhận',upcoming:'Chưa đến lịch',attention:'Cần xử lý'})[group],'badge '+({ready:'badge-success',upcoming:'badge-info',attention:'badge-neutral'})[group]));
        if (reason) {
            const description = textNode('p',reason,'checkin-reason'); description.id = 'checkin-reason-'+b.bookingId;
            button.setAttribute('aria-describedby',description.id); state.append(description);
        }
        const identity=textNode('div','','checkin-customer');identity.append(textNode('strong',b.customerName||'Chưa có tên'),textNode('span',b.phone||'Chưa có điện thoại','checkin-secondary'));
        const code=textNode('div','');code.append(textNode('strong','BK'+b.bookingId),textNode('span',b.billingUnit==='Hour'?'Theo giờ':'Qua đêm','checkin-secondary'));
        if(b.note){const note=textNode('details','','checkin-note');note.append(textNode('summary','Ghi chú'),textNode('p',b.note));code.append(note);}
        const time=textNode('div','','checkin-time');time.append(textNode('span','Nhận · '+bookingTime(b,'checkin')),textNode('span','Trả · '+bookingTime(b,'checkout')));
        const row=appendTableRow(body,[code,identity,textNode('strong',b.roomCode||''),time,state],actions);
        Array.from(row.children).forEach((cell,i)=>cell.setAttribute('data-label',['Booking','Khách hàng','Phòng','Thời gian','Tình trạng','Thao tác'][i]));
    });
}
// NOTE FRONTEND: Khóa/đổi nhãn nút xác nhận, hủy và trạng thái modal khi đang gửi nhận phòng.
function updateCheckinControls() {
    document.getElementById('checkin-confirm-btn').disabled = checkinSubmitting || checkinNeedsReload || !currentCheckin || !!checkinReason(currentCheckin);
    document.getElementById('checkin-confirm-btn').textContent = checkinSubmitting ? 'Đang nhận phòng...' : 'Xác nhận nhận phòng';
    document.getElementById('checkin-confirm-btn').setAttribute('aria-busy',String(checkinSubmitting));
    document.getElementById('checkin-close-btn').disabled = checkinSubmitting;
}
// NOTE FRONTEND: Mở bước xác nhận với đúng bookingId, mã phòng và tên khách đã chọn.
function doCheckin(bookingId, roomCode, customerName) {
    if (checkinSubmitting || checkinBusy.has(bookingId)) return;
    const booking = pendingCheckins.get(bookingId);
    if (!booking) { showAlert('Booking không còn trong danh sách. Vui lòng tải lại.', 'error'); return; }
    const reason = checkinReason(booking);
    if (reason) { showAlert(reason, 'error'); return; }
    currentCheckin = booking; checkinNeedsReload = false;
    document.getElementById('checkin-success').hidden = true;
    const info = document.getElementById('checkin-info'); info.replaceChildren();
    for (const [label, value] of [['Booking', 'BK' + bookingId], ['Khách hàng', booking.customerName], ['Điện thoại', booking.phone || '--'], ['Phòng', booking.roomCode],
        ['Ngày nhận', bookingTime(booking,'checkin')], ['Ngày trả', bookingTime(booking,'checkout')], ['Ghi chú', booking.note || '--']]) {
        const line = document.createElement('div'); line.append(textNode('dt',label),textNode('dd',value)); info.appendChild(line);
    }
    document.getElementById('checkin-status').textContent = '';
    updateCheckinControls(); openModal('checkin-modal');
}
// NOTE FRONTEND: Không cho đóng khi server đang ghi để tránh gửi trùng hoặc mất dấu kết quả.
function closeCheckinModal() {
    if (checkinSubmitting) return;
    currentCheckin = null; checkinNeedsReload = false; updateCheckinControls();
    document.getElementById('checkin-info').replaceChildren();
    document.getElementById('checkin-modal').classList.remove('show');
}
// NOTE FRONTEND: Gửi PUT /checkin/{id}, nhận kết quả và tải lại dữ liệu; quyền và điều kiện cuối cùng do backend kiểm tra.
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
    document.getElementById('checkin-search').addEventListener('input',refreshCheckinView);
    document.getElementById('checkin-filter').addEventListener('change',refreshCheckinView);
    loadPendingBookings(); updateCheckinControls();
});
