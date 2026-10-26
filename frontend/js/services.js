// ============================================
// services.js - XU LY TRANG DICH VU
// ============================================

checkLogin();
let currentServices = [];

document.addEventListener('DOMContentLoaded', async () => {
    if (!await checkLogin()) return;
    loadServices();
});

async function loadServices() {
    try {
        currentServices = expectList(await apiGet('/services'));
        renderTable(currentServices);
    } catch (err) {
        currentServices = [];
        tableState('services-tbody', 5, err.message || 'Không tải được danh sách dịch vụ.');
        showRequestError(err, 'Lỗi khi tải danh sách dịch vụ!');
    }
}

function renderTable(services) {
    const tbody = document.getElementById('services-tbody'); if (!tbody) return;
    tbody.replaceChildren();
    if (!services.length) { tableState('services-tbody', 5, 'Không tìm thấy dịch vụ nào'); return; }
    services.forEach(service => appendTableRow(tbody, [textNode('strong', 'DV' + service.serviceId), service.serviceName,
        formatMoney(service.unitPrice), textNode('span', service.status, 'badge ' + (service.status === 'Hoạt động' ? 'bg-success' : 'bg-secondary'))], [
        ...(currentUser?.role === 'admin' ? [actionButton('Sửa', 'btn btn-primary', () => openEditModal(service)),
        actionButton('Xóa', 'btn btn-danger', () => deleteService(service.serviceId, service.serviceName))] : [])
    ]));
}

function openAddModal() {
    if (currentUser?.role !== 'admin') return;
    document.getElementById('modal-title').textContent = 'Thêm Dịch Vụ Mới';
    document.getElementById('service-id').value = '';
    document.getElementById('service-name').value = '';
    document.getElementById('service-price').value = '';
    document.getElementById('service-status').value = 'Hoạt động';
    openModal('service-modal');
}

function openEditModal(service) {
    if (currentUser?.role !== 'admin') return;
    document.getElementById('modal-title').textContent = 'Sửa Dịch Vụ';
    document.getElementById('service-id').value = service.serviceId;
    document.getElementById('service-name').value = service.serviceName;
    document.getElementById('service-price').value = service.unitPrice;
    document.getElementById('service-status').value = service.status;
    openModal('service-modal');
}

async function saveService() {
    if (currentUser?.role !== 'admin') return;
    const id = document.getElementById('service-id').value;
    const serviceName = document.getElementById('service-name').value.trim();
    const unitPrice = document.getElementById('service-price').value;
    const status = document.getElementById('service-status').value;
    
    if (!serviceName || !unitPrice) {
        showAlert('Vui lòng nhập Tên dịch vụ và Đơn giá', 'error');
        return;
    }
    
    const data = { serviceName, unitPrice: Number(unitPrice), status };
    
    try {
        validText(serviceName, 'Tên dịch vụ', 100); validMoney(unitPrice, 'Đơn giá');
        validEnum(status, ['Hoạt động', 'Ngừng'], 'Trạng thái dịch vụ');
        let res;
        if (id) {
            res = await apiPut('/services/' + id, data);
        } else {
            res = await apiPost('/services', data);
        }
        
        if (res.success !== false) {
            showAlert(res.message || 'Lưu thành công!', 'success');
            closeModal();
            loadServices();
        } else {
            showAlert(res.message, 'error');
        }
    } catch (err) {
        showRequestError(err, 'Có lỗi xảy ra khi lưu!');
    }
}

async function deleteService(id, name) {
    if (currentUser?.role !== 'admin') return;
    if (!confirm(`Bạn có chắc chắn muốn xóa dịch vụ [${name}] không?`)) return;
    
    try {
        const res = await apiDelete('/services/' + id);
        if (res.success !== false) {
            showAlert(res.message || 'Xóa thành công!', 'success');
            loadServices();
        } else {
            showAlert(res.message, 'error');
        }
    } catch (err) {
        showRequestError(err, 'Có lỗi xảy ra khi xóa!');
    }
}
