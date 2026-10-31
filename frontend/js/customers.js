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
let customersExportReady = false;

document.addEventListener('DOMContentLoaded', async () => {
    if (!await checkLogin()) return;
    if(new URLSearchParams(window.location.search).get('workspace')!=='admin'){
        const title=document.querySelector('.customers-heading h1');if(title)title.textContent='Khách hàng';
    }
    loadCustomers();
    
    // Su kien tim kiem
    const searchInput = document.getElementById('search-input');
    if(searchInput) {
        searchInput.addEventListener('input', function() {
            customersExportReady = false;document.getElementById('customers-export').disabled = true;
            customersVersion++;
            clearTimeout(customerSearchTimer);
            customerSearchTimer = setTimeout(loadCustomers,250);
        });
    }
});

async function loadCustomers() {
    customersExportReady = false;document.getElementById('customers-export').disabled = true;
    const version = ++customersVersion;
    const search = document.getElementById('search-input').value.trim();
    tableState('customers-tbody',6,'Đang tải khách hàng...');
    document.getElementById('customers-result').textContent = 'Đang tải khách hàng…';
    try {
        const list = expectList(await apiGet('/customers'+(search ? '?search='+encodeURIComponent(search) : '')));
        if (version !== customersVersion) return;
        currentCustomers = list;
        customersLoadFailed = false;
        renderTable(currentCustomers);
        customersExportReady = true;document.getElementById('customers-export').disabled = false;
    } catch (err) {
        if (version !== customersVersion) return;
        currentCustomers = []; customersLoadFailed = true;
        document.getElementById('customers-result').textContent = 'Chưa tải được danh sách. Hãy thử tải lại.';
        tableState('customers-tbody', 6, err.message || 'Không tải được danh sách khách hàng.');
        showRequestError(err, 'Lỗi khi tải danh sách khách hàng!');
    }
}
function exportCustomers() {
    return downloadCsv('khach-hang-'+vietnamToday()+'.csv',[
        {label:'Mã khách',value:c=>'KH'+c.customerId},{label:'Họ tên',value:c=>c.fullName,text:true},
        {label:'Loại giấy tờ',value:c=>c.documentType},{label:'Giấy tờ',value:c=>c.cccd,text:true},
        {label:'Điện thoại',value:c=>c.phone,text:true},{label:'Email',value:c=>c.email,text:true},{label:'Địa chỉ',value:c=>c.address,text:true}
    ],currentCustomers,customersExportReady && !customersLoadFailed);
}

function renderTable(customers) {
    const tbody = document.getElementById('customers-tbody'); if (!tbody) return;
    tbody.replaceChildren();
    document.getElementById('customers-result').textContent = customers.length + ' khách hàng theo từ khóa hiện tại';
    if (!customers.length) {
        tableState('customers-tbody', 6, document.getElementById('search-input').value.trim()
            ? 'Không tìm thấy khách hàng phù hợp. Hãy thử tên, số giấy tờ hoặc số điện thoại khác.'
            : 'Chưa có khách hàng trong danh mục.'); return;
    }
    pageRows('customers', customers, renderTable).forEach(customer => {
        const documentCell = textNode('span', `${customer.documentType === 'PASSPORT' ? 'Hộ chiếu' : customer.documentType === 'LEGACY' ? 'Chưa xác định' : 'CCCD'}: ${customer.cccd}`);
        documentCell.title = (customer.validationWarnings || []).join(' ');
        if (customer.documentType === 'LEGACY' || customer.validationWarnings?.length) {
            documentCell.appendChild(textNode('span',customer.validationWarnings?.length
                ? customer.validationWarnings.join(' ') : 'Dữ liệu cũ: cần kiểm tra loại giấy tờ.','customer-document-warning'));
        }
        const identity = document.createElement('div');
        identity.appendChild(textNode('strong',customer.fullName));
        identity.appendChild(textNode('span',customer.address || 'Chưa có địa chỉ','customer-secondary'));
        const actions = [actionButton('Sửa', 'btn btn-primary', () => openEditModal(customer)),
            actionButton('Lịch sử lưu trú','btn btn-secondary',() => openCustomerHistory(customer.customerId))];
        if (currentUser?.role === 'admin') actions.push(actionButton('Xóa', 'btn btn-danger', () => deleteCustomer(customer.customerId, customer.fullName)));
        actions.forEach(button => {
            button.disabled = customerDeleting.has(customer.customerId);
            button.setAttribute('aria-label',button.textContent+' khách hàng KH'+customer.customerId);
        });
        appendTableRow(tbody, [textNode('strong', 'KH' + customer.customerId), identity, documentCell, customer.phone, customer.email || 'Chưa có email'], actions);
    });
}

