checkLogin();
let currentRooms = [], roomsLoadFailed = false, roomsVersion = 0, roomSaving = false;
const roomPending = new Set();
let roomSearchTimer, roomsBusinessDay = '';
let roomsExportReady = false;
let roomInitialLinkOpened=false;
function roomAdmin() { return currentUser?.role === 'admin'; }
document.addEventListener('DOMContentLoaded', async () => {
    if (!await checkLogin()) return;
    document.getElementById('room-add-btn').hidden = !roomAdmin();
    loadRooms();
    document.getElementById('search-input').addEventListener('input', () => {
        roomsExportReady = false;document.getElementById('rooms-export').disabled = true;
        clearTimeout(roomSearchTimer);
        // Invalidate the older request immediately, before the debounce expires.
        roomsVersion++; roomSearchTimer = setTimeout(loadRooms,250);
    });
    ['filter-type','filter-status','filter-pricing'].forEach(id => document.getElementById(id).addEventListener('change',loadRooms));
    window.addEventListener('focus',loadRooms);
    document.addEventListener('visibilitychange',() => { if (!document.hidden) loadRooms(); });
    setInterval(() => { if (!document.hidden && !stayBusy && !document.getElementById('room-stay-modal').classList.contains('show')) loadRooms(); },60000);
});
async function loadRooms() {
    roomsExportReady = false;document.getElementById('rooms-export').disabled = true;
    const version = ++roomsVersion, day = vietnamToday();
    const params = new URLSearchParams();
    for (const [key,id] of [['type','filter-type'],['search','search-input']]) {
        const value = document.getElementById(id).value.trim(); if (value) params.set(key,value);
    }
    roomGridState('Đang tải phòng...');
    document.getElementById('rooms-result').textContent = 'Đang tải phòng…';
    try {
        const [raw,bookings] = await Promise.all([apiGet('/rooms'+(params.size ? '?'+params : '')),apiGet('/bookings')]);
        const list = expectList(raw);
        if (version !== roomsVersion) return;
        roomBookings = expectList(bookings);
        const selected=document.getElementById('filter-status').value;
        const statusMap={'Còn trống':'Trống','Đã đặt · Chưa nhận phòng':'Đã đặt','Có khách đang ở':'Đang sử dụng','Bảo trì':'Bảo trì'};
        currentRooms = selected ? list.filter(r=>statusMap[roomCardStatus(r)[0]]===selected) : list;
        const pricing=document.getElementById('filter-pricing').value;
        if(pricing==='ready')currentRooms=currentRooms.filter(room=>Number(room.hourlyPrice)>0);
        if(pricing==='missing')currentRooms=currentRooms.filter(room=>!(Number(room.hourlyPrice)>0));
        roomsLoadFailed = false; roomsBusinessDay = day; renderTable(currentRooms);
        roomsExportReady = true;document.getElementById('rooms-export').disabled = false;
        if(!roomInitialLinkOpened){
            roomInitialLinkOpened=true;
            const requested=new URLSearchParams(window.location.search).get('roomId');
            if(requested && /^[1-9]\d*$/.test(requested)){
                const room=list.find(item=>item.roomId===Number(requested));
                if(room)await openRoomStay(room);
                else showAlert('Phòng không còn trong danh mục. Vui lòng chọn phòng khác.','error');
            }
        }
    } catch (err) {
        if (version !== roomsVersion) return;
        currentRooms = []; roomsLoadFailed = true;
        document.getElementById('rooms-result').textContent = 'Chưa tải được danh sách. Hãy thử tải lại.';
        roomGridState(err.message || 'Không tải được danh sách phòng.'); showRequestError(err,'Lỗi tải phòng.');
    }
}
function exportRooms() {
    return downloadCsv('phong-'+vietnamToday()+'.csv',[
        {label:'Mã phòng',value:r=>r.roomCode,text:true},{label:'Tên phòng',value:r=>r.roomName,text:true},
        {label:'Loại',value:r=>r.roomType},{label:'Giá mỗi đêm (VND)',value:r=>r.price},{label:'Giá mỗi giờ (VND)',value:r=>r.hourlyPrice ?? ''},{label:'Trạng thái',value:r=>roomCardStatus(r)[0]}
    ],currentRooms,roomsExportReady && !roomsLoadFailed);
}
function roomGridState(message) {
    const body=document.getElementById('rooms-tbody');body.replaceChildren(textNode('p',message,'room-grid-state'));
    document.getElementById('pager-rooms').hidden=true;document.getElementById('pager-rooms').replaceChildren();
}
function renderTable(rooms) {
    const body=document.getElementById('rooms-tbody');body.replaceChildren();
    document.getElementById('rooms-result').textContent=rooms.length+' phòng theo bộ lọc · Chọn một thẻ để đặt, nhận hoặc trả phòng';
    if(!rooms.length) {roomGridState(roomsLoadFailed ? 'Chưa tải được phòng. Hãy thử lại.' : 'Không có phòng phù hợp với bộ lọc hiện tại.');return;}
    pageRows('rooms',rooms,renderTable).forEach(room=>{
        const card=actionButton('','room-tile',()=>openRoomStay(room));
        card.setAttribute('aria-label','Mở phòng '+room.roomCode+' · '+room.roomName);
        card.disabled=roomPending.has(room.roomId);
        const top=textNode('span','','room-tile-top');
        top.appendChild(textNode('span',room.roomCode,'room-code'));
        top.appendChild(textNode('span',room.roomType,'room-type'));card.appendChild(top);
        card.appendChild(textNode('strong',room.roomName,'room-tile-name'));
        const [status,style]=roomCardStatus(room);
        card.className+=' room-tile-'+({'badge-available':'available','badge-booked':'booked','badge-occupied':'occupied','badge-maintenance':'maintenance'}[style] || 'available');
        card.appendChild(textNode('span',status,'badge '+style));
        const price=textNode('span','','room-tile-price');
        price.appendChild(textNode('strong',room.hourlyPrice ? formatMoney(room.hourlyPrice) : 'Chưa có giá giờ',room.hourlyPrice?'':'price-unset'));
        price.appendChild(textNode('span',room.hourlyPrice ? ' / giờ' : ''));
        card.appendChild(price);
        card.appendChild(textNode('span',room.hourlyPrice ? 'Nhận đặt theo khung giờ' : 'Cần nhập giá để mở đặt theo giờ','room-night-price'));
        const next=roomStayBookings(room.roomId)[0];
        card.appendChild(textNode('span',room.status==='Bảo trì' ? 'Tạm ngừng nhận đặt phòng' : next ? next.customerName+' · '+bookingTime(next,next.status==='CheckedIn'?'checkout':'checkin') : 'Sẵn sàng cho lượt đặt mới','room-tile-guest'));
        body.appendChild(card);
    });
}
function openAddModal() {
    if (!roomAdmin() || roomSaving) return;
    clearRoomErrors();
    document.getElementById('modal-title').textContent = 'Thêm phòng mới';
    ['id','code','name','type','price','hourly-price','desc'].forEach(key => { document.getElementById('room-'+key).value = ''; });
    openModal('room-modal');
}
function openEditModal(room) {
    if (!roomAdmin() || roomSaving || roomPending.has(room.roomId)) return;
    clearRoomErrors();
    document.getElementById('modal-title').textContent = 'Sửa phòng';
    for (const [key,value] of Object.entries({id:room.roomId,code:room.roomCode,name:room.roomName,type:room.roomType,price:room.price,'hourly-price':room.hourlyPrice ?? '',desc:room.description || ''})) {
        document.getElementById('room-'+key).value = String(value);
    }
    openModal('room-modal');
    document.getElementById('room-hourly-price').focus();
}
function closeRoomModal() { if (!roomSaving) document.getElementById('room-modal').classList.remove('show'); }
function clearRoomErrors() {
    document.getElementById('room-form-error').hidden = true;
    ['code','name','type','price','hourly-price','desc'].forEach(key => {
        document.getElementById('room-'+key).setAttribute('aria-invalid','false');
        document.getElementById('room-'+key+'-error').hidden = true;
    });
}
async function saveRoom() {
    if (!roomAdmin() || roomSaving) return;
    clearRoomErrors();
    const get = key => document.getElementById('room-'+key).value.trim();
    const id = get('id');
    const data = {};
    let firstInvalid;
    for (const [key,property,validate] of [
        ['code','roomCode',() => validText(get('code'),'Mã phòng',20)],
        ['name','roomName',() => validText(get('name'),'Tên phòng',100)],
        ['type','roomType',() => validEnum(get('type'),['Single','Double','VIP','Family'],'Loại phòng')],
        ['price','price',() => validMoney(get('price'),'Giá phòng',1)],
        ['hourly-price','hourlyPrice',() => get('hourly-price') ? validMoney(get('hourly-price'),'Giá giờ',1) : null],
        ['desc','description',() => validText(get('desc'),'Mô tả',500,false)]
    ]) {
        try { data[property] = validate(); }
        catch (err) {
            const input = document.getElementById('room-'+key), error = document.getElementById('room-'+key+'-error');
            input.setAttribute('aria-invalid','true'); error.textContent = err.message; error.hidden = false;
            firstInvalid ||= input;
        }
    }
    if (firstInvalid) { firstInvalid.focus(); return; }
    try {
        roomSaving = true;
        document.getElementById('room-save-btn').textContent = 'Đang lưu…';
        document.getElementById('room-save-btn').setAttribute('aria-busy','true');
        document.querySelectorAll('#room-modal input, #room-modal select, #room-modal textarea, #room-modal button').forEach(el => { el.disabled = true; });
        const res = id ? await apiPut('/rooms/'+id,data) : await apiPost('/rooms',data);
        if (res.success !== true) throw new Error(res.message || 'Chưa xác nhận lưu phòng.');
        document.getElementById('room-modal').classList.remove('show'); showAlert(res.message || 'Đã lưu phòng.','success');
    } catch (err) {
        const error = document.getElementById('room-form-error');
        error.textContent = err.message || 'Không thể lưu phòng.'; error.hidden = false;
        showRequestError(err,'Không thể lưu phòng.');
    }
    finally {
        roomSaving = false;
        document.getElementById('room-save-btn').textContent = 'Lưu phòng';
        document.getElementById('room-save-btn').setAttribute('aria-busy','false');
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
    if (!await askConfirmation((status === 'Bảo trì' ? 'Bật bảo trì phòng ' : 'Kích hoạt lại phòng ')+room.roomCode+'? Trạng thái sẽ được tính lại từ booking.')) return;
    await roomMutation(room.roomId,() => apiPut('/rooms/'+room.roomId+'/status',{status}));
}
async function deleteRoom(id,code) {
    if (!roomAdmin() || roomPending.has(id) || !await askConfirmation('Xóa phòng ['+code+']? Phòng có lịch sử đặt sẽ không thể xóa.')) return;
    await roomMutation(id,() => apiDelete('/rooms/'+id));
}
