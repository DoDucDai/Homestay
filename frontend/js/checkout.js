checkLogin();
let currentCheckoutBookingId = null, checkoutPreviewReady = false, checkoutSubmitting = false;
let checkoutPreviewVersion = 0, checkoutListVersion = 0, paymentListVersion = 0, serviceCatalogVersion = 0;
let officialInvoice = null, availableServices = [], servicesReady = false, serviceBusy = false;
const invoiceFields = ['id','customer','room','checkin','checkout','nights','price-per-night','room-total','service-total','grand-total','paid-at','method','warning'];
function updateCheckoutControls() {
    const locked = checkoutSubmitting || serviceBusy;
    document.getElementById('invoice-state').textContent = officialInvoice ? 'Đã thanh toán · Hóa đơn đã lưu'
        : checkoutSubmitting ? 'Đang ghi nhận thanh toán' : serviceBusy ? 'Đang cập nhật dịch vụ'
        : checkoutPreviewReady ? 'Chưa thanh toán · Bản tạm tính' : 'Chưa có dữ liệu hóa đơn đã xác nhận';
    document.getElementById('invoice-actions-header').hidden = !!officialInvoice;
    document.getElementById('checkout-confirm-btn').setAttribute('aria-busy',String(checkoutSubmitting));
    document.getElementById('service-save-btn').setAttribute('aria-busy',String(serviceBusy));
    document.getElementById('checkout-confirm-btn').disabled = !checkoutPreviewReady || locked || !!officialInvoice;
    document.getElementById('checkout-confirm-btn').hidden = !!officialInvoice;
    document.getElementById('checkout-confirm-btn').textContent = checkoutSubmitting ? 'Đang ghi nhận...' : 'Xác nhận đã thu tiền & Check-out';
    document.getElementById('checkout-cancel-btn').disabled = locked;
    document.getElementById('payment-method').disabled = locked || !checkoutPreviewReady || !!officialInvoice;
    document.getElementById('payment-controls').hidden = !!officialInvoice;
    document.getElementById('add-service-btn').disabled = locked || !checkoutPreviewReady || !!officialInvoice;
    document.getElementById('add-service-btn').hidden = !!officialInvoice;
    document.getElementById('invoice-print-btn').disabled = !officialInvoice || locked;
    document.getElementById('invoice-preview-btn').disabled = !officialInvoice || locked;
    document.getElementById('service-save-btn').disabled = !servicesReady || locked;
    document.getElementById('service-cancel-btn').disabled = locked;
    document.getElementById('service-select').disabled = locked || !servicesReady;
    document.getElementById('service-quantity').disabled = locked;
    document.querySelectorAll('#invoice-services button').forEach(button => { button.disabled = locked || !checkoutPreviewReady; });
}
function resetCheckoutPreview() {
    checkoutPreviewReady = false; officialInvoice = null;
    invoiceFields.forEach(key => { document.getElementById('inv-' + key).textContent = '--'; });
    tableState('invoice-services', 5, 'Chưa tải bảng kê dịch vụ.'); updateCheckoutControls();
}
function closeCheckoutModal() {
    if (checkoutSubmitting || serviceBusy) return;
    closePrintPreview();
    checkoutPreviewVersion++; serviceCatalogVersion++; currentCheckoutBookingId = null;
    resetCheckoutPreview(); document.getElementById('checkout-status').textContent = '';
    document.getElementById('checkout-modal').classList.remove('show'); closeServiceModal();
}
function isValidCheckoutPreview(p, bookingId) {
    if (!p || p.bookingId !== bookingId) return false;
    if (!['pricePerNight','roomTotal','serviceTotal','grandTotal'].every(key => Number.isSafeInteger(p[key]) && p[key] >= 0 && p[key] <= 999999999999)) return false;
    if (p.billingUnit === 'Hour') {
        const elapsed=Date.parse(p.checkoutAt+'+07:00')-Date.parse(p.checkinAt+'+07:00');
        if(!Number.isFinite(elapsed) || elapsed<=0 || Math.ceil(elapsed/3600000)!==p.billedHours
            || !Number.isSafeInteger(p.pricePerHour) || p.pricePerHour<=0 || p.roomTotal!==p.pricePerHour*p.billedHours) return false;
    } else {
    if (!Number.isInteger(p.nights) || p.nights <= 0) return false;
    if (![p.checkinDate,p.checkoutDate].every(value => typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)))) return false;
    if ((Date.parse(p.checkoutDate)-Date.parse(p.checkinDate))/86400000 !== p.nights) return false;
    if(p.roomTotal !== p.pricePerNight*p.nights) return false;
    }
    return Array.isArray(p.services) && p.grandTotal === p.roomTotal+p.serviceTotal
        && p.services.every(s => Number.isInteger(s.bookingServiceId) && s.bookingServiceId > 0 && Number.isInteger(s.quantity) && s.quantity > 0
            && Number.isFinite(s.unitPrice) && s.unitPrice >= 0 && Number.isSafeInteger(s.totalPrice) && s.totalPrice >= 0)
        && p.services.reduce((sum,s) => sum+s.totalPrice,0) === p.serviceTotal;
}
function renderInvoice(p, official = false) {
    document.getElementById('invoice-title').textContent = official ? 'Hóa đơn HD' + p.paymentId : 'Tạm tính BK' + p.bookingId;
    const values = {id: official ? 'HD'+p.paymentId : 'BK'+p.bookingId, customer:p.customerName || '', room:(p.roomCode || '')+' — '+(p.roomName || ''),
        checkin:bookingTime(p,'checkin'), checkout:bookingTime(p,'checkout'), nights:p.billingUnit==='Hour'?p.billedHours+' giờ':p.nights+' đêm',
        'price-per-night':formatMoney(p.billingUnit==='Hour'?p.pricePerHour:p.pricePerNight)+(p.billingUnit==='Hour'?' / giờ':' / đêm'), 'room-total':formatMoney(p.roomTotal), 'service-total':formatMoney(p.serviceTotal),
        'grand-total':formatMoney(p.grandTotal), 'paid-at':official ? (p.paidAt || '--').replace('T',' ') : 'Chưa thanh toán',
        method:official ? p.paymentMethod : 'Chưa ghi nhận', warning:[p.snapshotEstimated ? 'Giá dữ liệu cũ được dựng lại, cần đối soát.' : '', p.reconciliationWarning || ''].filter(Boolean).join(' ')};
    Object.entries(values).forEach(([key,value]) => { document.getElementById('inv-'+key).textContent = value; });
    const body = document.getElementById('invoice-services'); body.replaceChildren();
    if (!p.services.length) tableState('invoice-services',official ? 4 : 5,'Chưa sử dụng dịch vụ.');
    p.services.forEach(s => appendTableRow(body,[s.serviceName,s.quantity,formatMoney(s.unitPrice),formatMoney(s.totalPrice)],
        official ? [] : [actionButton('Hủy dòng','btn btn-danger',() => removeBookingService(s.bookingServiceId))]));
}
document.addEventListener('DOMContentLoaded', async () => {
    if (!await checkLogin()) return;
    loadCheckedInBookings(); loadPayments();
    const query = new URLSearchParams(window.location.search);
    const bookingId = query.get('bookingId');
    if(bookingId && /^[1-9]\d*$/.test(bookingId) && Number.isSafeInteger(Number(bookingId))) openCheckoutModal(Number(bookingId));
    const id = query.get('paymentId');
    if (id && /^[1-9]\d*$/.test(id) && Number.isSafeInteger(Number(id))) openPayment(Number(id));
    else if (id !== null) showAlert('Mã hóa đơn trong liên kết không hợp lệ. Hãy chọn hóa đơn từ danh sách.','error');
});
async function loadCheckedInBookings() {
    const version = ++checkoutListVersion; tableState('checkout-tbody',6,'Đang tải khách đang ở...');
    document.getElementById('stays-result').textContent = 'Đang tải khách đang ở…';
    try {
        const bookings = expectList(await apiGet('/bookings?status=CheckedIn'));
        if (version === checkoutListVersion) renderTable(bookings);
    } catch (err) {
        if (version !== checkoutListVersion) return;
        document.getElementById('stays-result').textContent = 'Chưa tải được khách đang ở. Hãy thử tải lại.';
        tableState('checkout-tbody',6,err.message || 'Không tải được danh sách lưu trú.'); showRequestError(err,'Lỗi tải khách đang ở.');
    }
}
function renderTable(bookings) {
    const body = document.getElementById('checkout-tbody'); body.replaceChildren();
    document.getElementById('stays-result').textContent = bookings.length+' booking đang ở';
    if (!bookings.length) { tableState('checkout-tbody',6,'Không có khách nào đang lưu trú'); return; }
    pageRows('stays', bookings, renderTable).forEach(b => appendTableRow(body,[textNode('strong','BK'+b.bookingId),b.customerName || '',b.roomCode || '',bookingTime(b,'checkin'),bookingTime(b,'checkout')],
        [actionButton('Dịch vụ & Check-out','btn btn-primary',() => openCheckoutModal(b.bookingId))]));
}
async function loadPayments() {
    const version = ++paymentListVersion; tableState('payments-tbody',6,'Đang tải hóa đơn đã thu...');
    document.getElementById('payments-result').textContent = 'Đang tải hóa đơn…';
    try {
        const list = expectList(await apiGet('/payments'));
        if (version !== paymentListVersion) return;
        renderPayments(list);
    } catch (err) { if (version === paymentListVersion) {
        document.getElementById('payments-result').textContent = 'Chưa tải được hóa đơn. Hãy thử tải lại.';
        tableState('payments-tbody',6,err.message || 'Không tải được hóa đơn đã thu.');
    } }
}
function renderPayments(list) {
        const body = document.getElementById('payments-tbody'); body.replaceChildren();
        document.getElementById('payments-result').textContent = list.length+' hóa đơn đã thu';
        if (!list.length) { tableState('payments-tbody',6,'Chưa có hóa đơn đã thu.'); return; }
        pageRows('payments',list,renderPayments).forEach(p => appendTableRow(body,['HD'+p.paymentId,'BK'+p.bookingId,(p.paidAt || '').replace('T',' '),p.paymentMethod,formatMoney(p.grandTotal)],
            [actionButton('Xem / In lại','btn btn-primary',() => openPayment(p.paymentId))]));
}

