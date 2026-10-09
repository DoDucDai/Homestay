// ============================================
// services.js - XU LY TRANG DICH VU
// ============================================

checkLogin();
let currentServices = [];

document.addEventListener('DOMContentLoaded', () => {
    loadServices();
});

async function loadServices() {
    try {
        currentServices = await apiGet('/services');
        renderTable(currentServices);
    } catch (err) {
        showAlert('Lỗi khi tải danh sách dịch vụ!', 'error');
    }
}

function renderTable(services) {
    const tbody = document.getElementById('services-tbody');
    if (!tbody) return;
    
    tbody.innerHTML = '';
    
    if (services.length === 0) {
        tbody.innerHTML = '<tr><td colspan="5" style="text-align:center;">Không tìm thấy dịch vụ nào</td></tr>';
        return;
    }
    
    services.forEach(service => {
        const tr = document.createElement('tr');
        
        let statusBadge = service.status === 'Hoạt động' ? 'bg-success' : 'bg-secondary';
        
        tr.innerHTML = `
            <td><strong>DV${service.serviceId}</strong></td>
            <td>${service.serviceName}</td>
            <td>${formatMoney(service.unitPrice)}</td>
            <td><span class="badge ${statusBadge}">${service.status}</span></td>
            <td>
                <button class="btn btn-primary" onclick='openEditModal(${JSON.stringify(service).replace(/'/g, "&#39;")})'>Sửa</button>
                <button class="btn btn-danger" onclick="deleteService(${service.serviceId}, '${service.serviceName}')">Xóa</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function openAddModal() {
    document.getElementById('modal-title').textContent = 'Thêm Dịch Vụ Mới';
    document.getElementById('service-id').value = '';
    document.getElementById('service-name').value = '';
    document.getElementById('service-price').value = '';
    document.getElementById('service-status').value = 'Hoạt động';
    openModal('service-modal');
}

function openEditModal(service) {
    document.getElementById('modal-title').textContent = 'Sửa Dịch Vụ';
    document.getElementById('service-id').value = service.serviceId;
    document.getElementById('service-name').value = service.serviceName;
    document.getElementById('service-price').value = service.unitPrice;
    document.getElementById('service-status').value = service.status;
    openModal('service-modal');
}

async function saveService() {
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
        showAlert('Có lỗi xảy ra khi lưu!', 'error');
    }
}

async function deleteService(id, name) {
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
        showAlert('Có lỗi xảy ra khi xóa!', 'error');
    }
}
