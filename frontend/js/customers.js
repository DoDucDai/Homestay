// ============================================
// customers.js - XU LY TRANG KHACH HANG
// NGUOI LAM: DUC DAI (LEADER)
// API BACKEND: DUC DAI
//   GET    /api/customers
//   POST   /api/customers
//   PUT    /api/customers/{id}
//   DELETE /api/customers/{id}
// ============================================

// Kiem tra dang nhap
checkLogin();

let currentCustomers = [];
let editingCustomer = null;
let customersLoadFailed = false;
let customersVersion = 0, historyVersion = 0, customerSaving = false, customerSearchTimer;
const customerDeleting = new Set();

document.addEventListener('DOMContentLoaded', async () => {
    if (!await checkLogin()) return;
    loadCustomers();
    
    // Su kien tim kiem
    const searchInput = document.getElementById('search-input');
    if(searchInput) {
        searchInput.addEventListener('input', function() {
            customersVersion++;
            clearTimeout(customerSearchTimer);
            customerSearchTimer = setTimeout(loadCustomers,250);
        });
    }
});

async function loadCustomers() {
    const version = ++customersVersion;
    const search = document.getElementById('search-input').value.trim();
    tableState('customers-tbody',6,'Đang tải khách hàng...');
    try {
        const list = expectList(await apiGet('/customers'+(search ? '?search='+encodeURIComponent(search) : '')));
        if (version !== customersVersion) return;
        currentCustomers = list;
        customersLoadFailed = false;
        renderTable(currentCustomers);
    } catch (err) {
        if (version !== customersVersion) return;
        currentCustomers = []; customersLoadFailed = true;
        tableState('customers-tbody', 6, err.message || 'Không tải được danh sách khách hàng.');
        showRequestError(err, 'Lỗi khi tải danh sách khách hàng!');
    }
}

function renderTable(customers) {
    const tbody = document.getElementById('customers-tbody'); if (!tbody) return;
    tbody.replaceChildren();
    if (!customers.length) { tableState('customers-tbody', 6, 'Không tìm thấy khách hàng nào'); return; }
    customers.forEach(customer => {
        const documentCell = textNode('span', `${customer.documentType === 'PASSPORT' ? 'Hộ chiếu' : customer.documentType === 'LEGACY' ? 'Chưa xác định' : 'CCCD'}: ${customer.cccd}`);
        documentCell.title = (customer.validationWarnings || []).join(' ');
        const actions = [actionButton('Sửa', 'btn btn-primary', () => openEditModal(customer)),
            actionButton('Lịch sử lưu trú','btn btn-secondary',() => openCustomerHistory(customer.customerId))];
        if (currentUser?.role === 'admin') actions.push(actionButton('Xóa', 'btn btn-danger', () => deleteCustomer(customer.customerId, customer.fullName)));
        actions.forEach(button => { button.disabled = customerDeleting.has(customer.customerId); });
        appendTableRow(tbody, [textNode('strong', 'KH' + customer.customerId), customer.fullName, documentCell, customer.phone, customer.email || ''], actions);
    });
}

function openAddModal() {
    if (customerSaving) return;
    editingCustomer = null;
    document.getElementById('customer-document-type').value = 'CCCD';
    document.getElementById('customer-validation-warning').textContent = '';
    document.getElementById('modal-title').textContent = 'Thêm Khách Hàng Mới';
    document.getElementById('customer-id').value = '';
    document.getElementById('customer-name').value = '';
    document.getElementById('customer-cccd').value = '';
    document.getElementById('customer-phone').value = '';
    document.getElementById('customer-email').value = '';
    document.getElementById('customer-address').value = '';
    
    openModal('customer-modal');
}