function openAddModal() {
    if (customerSaving) return;
    clearCustomerErrors();
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
    clearCustomerErrors();
    editingCustomer = customer;
    document.getElementById('customer-document-type').value = customer.documentType;
    document.getElementById('customer-validation-warning').textContent = (customer.documentType === 'LEGACY'
        ? 'Giấy tờ thuộc dữ liệu cũ, chưa xác định loại. Có thể giữ nguyên khi sửa thông tin khác; nếu đổi giấy tờ, hãy chọn CCCD hoặc hộ chiếu hợp lệ. ' : '')
        + (customer.validationWarnings || []).join(' ');
    document.getElementById('modal-title').textContent = 'Sửa Khách Hàng';
    document.getElementById('customer-id').value = String(customer.customerId);
    document.getElementById('customer-name').value = customer.fullName;
    document.getElementById('customer-cccd').value = customer.cccd;
    document.getElementById('customer-phone').value = customer.phone;
    document.getElementById('customer-email').value = customer.email || '';
    document.getElementById('customer-address').value = customer.address || '';
    
    openModal('customer-modal');
}

function clearCustomerErrors() {
    document.getElementById('customer-form-error').hidden = true;
    ['name','document-type','cccd','phone','email','address'].forEach(key => {
        document.getElementById('customer-'+key).setAttribute('aria-invalid','false');
        document.getElementById('customer-'+key+'-error').hidden = true;
    });
}

async function saveCustomer() {
    if (customerSaving) return;
    clearCustomerErrors();
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
    
    // Retain the existing exceptions for unchanged legacy document/contact data.
    const unchangedDocument = editingCustomer && editingCustomer.documentType === documentType && editingCustomer.cccd === cccd;
    let firstInvalid;
    const field = (key,validate) => {
        try { validate(); }
        catch (err) {
            const input = document.getElementById('customer-'+key), error = document.getElementById('customer-'+key+'-error');
            input.setAttribute('aria-invalid','true'); error.textContent = err.message; error.hidden = false;
            firstInvalid ||= input;
        }
    };
    field('name',() => validText(fullName,'Họ tên',100));
    field('document-type',() => {
        if (!unchangedDocument) validEnum(documentType,['CCCD','PASSPORT'],'Loại giấy tờ');
    });
    field('cccd',() => {
        validText(cccd,'Giấy tờ',20,!editingCustomer || editingCustomer.cccd !== cccd);
        if (!unchangedDocument && !(documentType === 'CCCD' ? /^[0-9]{12}$/.test(cccd) : documentType === 'PASSPORT' && /^[A-Za-z0-9]{6,20}$/.test(cccd))) {
            throw new Error('CCCD cần 12 chữ số; hộ chiếu cần 6–20 chữ cái hoặc chữ số.');
        }
    });
    field('phone',() => {
        validText(phone,'Số điện thoại',15,!editingCustomer || editingCustomer.phone !== phone);
        if ((!editingCustomer || editingCustomer.phone !== phone) && !/^0[0-9]{9}$/.test(phone)) throw new Error('Số điện thoại cần 10 chữ số, bắt đầu bằng 0.');
    });
    field('email',() => {
        validText(email,'Email',100,false);
        if ((!editingCustomer || (editingCustomer.email || '') !== email) && email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) throw new Error('Email không hợp lệ.');
    });
    field('address',() => validText(address,'Địa chỉ',200,false));
    if (firstInvalid) {
        firstInvalid.focus(); return;
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
        customerSaving = true;
        document.getElementById('customer-save-btn').textContent = 'Đang lưu…';
        document.getElementById('customer-save-btn').setAttribute('aria-busy','true');
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
            throw new Error(res.message || 'Không thể lưu khách hàng.');
        }
    } catch (err) {
        const error = document.getElementById('customer-form-error');
        error.textContent = err.message || 'Có lỗi xảy ra khi lưu khách hàng!'; error.hidden = false;
        showRequestError(err, 'Có lỗi xảy ra khi lưu khách hàng!');
    } finally {
        customerSaving = false;
        document.getElementById('customer-save-btn').textContent = 'Lưu khách hàng';
        document.getElementById('customer-save-btn').setAttribute('aria-busy','false');
        document.querySelectorAll('#customer-modal input, #customer-modal select, #customer-modal button').forEach(el => { el.disabled = false; });
    }
}

