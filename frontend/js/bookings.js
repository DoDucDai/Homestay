checkLogin();
let bookingRows = [];
let bookingListVersion = 0;
let bookingTableReady = false;
const cancellingBookings = new Set();
let bookingModalOpen = false;
let bookingSubmitting = false;
let customerVersion = 0;
let availabilityVersion = 0;
let customersReady = false;
let availableRange = '';
let pendingRange = '';
let pendingAvailability = null;
let bookingCustomers = new Map();
let bookingRooms = new Map();

function dateRangeKey() {
    return document.getElementById('booking-checkin-date').value + '|' + document.getElementById('booking-checkout-date').value;
}
function updateBookingControls() {
    const room = bookingRooms.get(Number(document.getElementById('booking-room').value));
    const customer = bookingCustomers.get(Number(document.getElementById('booking-customer').value));
    let ready = bookingModalOpen && !bookingSubmitting && customersReady && availableRange === dateRangeKey() && !!availableRange && !!room && !!customer;
    if (ready) {
        try {
            const checkin = document.getElementById('booking-checkin-date').value;
            const checkout = document.getElementById('booking-checkout-date').value;
            validBookingDates(checkin, checkout);
            validMoney((Date.parse(checkout + 'T00:00:00Z') - Date.parse(checkin + 'T00:00:00Z')) / 86400000
                * validMoney(room.price, 'Giá phòng', 1), 'Tiền phòng', 1);
        } catch { ready = false; }
    }
    document.getElementById('booking-save-btn').disabled = !ready;
    document.getElementById('booking-save-btn').textContent = bookingSubmitting ? 'Đang đặt phòng...' : 'Xác nhận booking';
    document.getElementById('booking-save-btn').setAttribute('aria-busy',String(bookingSubmitting));
    for (const id of ['booking-close-btn', 'booking-customer', 'booking-checkin-date', 'booking-checkout-date', 'booking-note', 'booking-reload-btn', 'booking-hour-switch']) {
        document.getElementById(id).disabled = bookingSubmitting;
    }
    document.getElementById('booking-room').disabled = bookingSubmitting || !availableRange || !bookingRooms.size;
    document.getElementById('booking-customer').disabled = bookingSubmitting || !customersReady;
}
async function loadBookings() {
    if (typeof loadCalendarData === 'function') return loadCalendarData(true);
    const version = ++bookingListVersion;
    bookingTableReady = false;
    tableState('bookings-tbody', 8, 'Đang tải booking...');
    document.getElementById('bookings-result').textContent = 'Đang tải booking…';
    try {
        const rows = expectList(await apiGet('/bookings'));
        if (version !== bookingListVersion) return;
        renderTable(rows);
    } catch (err) {
        if (version !== bookingListVersion) return;
        bookingRows = [];
        document.getElementById('bookings-result').textContent = 'Chưa tải được danh sách. Hãy thử tải lại.';
        tableState('bookings-tbody', 8, err.message || 'Không tải được danh sách booking.');
        showRequestError(err, 'Không tải được danh sách booking.');
    }
}
function renderTable(bookings) {
    bookingRows = bookings;
    bookingTableReady = true;
    filterBookings();
    if (typeof renderBookingCalendar === 'function') renderBookingCalendar();
}
// Adapted from Long's booking search/status UX; keep the existing API and business rules.
function filterBookings() {
    if (!bookingTableReady) return;
    const query = document.getElementById('booking-search').value.trim().toLocaleLowerCase('vi');
    const status = document.getElementById('booking-status-filter').value;
    const filtered = bookingRows.filter(b => (!status || b.status === status) &&
        (!query || ['BK' + b.bookingId, b.customerName || b.fullName || '', b.roomCode || '']
            .some(value => String(value).toLocaleLowerCase('vi').includes(query))));
    renderBookingRows(filtered);
    if (typeof renderBookingCalendar === 'function') renderBookingCalendar();
}
function renderBookingRows(bookings) {
    const body = document.getElementById('bookings-tbody'); if (!body) return;
    body.replaceChildren();
    document.getElementById('bookings-result').textContent = bookings.length+' booking theo bộ lọc · Tổng '+bookingRows.length+' booking';
    if (!bookings.length) {
        const filtered = document.getElementById('booking-search').value.trim() || document.getElementById('booking-status-filter').value;
        tableState('bookings-tbody', 8, filtered ? 'Không có booking phù hợp. Hãy thay đổi từ khóa hoặc trạng thái.' : 'Chưa có booking trong danh sách.'); return;
    }
    pageRows('bookings', bookings, renderBookingRows).forEach(b => {
        const actions = [];
        if (b.status === 'Confirmed') {
            const cancel = actionButton(cancellingBookings.has(b.bookingId) ? 'Đang hủy...' : 'Hủy đặt phòng', 'btn btn-danger', () => cancelBooking(b.bookingId));
            cancel.disabled = cancellingBookings.has(b.bookingId); actions.push(cancel);
            cancel.setAttribute('aria-label',cancel.textContent+' BK'+b.bookingId);
        }
        appendTableRow(body, [textNode('strong', 'BK' + b.bookingId), b.customerName || b.fullName || '', b.roomCode || '',
            bookingTime(b,'checkin'), bookingTime(b,'checkout'), textNode('span',
                ({Confirmed:'Đã xác nhận',CheckedIn:'Đã nhận phòng',CheckedOut:'Đã trả phòng',Cancelled:'Đã hủy'}[b.status] || b.status), 'badge ' +
                ({ Confirmed: 'bg-primary', CheckedIn: 'bg-warning', CheckedOut: 'bg-success' }[b.status] || 'bg-secondary')), b.note || ''], actions);
    });
}
async function cancelBooking(bookingId) {
    const row = bookingRows.find(b => b.bookingId === bookingId && b.status === 'Confirmed');
    if (!row || cancellingBookings.has(bookingId)) return;
    if (!await askConfirmation(`Hủy BK${bookingId} của khách ${row.customerName || ''}, phòng ${row.roomCode || ''}?`)) return;
    if (cancellingBookings.has(bookingId)) return;
    cancellingBookings.add(bookingId); ++bookingListVersion; renderTable(bookingRows);
    try {
        const result = await apiPut('/bookings/' + bookingId + '/cancel');
        if (result.success !== true) throw new Error(result.message || 'Chưa xác nhận được việc hủy booking.');
        showAlert(result.message || 'Đã hủy đặt phòng!');
    } catch (err) { showRequestError(err, 'Không thể hủy booking.'); }
    finally {
        cancellingBookings.delete(bookingId);
        await loadBookings();
        if (bookingModalOpen && !bookingSubmitting) await refreshAvailableRooms(true);
    }
}
async function loadBookingCustomers() {
    const version = ++customerVersion;
    const select = document.getElementById('booking-customer');
    const previous = select.value;
    customersReady = false; bookingCustomers = new Map();
    select.replaceChildren(selectOption('', '-- Đang tải khách hàng --'));
    document.getElementById('booking-customer-status').textContent = 'Đang tải khách hàng...';
    updateBookingControls();
    try {
        const rows = expectList(await apiGet('/customers'));
        if (!bookingModalOpen || version !== customerVersion) return;
        bookingCustomers = new Map(rows.map(c => [c.customerId, c]));
        select.replaceChildren(selectOption('', rows.length ? '-- Chọn khách hàng --' : '-- Chưa có khách hàng --'));
        rows.forEach(c => select.appendChild(selectOption(c.customerId, `${c.fullName} - ${c.cccd}`)));
        if (bookingCustomers.has(Number(previous))) select.value = previous;
        customersReady = true;
        document.getElementById('booking-customer-status').textContent = rows.length ? '' : 'Chưa có khách hàng. Thêm khách hàng trước khi đặt phòng.';
    } catch (err) {
        if (!bookingModalOpen || version !== customerVersion) return;
        select.replaceChildren(selectOption('', '-- Không tải được khách hàng --'));
        document.getElementById('booking-customer-status').textContent = err.message || 'Không tải được khách hàng.';
    } finally { if (bookingModalOpen && version === customerVersion) updateBookingControls(); }
}
function refreshAvailableRooms(force = false) {
    if (!bookingModalOpen || bookingSubmitting) return Promise.resolve();
    const key = dateRangeKey();
    if (!force && key === pendingRange && pendingAvailability) return pendingAvailability;
    if (!force && key === availableRange) { calculateEstimate(); updateBookingControls(); return Promise.resolve(); }
    const version = ++availabilityVersion;
    const select = document.getElementById('booking-room');
    const previous = select.value;
    availableRange = ''; pendingRange = ''; bookingRooms = new Map();
    select.replaceChildren(selectOption('', '-- Chọn ngày hợp lệ trước --', 0));
    document.getElementById('price-preview').style.display = 'none';
    updateBookingControls();
    const checkin = document.getElementById('booking-checkin-date').value;
    const checkout = document.getElementById('booking-checkout-date').value;
    try { validBookingDates(checkin, checkout); }
    catch (err) { document.getElementById('booking-room-status').textContent = err.message; return Promise.resolve(); }
    document.getElementById('booking-checkout-date').min = new Date(Date.parse(checkin + 'T00:00:00Z') + 86400000).toISOString().slice(0, 10);
    document.getElementById('booking-room-status').textContent = 'Đang tìm phòng trống theo ngày...';
    select.replaceChildren(selectOption('', '-- Đang tìm phòng --', 0));
    pendingRange = key;
    pendingAvailability = (async () => {
        try {
            const rooms = expectList(await apiGet('/bookings/available-rooms?checkin=' + encodeURIComponent(checkin) + '&checkout=' + encodeURIComponent(checkout)));
            if (!bookingModalOpen || version !== availabilityVersion || key !== dateRangeKey()) return;
            for (const room of rooms) {
                if (!Number.isInteger(room.roomId) || room.roomId <= 0 || !Number.isSafeInteger(Number(room.price)) || Number(room.price) <= 0) throw new Error('Dữ liệu phòng trống không hợp lệ.');
            }
            bookingRooms = new Map(rooms.map(r => [r.roomId, r])); availableRange = key;
            select.replaceChildren(selectOption('', rooms.length ? '-- Chọn phòng --' : '-- Không có phòng trống --', 0));
            rooms.forEach(r => select.appendChild(selectOption(r.roomId, `${r.roomCode} - ${r.roomName} (${formatMoney(r.price)})`, r.price)));
            if (bookingRooms.has(Number(previous))) select.value = previous;
            document.getElementById('booking-room-status').textContent = rooms.length
                ? `Có ${rooms.length} phòng phù hợp khoảng ngày đã chọn.` : 'Không có phòng trống trong khoảng ngày này. Vui lòng chọn ngày khác.';
            calculateEstimate();
        } catch (err) {
            if (!bookingModalOpen || version !== availabilityVersion || key !== dateRangeKey()) return;
            bookingRooms = new Map(); availableRange = '';
            select.replaceChildren(selectOption('', '-- Không tải được phòng trống --', 0));
            document.getElementById('booking-room-status').textContent = err.message || 'Không tải được phòng trống. Bấm Tải lại dữ liệu để thử lại.';
        } finally {
            if (version === availabilityVersion) {
                pendingRange = ''; pendingAvailability = null;
                if (bookingModalOpen) updateBookingControls();
            }
        }
    })();
    return pendingAvailability;
}
async function openAddModal() {
    if (bookingSubmitting) return;
    bookingModalOpen = true; ++customerVersion; ++availabilityVersion;
    customersReady = false; availableRange = ''; pendingRange = ''; pendingAvailability = null;
    bookingCustomers = new Map(); bookingRooms = new Map();
    document.getElementById('booking-customer').replaceChildren(selectOption('', '-- Chọn khách hàng --'));
    document.getElementById('booking-room').replaceChildren(selectOption('', '-- Chọn ngày --', 0));
    document.getElementById('booking-checkin-date').value = vietnamToday();
    document.getElementById('booking-checkin-date').min = vietnamToday();
    document.getElementById('booking-checkout-date').value = vietnamToday(1);
    document.getElementById('booking-note').value = '';
    document.getElementById('booking-status').textContent = '';
    document.getElementById('price-preview').style.display = 'none';
    openModal('booking-modal'); updateBookingControls();
    await reloadBookingData();
}
function closeBookingModal() {
    if (bookingSubmitting) return;
    bookingModalOpen = false; ++customerVersion; ++availabilityVersion;
    customersReady = false; availableRange = ''; pendingRange = ''; pendingAvailability = null;
    bookingCustomers = new Map(); bookingRooms = new Map();
    document.getElementById('price-preview').style.display = 'none'; updateBookingControls();
    document.getElementById('booking-modal').classList.remove('show');
}
async function reloadBookingData() {
    if (!bookingModalOpen || bookingSubmitting) return;
    await Promise.all([loadBookingCustomers(), refreshAvailableRooms(true)]);
}
function calculateEstimate() {
    const room = bookingRooms.get(Number(document.getElementById('booking-room').value));
    const preview = document.getElementById('price-preview'); preview.style.display = 'none';
    if (!room || availableRange !== dateRangeKey()) return;
    try {
        const checkin = document.getElementById('booking-checkin-date').value;
        const checkout = document.getElementById('booking-checkout-date').value;
        validBookingDates(checkin, checkout);
        const nights = (Date.parse(checkout + 'T00:00:00Z') - Date.parse(checkin + 'T00:00:00Z')) / 86400000;
        const price = validMoney(room.price, 'Giá phòng', 1);
        const total = validMoney(nights * price, 'Tiền phòng', 1);
        document.getElementById('estimated-nights').textContent = nights;
        document.getElementById('room-price-display').textContent = formatMoney(price);
        document.getElementById('total-price-display').textContent = formatMoney(total);
        preview.style.display = 'block';
    } catch (err) { document.getElementById('booking-status').textContent = err.message; }
}
async function saveBooking() {
    if (!bookingModalOpen || bookingSubmitting) return;
    const customerId = Number(document.getElementById('booking-customer').value);
    const roomId = Number(document.getElementById('booking-room').value);
    const checkinDate = document.getElementById('booking-checkin-date').value;
    const checkoutDate = document.getElementById('booking-checkout-date').value;
    const note = document.getElementById('booking-note').value.trim();
    try {
        validBookingDates(checkinDate, checkoutDate); validText(note, 'Ghi chú', 500, false);
        if (!customersReady || availableRange !== dateRangeKey() || !availableRange || !bookingCustomers.has(customerId) || !bookingRooms.has(roomId)) throw new Error('Chờ tải xong dữ liệu và chọn khách hàng, phòng phù hợp.');
        const room = bookingRooms.get(roomId);
        const nights = (Date.parse(checkoutDate + 'T00:00:00Z') - Date.parse(checkinDate + 'T00:00:00Z')) / 86400000;
        validMoney(nights * validMoney(room.price, 'Giá phòng', 1), 'Tiền phòng', 1);
    } catch (err) { document.getElementById('booking-status').textContent = err.message; return; }
    bookingSubmitting = true; updateBookingControls();
    document.getElementById('booking-status').textContent = 'Đang tạo booking...';
    try {
        const result = await apiPost('/bookings', { customerId, roomId, checkinDate, checkoutDate, note });
        if (result.success !== true) throw new Error(result.message || 'Chưa xác nhận được việc tạo booking.');
        bookingSubmitting = false; closeBookingModal();
        showAlert(result.message || 'Đặt phòng thành công!'); await loadBookings();
    } catch (err) {
        bookingSubmitting = false;
        document.getElementById('booking-status').textContent = err.message || 'Chưa xác nhận được kết quả. Kiểm tra danh sách trước khi thử lại.';
        showRequestError(err, 'Không thể tạo booking.');
        await Promise.all([loadBookings(), reloadBookingData()]);
    } finally { bookingSubmitting = false; updateBookingControls(); }
}
document.addEventListener('DOMContentLoaded', async () => {
    if (!await checkLogin()) return;
    if (typeof initializeBookingCalendar === 'function') initializeBookingCalendar(); else loadBookings();
    document.getElementById('booking-search').addEventListener('input', filterBookings);
    document.getElementById('booking-status-filter').addEventListener('change', filterBookings);
    document.getElementById('booking-customer').addEventListener('change', updateBookingControls);
    document.getElementById('booking-room').addEventListener('change', () => { calculateEstimate(); updateBookingControls(); });
    for (const id of ['booking-checkin-date', 'booking-checkout-date']) {
        document.getElementById(id).addEventListener('input', () => refreshAvailableRooms());
        document.getElementById(id).addEventListener('change', () => refreshAvailableRooms());
    }
});
