checkLogin();
let currentRooms = [], roomsLoadFailed = false, roomsVersion = 0, roomSaving = false;
const roomPending = new Set();
let roomSearchTimer, roomsBusinessDay = '';
function roomAdmin() { return currentUser?.role === 'admin'; }
document.addEventListener('DOMContentLoaded', async () => {
    if (!await checkLogin()) return;
    document.getElementById('room-add-btn').hidden = !roomAdmin();
    loadRooms();
    document.getElementById('search-input').addEventListener('input', () => {
        clearTimeout(roomSearchTimer);
        // Invalidate the older request immediately, before the debounce expires.
        roomsVersion++; roomSearchTimer = setTimeout(loadRooms,250);
    });
    ['filter-type','filter-status'].forEach(id => document.getElementById(id).addEventListener('change',loadRooms));
    window.addEventListener('focus',loadRooms);
    document.addEventListener('visibilitychange',() => { if (!document.hidden) loadRooms(); });
    setInterval(() => { if (!document.hidden && roomsBusinessDay !== vietnamToday()) loadRooms(); },30000);
});
async function loadRooms() {
    const version = ++roomsVersion, day = vietnamToday();
    const params = new URLSearchParams();
    for (const [key,id] of [['type','filter-type'],['status','filter-status'],['search','search-input']]) {
        const value = document.getElementById(id).value.trim(); if (value) params.set(key,value);
    }
    tableState('rooms-tbody',6,'Đang tải phòng...');
    try {
        const list = expectList(await apiGet('/rooms'+(params.size ? '?'+params : '')));
        if (version !== roomsVersion) return;
        currentRooms = list; roomsLoadFailed = false; roomsBusinessDay = day; renderTable(list);
    } catch (err) {
        if (version !== roomsVersion) return;
        currentRooms = []; roomsLoadFailed = true;
        tableState('rooms-tbody',6,err.message || 'Không tải được danh sách phòng.'); showRequestError(err,'Lỗi tải phòng.');
    }
}
function renderTable(rooms) {
    const body = document.getElementById('rooms-tbody'); body.replaceChildren();
    if (!rooms.length) { tableState('rooms-tbody',6,'Không tìm thấy phòng phù hợp.'); return; }
    rooms.forEach(room => {
        const actions = roomAdmin() ? [actionButton('Sửa','btn btn-primary',() => openEditModal(room)),
            actionButton(room.status === 'Bảo trì' ? 'Kích hoạt' : 'Bảo trì','btn btn-secondary',() => changeRoomStatus(room)),
            actionButton('Xóa','btn btn-danger',() => deleteRoom(room.roomId,room.roomCode))] : [];
        actions.forEach(button => { button.disabled = roomPending.has(room.roomId); });
        appendTableRow(body,[textNode('strong',room.roomCode),room.roomName,room.roomType,formatMoney(room.price),
            textNode('span',room.status,'badge '+({'Trống':'badge-available','Đã đặt':'badge-booked','Đang sử dụng':'badge-occupied','Bảo trì':'badge-maintenance'}[room.status] || ''))],actions);
    });
}
function openAddModal() {
    if (!roomAdmin() || roomSaving) return;
    document.getElementById('modal-title').textContent = 'Thêm phòng mới';
    ['id','code','name','type','price','desc'].forEach(key => { document.getElementById('room-'+key).value = ''; });
    openModal('room-modal');
}
function openEditModal(room) {
    if (!roomAdmin() || roomSaving || roomPending.has(room.roomId)) return;
    document.getElementById('modal-title').textContent = 'Sửa phòng';
    for (const [key,value] of Object.entries({id:room.roomId,code:room.roomCode,name:room.roomName,type:room.roomType,price:room.price,desc:room.description || ''})) {
        document.getElementById('room-'+key).value = String(value);
    }
    openModal('room-modal');
}
function closeRoomModal() { if (!roomSaving) document.getElementById('room-modal').classList.remove('show'); }
async function saveRoom() {
    if (!roomAdmin() || roomSaving) return;
    const get = key => document.getElementById('room-'+key).value.trim();
    const id = get('id');
    try {
        const data = {roomCode:validText(get('code'),'Mã phòng',20),roomName:validText(get('name'),'Tên phòng',100),
            roomType:validEnum(get('type'),['Single','Double','VIP','Family'],'Loại phòng'),price:validMoney(get('price'),'Giá phòng',1),description:validText(get('desc'),'Mô tả',500,false)};
        roomSaving = true;
        document.querySelectorAll('#room-modal input, #room-modal select, #room-modal textarea, #room-modal button').forEach(el => { el.disabled = true; });
        const res = id ? await apiPut('/rooms/'+id,data) : await apiPost('/rooms',data);
        if (res.success !== true) throw new Error(res.message || 'Chưa xác nhận lưu phòng.');
        document.getElementById('room-modal').classList.remove('show'); showAlert(res.message || 'Đã lưu phòng.','success');
    } catch (err) { showRequestError(err,'Không thể lưu phòng.'); }
    finally {
        roomSaving = false;
        document.querySelectorAll('#room-modal input, #room-modal select, #room-modal textarea, #room-modal button').forEach(el => { el.disabled = false; });
        loadRooms();
    }
}
async function roomMutation(id,write) {
    if (!roomAdmin() || roomPending.has(id)) return;
    roomPending.add(id); roomsVersion++; renderTable(currentRooms);
    try { const res = await write(); if (res.success !== true) throw new Error(res.message || 'Chưa xác nhận kết quả.'); showAlert(res.message || 'Đã cập nhật phòng.','success'); }
    catch (err) { showRequestError(err,'Không thể cập nhật phòng.'); }
    finally { roomPending.delete(id); await loadRooms(); }
}
async function changeRoomStatus(room) {
    if (!roomAdmin() || roomPending.has(room.roomId)) return;
    const status = room.status === 'Bảo trì' ? 'Trống' : 'Bảo trì';
    if (!confirm((status === 'Bảo trì' ? 'Bật bảo trì phòng ' : 'Kích hoạt lại phòng ')+room.roomCode+'? Trạng thái sẽ được tính lại từ booking.')) return;
    await roomMutation(room.roomId,() => apiPut('/rooms/'+room.roomId+'/status',{status}));
}
async function deleteRoom(id,code) {
    if (!roomAdmin() || roomPending.has(id) || !confirm('Xóa phòng ['+code+']? Phòng có lịch sử đặt sẽ không thể xóa.')) return;
    await roomMutation(id,() => apiDelete('/rooms/'+id));
}