async function deleteCustomer(id, name) {
    if (currentUser?.role !== 'admin' || customerDeleting.has(id)) return;
    if (!await askConfirmation(`Bạn có chắc chắn muốn xóa khách hàng [${name}] không?`)) {
        return;
    }
    if (customerDeleting.has(id)) return;
    
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
    ['history-customer','history-phone','history-stays'].forEach(key => { document.getElementById(key).textContent = '--'; });
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
        document.getElementById('history-customer').textContent = 'KH'+id+' — '+data.customer.fullName;
        document.getElementById('history-phone').textContent = data.customer.phone || 'Chưa có số điện thoại';
        document.getElementById('history-stays').textContent = String(data.bookings.filter(b => ['CheckedIn','CheckedOut'].includes(b.status)).length);
        document.getElementById('history-total').textContent = formatMoney(total);
        document.getElementById('history-status').textContent = data.bookings.length+' booking · Tổng chi tiêu chỉ cộng các hóa đơn đã thanh toán.';
        renderHistoryBookings(data.bookings);
    } catch (err) {
        if (version !== historyVersion) return;
        document.getElementById('history-total').textContent = '--';
        document.getElementById('history-status').textContent = err.message || 'Không tải được lịch sử.';
        tableState('history-tbody',9,'Không tải được lịch sử. Bấm Tải lại để thử lại.');
    }
}

function renderHistoryBookings(bookings) {
        const body = document.getElementById('history-tbody');body.replaceChildren();
        if (!bookings.length) tableState('history-tbody',9,'Khách hàng chưa có lịch sử đặt phòng.');
        pageRows('history',bookings,renderHistoryBookings).forEach(b => {
            const invoice = b.paymentId == null ? textNode('span','Chưa thanh toán') : textNode('a','Xem HD'+b.paymentId);
            if (b.paymentId != null) invoice.href = 'checkout.html?paymentId='+b.paymentId;
            const status = textNode('span',({Confirmed:'Đã đặt',CheckedIn:'Đang ở',CheckedOut:'Đã trả phòng',Cancelled:'Đã hủy'}[b.status] || b.status),
                'badge '+({Confirmed:'badge-booked',CheckedIn:'badge-occupied',CheckedOut:'badge-success',Cancelled:'badge-neutral'}[b.status] || 'badge-neutral'));
            appendTableRow(body,['BK'+b.bookingId,(b.roomCode || '')+' — '+(b.roomName || ''),formatDate(b.checkinDate),formatDate(b.checkoutDate),b.nights,status,
                b.paymentId == null ? 'Chưa thanh toán' : formatMoney(b.paidTotal),b.paymentMethod || '--',invoice]);
        });
}