async function openCheckoutModal(bookingId) {
    if (checkoutSubmitting || serviceBusy) return;
    const version = ++checkoutPreviewVersion; currentCheckoutBookingId = bookingId; resetCheckoutPreview();
    document.getElementById('invoice-title').textContent = 'Tạm tính BK'+bookingId;
    document.getElementById('payment-method').value = 'Tiền mặt';
    document.getElementById('checkout-status').textContent = 'Đang tải hóa đơn tạm tính...'; openModal('checkout-modal');
    try {
        const p = await apiGet('/checkout/preview/'+bookingId);
        if (version !== checkoutPreviewVersion) return;
        if (p.success !== true || !isValidCheckoutPreview(p,bookingId)) throw new Error('Dữ liệu hóa đơn tạm tính không hợp lệ.');
        renderInvoice(p); checkoutPreviewReady = true; document.getElementById('checkout-status').textContent = '';
    } catch (err) {
        if (version !== checkoutPreviewVersion) return;
        resetCheckoutPreview(); document.getElementById('checkout-status').textContent = err.message;
    } finally { if (version === checkoutPreviewVersion) updateCheckoutControls(); }
}
async function openPayment(paymentId) {
    if (checkoutSubmitting || serviceBusy) return;
    const version = ++checkoutPreviewVersion; currentCheckoutBookingId = null; resetCheckoutPreview();
    document.getElementById('invoice-title').textContent = 'Đang tải hóa đơn HD'+paymentId;
    document.getElementById('checkout-status').textContent = 'Đang lấy bản hóa đơn đã lưu...'; openModal('checkout-modal');
    try {
        const response = await apiGet('/payments/'+paymentId), p = response.invoice;
        if (version !== checkoutPreviewVersion) return;
        if (response.success !== true || !p || p.paymentId !== paymentId || p.paymentStatus !== 'Đã thanh toán' || !isValidCheckoutPreview(p,p.bookingId)) throw new Error('Bản hóa đơn đã lưu không hợp lệ.');
        officialInvoice = p; renderInvoice(p,true); document.getElementById('checkout-status').textContent = '';
    } catch (err) { if (version === checkoutPreviewVersion) document.getElementById('checkout-status').textContent = err.message; }
    finally { if (version === checkoutPreviewVersion) updateCheckoutControls(); }
}
function printInvoice() { if (officialInvoice && !checkoutSubmitting && !serviceBusy) window.print(); }
function previewInvoicePrint() {
    if (!officialInvoice || checkoutSubmitting || serviceBusy) return;
    document.getElementById('invoice-print-css').media = 'all';
    document.getElementById('print-preview-controls').hidden = false;
    window.scrollTo(0,0);
}
function closePrintPreview() {
    document.getElementById('invoice-print-css').media = 'print';
    document.getElementById('print-preview-controls').hidden = true;
}
async function openServiceModal() {
    if (!checkoutPreviewReady || checkoutSubmitting || serviceBusy || officialInvoice) return;
    const version = ++serviceCatalogVersion; servicesReady = false; availableServices = [];
    document.getElementById('service-select').replaceChildren(selectOption('','Đang tải...'));
    document.getElementById('service-quantity').value = '1'; document.getElementById('service-status').textContent = 'Đang tải danh mục dịch vụ...';
    document.getElementById('service-price').textContent = '--'; openModal('service-modal'); updateCheckoutControls();
    try {
        const list = expectList(await apiGet('/services'));
        if (version !== serviceCatalogVersion) return;
        availableServices = list.filter(s => s.status === 'Hoạt động');
        document.getElementById('service-select').replaceChildren(selectOption('','-- Chọn dịch vụ --'),...availableServices.map(s => selectOption(s.serviceId,s.serviceName+' — '+formatMoney(s.unitPrice))));
        servicesReady = availableServices.length > 0;
        document.getElementById('service-status').textContent = servicesReady ? 'Giá được chốt tại server khi thêm.' : 'Không có dịch vụ hoạt động.';
    } catch (err) { if (version === serviceCatalogVersion) document.getElementById('service-status').textContent = err.message; }
    finally { if (version === serviceCatalogVersion) updateCheckoutControls(); }
}
function closeServiceModal() {
    if (serviceBusy) return;
    serviceCatalogVersion++; servicesReady = false; document.getElementById('service-modal').classList.remove('show');
}
function updateServicePrice() {
    const service = availableServices.find(s => s.serviceId === Number(document.getElementById('service-select').value));
    const quantity = Number(document.getElementById('service-quantity').value);
    document.getElementById('service-price').textContent = service && Number.isInteger(quantity) && quantity > 0 && quantity <= 10000
        ? formatMoney(service.unitPrice)+' × '+quantity+' = '+formatMoney(service.unitPrice*quantity) : '--';
}
async function addBookingService() {
    if (!servicesReady || !checkoutPreviewReady || serviceBusy || checkoutSubmitting || officialInvoice) return;
    const serviceId = Number(document.getElementById('service-select').value), quantity = Number(document.getElementById('service-quantity').value);
    if (!availableServices.some(s => s.serviceId === serviceId) || !Number.isInteger(quantity) || quantity < 1 || quantity > 10000) {
        document.getElementById('service-status').textContent = 'Chọn dịch vụ và số lượng nguyên từ 1–10000.'; return;
    }
    await mutateService(() => apiPost('/bookings/'+currentCheckoutBookingId+'/services',{serviceId,quantity}));
}
async function removeBookingService(id) {
    if (!checkoutPreviewReady || serviceBusy || checkoutSubmitting || officialInvoice || !await askConfirmation('Hủy dòng dịch vụ này?')) return;
    if (!checkoutPreviewReady || serviceBusy || checkoutSubmitting || officialInvoice) return;
    await mutateService(() => apiDelete('/bookings/services/'+id));
}
async function mutateService(write) {
    const bookingId = currentCheckoutBookingId; serviceBusy = true; checkoutPreviewReady = false; updateCheckoutControls();
    let message = '';
    try { const res = await write(); if (res.success !== true) throw new Error(res.message || 'Chưa xác nhận được kết quả.'); }
    catch (err) { message = err.message+' Đã tải lại bảng kê; hãy kiểm tra trước khi thao tác tiếp.'; }
    finally { serviceBusy = false; closeServiceModal(); }
    await openCheckoutModal(bookingId);
    if (message) document.getElementById('checkout-status').textContent = message;
}
async function confirmCheckout() {
    if (!currentCheckoutBookingId || !checkoutPreviewReady || checkoutSubmitting || serviceBusy || officialInvoice) return;
    const method = document.getElementById('payment-method').value;
    if (!['Tiền mặt','Chuyển khoản','Thẻ ngân hàng'].includes(method)) return;
    if (!await askConfirmation('Xác nhận bạn đã thu đủ tiền và trả phòng? Hệ thống chỉ ghi nhận phương thức thu tiền.')) return;
    if (!currentCheckoutBookingId || !checkoutPreviewReady || checkoutSubmitting || serviceBusy || officialInvoice) return;
    const bookingId = currentCheckoutBookingId; checkoutSubmitting = true; updateCheckoutControls();
    document.getElementById('checkout-status').textContent = 'Đang ghi nhận thanh toán...';
    let paymentId = null, message = '';
    try {
        const res = await apiPost('/checkout/'+bookingId,{paymentMethod:method});
        if (res.success !== true || !Number.isInteger(res.paymentId) || res.paymentId < 1) throw new Error('Chưa xác nhận được mã hóa đơn.');
        paymentId = res.paymentId;
    } catch (err) {
        message = err.message;
        // A lost POST response may follow a successful commit. Reconcile using reads only.
        try { const list = expectList(await apiGet('/payments?bookingId='+bookingId)); paymentId = list.find(p => p.bookingId === bookingId)?.paymentId || null; }
        catch { message += ' Chưa đối soát được; tải lại danh sách hóa đơn trước khi thử tiếp.'; }
    } finally { checkoutSubmitting = false; checkoutPreviewReady = false; updateCheckoutControls(); }
    loadCheckedInBookings(); loadPayments();
    if (paymentId) await openPayment(paymentId);
    else document.getElementById('checkout-status').textContent = message+' Hãy đóng và kiểm tra lại booking/hóa đơn; không tự gửi lại thanh toán.';
}
