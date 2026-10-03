// ============================================
// rooms.js - XU LY TRANG QUAN LY PHONG
// NGUOI LAM: DUC DAI (LEADER)
// API BACKEND: DUC DAI
//   GET    /api/rooms         -> lay danh sach phong
//   POST   /api/rooms         -> them phong moi
//   PUT    /api/rooms/{id}    -> cap nhat phong
//   DELETE /api/rooms/{id}    -> xoa phong
// ============================================

// Kiem tra dang nhap ngay khi vao trang
checkLogin();

let currentRooms = [];

// Khi trang load xong
document.addEventListener('DOMContentLoaded', () => {
    loadRooms();
    
    // Su kien cho o tim kiem
    const searchInput = document.getElementById('search-input');
    if(searchInput) {
        searchInput.addEventListener('input', function(e) {
            const keyword = e.target.value.toLowerCase().trim();
            const filtered = currentRooms.filter(r => 
                r.roomCode.toLowerCase().includes(keyword) || 
                r.roomName.toLowerCase().includes(keyword)
            );
            renderTable(filtered);
        });
    }
});

// Goi API lay danh sach phong
async function loadRooms() {
    try {
        currentRooms = await apiGet('/rooms');
        renderTable(currentRooms);
    } catch (err) {
        showAlert('Lỗi khi tải danh sách phòng!', 'error');
    }
}

// Render data ra bang
function renderTable(rooms) {
    const tbody = document.getElementById('rooms-tbody');
    if (!tbody) return;
    
    tbody.innerHTML = '';
    
    if (rooms.length === 0) {
        tbody.innerHTML = '<tr><td colspan="6" style="text-align:center;">Không tìm thấy phòng nào</td></tr>';
        return;
    }
    
    rooms.forEach(room => {
        const tr = document.createElement('tr');
        
        // CSS badge trang thai
        let statusBadge = 'bg-secondary';
        if (room.status === 'Trống') statusBadge = 'bg-success';
        else if (room.status === 'Đã đặt') statusBadge = 'bg-warning';
        else if (room.status === 'Đang sử dụng') statusBadge = 'bg-danger';
        
        tr.innerHTML = `
            <td><strong>${room.roomCode}</strong></td>
            <td>${room.roomName}</td>
            <td>${room.roomType}</td>
            <td>${formatMoney(room.price)}</td>
            <td><span class="badge ${statusBadge}">${room.status}</span></td>
            <td>
                <button class="btn btn-primary" onclick='openEditModal(${JSON.stringify(room).replace(/'/g, "&#39;")})'>Sửa</button>
                <button class="btn btn-danger" onclick="deleteRoom(${room.roomId}, '${room.roomCode}')">Xóa</button>
            </td>
        `;
        tbody.appendChild(tr);
    });
}

// Mo modal them moi
function openAddModal() {
    document.getElementById('modal-title').textContent = 'Thêm Phòng Mới';
    document.getElementById('room-id').value = '';
    document.getElementById('room-code').value = '';
    document.getElementById('room-name').value = '';
    document.getElementById('room-type').value = '';
    document.getElementById('room-price').value = '';
    document.getElementById('room-status').value = 'Trống';
    document.getElementById('room-desc').value = '';
    
    openModal('room-modal');
}

// Mo modal sua phong
function openEditModal(room) {
    document.getElementById('modal-title').textContent = 'Sửa Phòng';
    document.getElementById('room-id').value = room.roomId;
    document.getElementById('room-code').value = room.roomCode;
    document.getElementById('room-name').value = room.roomName;
    document.getElementById('room-type').value = room.roomType;
    document.getElementById('room-price').value = room.price;
    document.getElementById('room-status').value = room.status;
    document.getElementById('room-desc').value = room.description || '';
    
    openModal('room-modal');
}

// Luu phong (Them hoac Sua)
async function saveRoom() {
    const id = document.getElementById('room-id').value;
    const roomCode = document.getElementById('room-code').value.trim();
    const roomName = document.getElementById('room-name').value.trim();
    const roomType = document.getElementById('room-type').value;
    const price = document.getElementById('room-price').value;
    const status = document.getElementById('room-status').value;
    const description = document.getElementById('room-desc').value.trim();
    
    // Validate
    if (!roomCode || !roomName || !roomType || !price) {
        showAlert('Vui lòng nhập đầy đủ thông tin có dấu *', 'error');
        return;
    }
    
    const data = {
        roomCode, 
        roomName, 
        roomType, 
        price: Number(price), 
        status, 
        description
    };
    
    try {
        let res;
        if (id) {
            // Cap nhat (PUT)
            res = await apiPut('/rooms/' + id, data);
        } else {
            // Them moi (POST)
            res = await apiPost('/rooms', data);
        }
        
        if (res.success !== false) {
            showAlert(res.message || 'Lưu thành công!', 'success');
            closeModal();
            loadRooms(); // Tai lai bang sau khi luu
        } else {
            showAlert(res.message, 'error'); // Loi nghiep vu tu backend (VD: trung ma phong)
        }
    } catch (err) {
        showAlert('Có lỗi xảy ra khi lưu phòng!', 'error');
    }
}

// Xoa phong
async function deleteRoom(id, code) {
    if (!confirm(`Bạn có chắc chắn muốn xóa phòng [${code}] không?`)) {
        return;
    }
    
    try {
        const res = await apiDelete('/rooms/' + id);
        if (res.success !== false) {
            showAlert(res.message || 'Xóa thành công!', 'success');
            loadRooms(); // Tai lai bang
        } else {
            showAlert(res.message, 'error'); // Loi khoa ngoai tu backend
        }
    } catch (err) {
        showAlert('Có lỗi xảy ra khi xóa phòng!', 'error');
    }
}
