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

document.addEventListener('DOMContentLoaded', () => {
    loadCustomers();
    
    // Su kien tim kiem
    const searchInput = document.getElementById('search-input');
    if(searchInput) {
        searchInput.addEventListener('input', function(e) {
            const keyword = e.target.value.toLowerCase().trim();
            const filtered = currentCustomers.filter(c => 
                (c.fullName && c.fullName.toLowerCase().includes(keyword)) || 
                (c.cccd && c.cccd.toLowerCase().includes(keyword)) ||
                (c.phone && c.phone.includes(keyword))
            );
            renderTable(filtered);
        });
    }
});

async function loadCustomers() {
    try {
        currentCustomers = await apiGet('/customers');
        renderTable(currentCustomers);
    } catch (err) {
        showAlert('Lỗi khi tải danh sách khách hàng!', 'error');
    }
}

function renderTable(customers) {
    const tbody = document.getElementById('customers-tbody');
    if (!tbody) return;
    
    tbody.innerHTML = '';
    
    if (customers.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;">Không tìm thấy khách hàng nào</td></tr>';
        return;
    }
    
    customers.forEach(customer => {
        const tr = document.createElement('tr');
        
        tr.innerHTML = `
            <td><strong>KH${customer.customerId}</strong></td>
            <td>${customer.fullName}</td>
            <td>${customer.cccd}</td>
            <td>${customer.phone}</td>
            <td>${customer.email || ''}</td>
            <td>
                <button class="btn btn-primary" onclick='openEditModal(${JSON.stringify(customer).replace(/'/g, "&#39;")})'>Sửa</button>
                <button class="btn btn-danger" onclick="deleteCustomer(${customer.customerId}, '${customer.fullName}')">Xóa</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

function openAddModal() {
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
    document.getElementById('modal-title').textContent = 'Sửa Khách Hàng';
    document.getElementById('customer-id').value = customer.customerId;
    document.getElementById('customer-name').value = customer.fullName;
    document.getElementById('customer-cccd').value = customer.cccd;
    document.getElementById('customer-phone').value = customer.phone;
    document.getElementById('customer-email').value = customer.email || '';
    document.getElementById('customer-address').value = customer.address || '';
    
    openModal('customer-modal');
}

async function saveCustomer() {
    const id = document.getElementById('customer-id').value;
    const fullName = document.getElementById('customer-name').value.trim();
    const cccd = document.getElementById('customer-cccd').value.trim();
    const phone = document.getElementById('customer-phone').value.trim();
    const email = document.getElementById('customer-email').value.trim();
    const address = document.getElementById('customer-address').value.trim();
    
    // Validate
    if (!fullName || !cccd || !phone) {
        showAlert('Vui lòng nhập đầy đủ Họ tên, CCCD và Số điện thoại', 'error');
        return;
    }
    
    const data = {
        fullName, 
        cccd, 
        phone, 
        email, 
        address
    };
    
    try {
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
            closeModal();
            loadCustomers(); // Tai lai bang
        } else {
            // Loi trung CCCD hoac nghiep vu khac tu Backend
            showAlert(res.message, 'error');
        }
    } catch (err) {
        showAlert('Có lỗi xảy ra khi lưu khách hàng!', 'error');
    }
}

async function deleteCustomer(id, name) {
    if (!confirm(`Bạn có chắc chắn muốn xóa khách hàng [${name}] không?`)) {
        return;
    }
    
    try {
        const res = await apiDelete('/customers/' + id);
        if (res.success !== false) {
            showAlert(res.message || 'Xóa thành công!', 'success');
            loadCustomers(); // Tai lai bang
        } else {
            // Bao loi khi khach hang da tung dat phong (rang buoc khoa ngoai)
            showAlert(res.message, 'error');
        }
    } catch (err) {
        showAlert('Có lỗi xảy ra khi xóa khách hàng!', 'error');
    }
}
