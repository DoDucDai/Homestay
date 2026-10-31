// ============================================
// services.js - XU LY TRANG DICH VU
// ============================================

checkLogin();
let currentServices = [];
let servicesVersion = 0, serviceSaving = false;
const serviceDeleting = new Set();

document.addEventListener('DOMContentLoaded', async () => {
    if (!await checkLogin()) return;
    loadServices();
});

async function loadServices() {
    const version = ++servicesVersion;
    document.getElementById('services-result').textContent = 'Đang tải dịch vụ…';
    tableState('services-tbody',5,'Đang tải dịch vụ…');
    try {
        const list = expectList(await apiGet('/services'));
        if (version !== servicesVersion) return;
        currentServices = list;
        renderTable(currentServices);
    } catch (err) {
        if (version !== servicesVersion) return;
        currentServices = [];
        document.getElementById('services-result').textContent = 'Chưa tải được danh sách. Hãy thử tải lại.';
        tableState('services-tbody', 5, err.message || 'Không tải được danh sách dịch vụ.');
        showRequestError(err, 'Lỗi khi tải danh sách dịch vụ!');
    }
}

function renderTable(services) {
    const tbody = document.getElementById('services-tbody'); if (!tbody) return;
    tbody.replaceChildren();
    document.getElementById('services-result').textContent = services.length+' dịch vụ · Nhân viên chỉ xem danh mục';
    if (!services.length) { tableState('services-tbody', 5, 'Chưa có dịch vụ trong danh mục.'); return; }
    pageRows('services', services, renderTable).forEach(service => {
        const actions = currentUser?.role === 'admin' ? [actionButton('Sửa','btn btn-secondary',() => openEditModal(service)),
            actionButton('Xóa','btn btn-danger',() => deleteService(service.serviceId,service.serviceName))] : [];
        actions.forEach(button => {
            button.disabled = serviceDeleting.has(service.serviceId);
            button.setAttribute('aria-label',button.textContent+' dịch vụ DV'+service.serviceId);
        });
        appendTableRow(tbody, [textNode('strong', 'DV' + service.serviceId), service.serviceName,
        formatMoney(service.unitPrice), textNode('span', service.status, 'badge ' + (service.status === 'Hoạt động' ? 'bg-success' : 'bg-secondary'))], [
        ...actions
    ]);
    });
}

function openAddModal() {
    if (currentUser?.role !== 'admin' || serviceSaving) return;
    clearServiceErrors();
    document.getElementById('modal-title').textContent = 'Thêm Dịch Vụ Mới';
    document.getElementById('service-id').value = '';
    document.getElementById('service-name').value = '';
    document.getElementById('service-price').value = '';
    document.getElementById('service-status').value = 'Hoạt động';
    openModal('service-modal');
}

function openEditModal(service) {
    if (currentUser?.role !== 'admin' || serviceSaving || serviceDeleting.has(service.serviceId)) return;
    clearServiceErrors();
    document.getElementById('modal-title').textContent = 'Sửa Dịch Vụ';
    document.getElementById('service-id').value = service.serviceId;
    document.getElementById('service-name').value = service.serviceName;
    document.getElementById('service-price').value = service.unitPrice;
    document.getElementById('service-status').value = service.status;
    openModal('service-modal');
}

function clearServiceErrors() {
    document.getElementById('service-form-error').hidden = true;
    ['name','price','status'].forEach(key => {
        document.getElementById('service-'+key).setAttribute('aria-invalid','false');
        document.getElementById('service-'+key+'-error').hidden = true;
    });
}
function closeServiceModal() {
    if (!serviceSaving) document.getElementById('service-modal').classList.remove('show');
}
async function saveService() {
    if (currentUser?.role !== 'admin' || serviceSaving) return;
    clearServiceErrors();
    const id = document.getElementById('service-id').value;
    const serviceName = document.getElementById('service-name').value.trim();
    const unitPrice = document.getElementById('service-price').value;
    const status = document.getElementById('service-status').value;
    
    let firstInvalid;
    for (const [key,validate] of [
        ['name',() => validText(serviceName,'Tên dịch vụ',100)],
        ['price',() => validMoney(unitPrice,'Đơn giá')],
        ['status',() => validEnum(status,['Hoạt động','Ngừng'],'Trạng thái dịch vụ')]
    ]) {
        try { validate(); }
        catch (err) {
            const input = document.getElementById('service-'+key), error = document.getElementById('service-'+key+'-error');
            input.setAttribute('aria-invalid','true'); error.textContent = err.message; error.hidden = false;
            firstInvalid ||= input;
        }
    }
    if (firstInvalid) { firstInvalid.focus(); return; }
    
    const data = { serviceName, unitPrice: Number(unitPrice), status };
    
    try {
        serviceSaving = true;
        document.getElementById('service-save-btn').textContent = 'Đang lưu…';
        document.getElementById('service-save-btn').setAttribute('aria-busy','true');
        document.querySelectorAll('#service-modal input, #service-modal select, #service-modal button').forEach(el => { el.disabled = true; });
        let res;
        if (id) {
            res = await apiPut('/services/' + id, data);
        } else {
            res = await apiPost('/services', data);
        }
        
        if (res.success !== false) {
            showAlert(res.message || 'Lưu thành công!', 'success');
            document.getElementById('service-modal').classList.remove('show');
            loadServices();
        } else {
            throw new Error(res.message || 'Không thể lưu dịch vụ.');
        }
    } catch (err) {
        const error = document.getElementById('service-form-error');
        error.textContent = err.message || 'Có lỗi xảy ra khi lưu!'; error.hidden = false;
        showRequestError(err, 'Có lỗi xảy ra khi lưu!');
    } finally {
        serviceSaving = false;
        document.getElementById('service-save-btn').textContent = 'Lưu dịch vụ';
        document.getElementById('service-save-btn').setAttribute('aria-busy','false');
        document.querySelectorAll('#service-modal input, #service-modal select, #service-modal button').forEach(el => { el.disabled = false; });
    }
}

async function deleteService(id, name) {
    if (currentUser?.role !== 'admin' || serviceDeleting.has(id)) return;
    if (!await askConfirmation(`Bạn có chắc chắn muốn xóa dịch vụ [${name}] không?`)) return;
    if (serviceDeleting.has(id)) return;
    serviceDeleting.add(id);servicesVersion++;renderTable(currentServices);
    try {
        const res = await apiDelete('/services/' + id);
        if (res.success !== false) {
            showAlert(res.message || 'Xóa thành công!', 'success');
        } else {
            showAlert(res.message, 'error');
        }
    } catch (err) {
        showRequestError(err, 'Có lỗi xảy ra khi xóa!');
    } finally {
        serviceDeleting.delete(id);loadServices();
    }
}