function openEditModal(customer) {
    if (customerSaving || customerDeleting.has(customer.customerId)) return;
    editingCustomer = customer;
    document.getElementById('customer-document-type').value = customer.documentType;
    document.getElementById('customer-validation-warning').textContent = (customer.validationWarnings || []).join(' ');
    document.getElementById('modal-title').textContent = 'Sửa Khách Hàng';
    document.getElementById('customer-id').value = String(customer.customerId);
    document.getElementById('customer-name').value = customer.fullName;
    document.getElementById('customer-cccd').value = customer.cccd;
    document.getElementById('customer-phone').value = customer.phone;
    document.getElementById('customer-email').value = customer.email || '';
    document.getElementById('customer-address').value = customer.address || '';
    
    openModal('customer-modal');
}

async function saveCustomer() {
    if (customerSaving) return;
    const id = document.getElementById('customer-id').value;
    const fullName = document.getElementById('customer-name').value.trim();
    const documentValue = document.getElementById('customer-cccd').value;
    const cccd = editingCustomer && documentValue === editingCustomer.cccd ? documentValue : documentValue.trim();
    const documentType = document.getElementById('customer-document-type').value;
    const phoneValue = document.getElementById('customer-phone').value;
    const phone = editingCustomer && phoneValue === editingCustomer.phone ? phoneValue : phoneValue.trim();
    const emailValue = document.getElementById('customer-email').value;
    const email = editingCustomer && emailValue === editingCustomer.email ? emailValue : emailValue.trim();
    const address = document.getElementById('customer-address').value.trim();
    
    // Validate
    if (!fullName || !editingCustomer && (!cccd || !phone)) {
        showAlert('Vui lòng nhập đầy đủ Họ tên, CCCD và Số điện thoại', 'error');
        return;
    }
    
    const data = {
        fullName, 
        cccd, 
        documentType,
        phone, 
        email, 
        address
    };
    
    try {
        validText(fullName, 'Họ tên', 100); validText(cccd, 'Giấy tờ', 20, !editingCustomer || editingCustomer.cccd !== cccd);
        validText(phone, 'Số điện thoại', 15, !editingCustomer || editingCustomer.phone !== phone);
        validText(email, 'Email', 100, false); validText(address, 'Địa chỉ', 200, false);
        const unchangedDocument = editingCustomer && editingCustomer.documentType === documentType && editingCustomer.cccd === cccd;
        if (!unchangedDocument && !(documentType === 'CCCD' ? /^[0-9]{12}$/.test(cccd) : documentType === 'PASSPORT' && /^[A-Za-z0-9]{6,20}$/.test(cccd))) {
            throw new Error('CCCD cần 12 chữ số; hộ chiếu cần 6–20 chữ cái hoặc chữ số.');
        }
        if ((!editingCustomer || editingCustomer.phone !== phone) && !/^0[0-9]{9}$/.test(phone)) throw new Error('Số điện thoại cần 10 chữ số, bắt đầu bằng 0.');
        if ((!editingCustomer || (editingCustomer.email || '') !== email) && email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new Error('Email không hợp lệ.');
        customerSaving = true;
        document.querySelectorAll('#customer-modal input, #customer-modal select, #customer-modal button').forEach(el => { el.disabled = true; });
        let res;
        if (id) {
            // Cap nhat (PUT)
            res = await apiPut('/customers/' + id, data);
        } else {
            // Them moi (POST)
            res = await apiPost('/customers', data);
        }
        
        if (res.success !== false) {
            showAlert(res.message || 'Lưu thành công!', 'success');
            document.getElementById('customer-modal').classList.remove('show');
            loadCustomers(); // Tai lai bang
        } else {
            // Loi trung CCCD hoac nghiep vu khac tu Backend
            showAlert(res.message, 'error');
        }
    } catch (err) {
        showRequestError(err, 'Có lỗi xảy ra khi lưu khách hàng!');
    } finally {
        customerSaving = false;
        document.querySelectorAll('#customer-modal input, #customer-modal select, #customer-modal button').forEach(el => { el.disabled = false; });
    }
}

async function deleteCustomer(id, name) {
    if (currentUser?.role !== 'admin' || customerDeleting.has(id)) return;
    if (!confirm(`Bạn có chắc chắn muốn xóa khách hàng [${name}] không?`)) {
        return;
    }
    
    customerDeleting.add(id);customersVersion++;renderTable(currentCustomers);
    try {
        const res = await apiDelete('/customers/' + id);
        if (res.success !== false) {
            showAlert(res.message || 'Xóa thành công!', 'success');
        } else {
            // Bao loi khi khach hang da tung dat phong (rang buoc khoa ngoai)
            showAlert(res.message, 'error');
        }
    } catch (err) {
        showRequestError(err, 'Có lỗi xảy ra khi xóa khách hàng!');
    } finally {
        customerDeleting.delete(id);loadCustomers();
    }
}

function closeCustomerModal() { if (!customerSaving) document.getElementById('customer-modal').classList.remove('show'); }
function closeCustomerHistory() {
    historyVersion++;
    document.getElementById('customer-history-modal').classList.remove('show');
}
async function openCustomerHistory(id) {
    const version = ++historyVersion;
    document.getElementById('history-title').textContent = 'Lịch sử KH'+id;
    document.getElementById('history-total').textContent = '--';
    document.getElementById('history-status').textContent = 'Đang tải lịch sử...';
    document.getElementById('history-reload').onclick = () => openCustomerHistory(id);
    tableState('history-tbody',9,'Đang tải...');openModal('customer-history-modal');
    try {
        const data = await apiGet('/customers/'+id+'/history');
        if (version !== historyVersion) return;
        if (!data?.success || data.customer?.customerId !== id || !Array.isArray(data.bookings) || !/^\d+$/.test(data.totalPaid)) throw new Error('Dữ liệu lịch sử không hợp lệ.');
        let total = 0n;
        data.bookings.forEach(b => {
            if (!Number.isSafeInteger(b.paidTotal) || b.paidTotal < 0 || (b.paymentId != null && (!Number.isInteger(b.paymentId) || b.paymentId < 1))) throw new Error('Khoản thanh toán không hợp lệ.');
            if (b.paymentId == null && b.paidTotal !== 0) throw new Error('Khoản chưa thanh toán có số tiền đã thu.');
            total += BigInt(b.paidTotal);
        });
        if (total !== BigInt(data.totalPaid)) throw new Error('Tổng chi tiêu không khớp lịch sử.');
        document.getElementById('history-title').textContent = 'Lịch sử KH'+id+' — '+data.customer.fullName;
        document.getElementById('history-total').textContent = formatMoney(total);
        document.getElementById('history-status').textContent = 'Tổng chi tiêu chỉ cộng các hóa đơn đã thanh toán.';
        const body = document.getElementById('history-tbody');body.replaceChildren();
        if (!data.bookings.length) tableState('history-tbody',9,'Khách hàng chưa có lịch sử đặt phòng.');
        data.bookings.forEach(b => {
            const invoice = b.paymentId == null ? textNode('span','Chưa thanh toán') : textNode('a','Xem HD'+b.paymentId);
            if (b.paymentId != null) invoice.href = 'checkout.html?paymentId='+b.paymentId;
            appendTableRow(body,['BK'+b.bookingId,(b.roomCode || '')+' — '+(b.roomName || ''),formatDate(b.checkinDate),formatDate(b.checkoutDate),b.nights,
                ({Confirmed:'Đã đặt',CheckedIn:'Đang ở',CheckedOut:'Đã trả phòng',Cancelled:'Đã hủy'}[b.status] || b.status),
                b.paymentId == null ? 'Chưa thanh toán' : formatMoney(b.paidTotal),b.paymentMethod || '--',invoice]);
        });
    } catch (err) {
        if (version !== historyVersion) return;
        document.getElementById('history-total').textContent = '--';
        document.getElementById('history-status').textContent = err.message || 'Không tải được lịch sử.';
        tableState('history-tbody',9,'Không tải được lịch sử. Bấm Tải lại để thử lại.');
    }
}
