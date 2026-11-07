// NOTE FRONTEND: Ghép dữ liệu phòng và booking để hiển thị thẻ phòng; thao tác danh mục chỉ dành cho admin.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
checkLogin();
let currentRooms = [], roomsLoadFailed = false, roomsVersion = 0, roomSaving = false;
const roomPending = new Set();
let roomSearchTimer, roomsBusinessDay = '';
let roomsExportReady = false;
let roomInitialLinkOpened=false;
let roomProperties=[];
// NOTE FRONTEND: Tải danh sách cơ sở vào form thêm/sửa, chọn cơ sở hiện tại và có lựa chọn dự phòng khi tải lỗi.
async function roomPropertyOptions(selected){
    const select=document.getElementById('room-property');select.disabled=true;select.dataset.propertyId=String(selected || selectedPropertyId() || 1);
    try{
        if(!roomProperties.length)roomProperties=expectList(await apiGet('/public/properties'));
        select.replaceChildren();roomProperties.forEach(p=>select.append(selectOption(String(p.propertyId),p.propertyName)));
        select.value=String(selected || selectedPropertyId() || 1);select.disabled=false;
    }catch(_){select.replaceChildren(selectOption(String(selected || 1),'Cơ sở '+(selected || 1)));select.value=String(selected || 1);}
}
// NOTE FRONTEND: Kiểm tra quyền để điều khiển nút trên giao diện; backend vẫn chặn độc lập.
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
// NOTE FRONTEND: Tải phòng và booking song song, lọc trạng thái/giá, bỏ kết quả cũ và mở phòng được truyền từ URL nếu có.
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
// NOTE FRONTEND: Xuất danh sách phòng đang lọc khi tải thành công, không xuất dữ liệu trong lúc đang lỗi/chờ tải.
function exportRooms() {
    return downloadCsv('phong-'+vietnamToday()+'.csv',[
        {label:'Mã phòng',value:r=>r.roomCode,text:true},{label:'Tên phòng',value:r=>r.roomName,text:true},
        {label:'Cơ sở',value:r=>r.propertyName || '',text:true},{label:'Địa chỉ cơ sở',value:r=>r.propertyAddress || '',text:true},
        {label:'Loại',value:r=>r.roomType},{label:'Giá đêm ngày thường (VND)',value:r=>r.price},{label:'Giá đêm cuối tuần (VND)',value:r=>r.nightlyPricing?.weekendPrice ?? ''},{label:'Đêm cuối tuần',value:r=>r.nightlyPricing?weekendDaysLabel(r.nightlyPricing.weekendDays):''},{label:'Giá giờ/giờ lẻ (VND)',value:r=>r.hourlyPrice ?? ''},{label:'Gói 2 giờ (VND)',value:r=>r.hourlyPackages?.twoHours ?? ''},{label:'Gói 4 giờ (VND)',value:r=>r.hourlyPackages?.fourHours ?? ''},{label:'Gói 8 giờ (VND)',value:r=>r.hourlyPackages?.eightHours ?? ''},{label:'Trạng thái',value:r=>roomCardStatus(r)[0]}
    ],currentRooms,roomsExportReady && !roomsLoadFailed);
}
// NOTE FRONTEND: Đặt trạng thái đang tải/trống/lỗi cho vùng thẻ phòng.
function roomGridState(message) {
    const body=document.getElementById('rooms-tbody');body.replaceChildren(textNode('p',message,'room-grid-state'));
    document.getElementById('pager-rooms').hidden=true;document.getElementById('pager-rooms').replaceChildren();
}
// NOTE FRONTEND: Dựng thẻ phòng, ảnh, giá, trạng thái từ lịch và các thao tác phù hợp với quyền.
function renderTable(rooms) {
    const body=document.getElementById('rooms-tbody');body.replaceChildren();
    document.getElementById('rooms-result').textContent=rooms.length+' phòng theo bộ lọc · Chọn một thẻ để đặt, nhận hoặc trả phòng';
    if(!rooms.length) {roomGridState(roomsLoadFailed ? 'Chưa tải được phòng. Hãy thử lại.' : 'Không có phòng phù hợp với bộ lọc hiện tại.');return;}
    pageRows('rooms',rooms,renderTable).forEach(room=>{
        const card=actionButton('','room-tile',()=>openRoomStay(room));
        card.setAttribute('aria-label','Mở phòng '+room.roomCode+' · '+room.roomName);
        card.disabled=roomPending.has(room.roomId);
        const photo=RoomPhotos.create(room);if(photo)card.append(photo);
        const top=textNode('span','','room-tile-top');
        top.appendChild(textNode('span',room.roomCode,'room-code'));
        top.appendChild(textNode('span',room.roomType,'room-type'));card.appendChild(top);
        card.appendChild(textNode('strong',room.roomName,'room-tile-name'));
        if(room.propertyName)card.appendChild(textNode('span',room.propertyName+' · '+room.propertyAddress,'room-property-address'));
        const [status,style]=roomCardStatus(room);
        card.className+=' room-tile-'+({'badge-available':'available','badge-booked':'booked','badge-occupied':'occupied','badge-maintenance':'maintenance'}[style] || 'available');
        card.appendChild(textNode('span',status,'badge '+style));
        const price=textNode('span','','room-tile-price');
        price.appendChild(textNode('strong',room.hourlyPackages ? formatMoney(room.hourlyPackages.twoHours) : room.hourlyPrice ? formatMoney(room.hourlyPrice) : 'Chưa có giá giờ',room.hourlyPrice?'':'price-unset'));
        price.appendChild(textNode('span',room.hourlyPackages ? ' / 2 giờ' : room.hourlyPrice ? ' / giờ' : ''));
        card.appendChild(price);
        card.appendChild(textNode('span',room.hourlyPackages ? hourlyPriceLabel(room) : room.hourlyPrice ? 'Nhận đặt theo khung giờ' : 'Cần nhập giá để mở đặt theo giờ','room-night-price'));
        const next=roomStayBookings(room.roomId)[0];
        card.appendChild(textNode('span',room.status==='Bảo trì' ? 'Tạm ngừng nhận đặt phòng' : next ? next.customerName+' · '+bookingTime(next,next.status==='CheckedIn'?'checkout':'checkin') : 'Sẵn sàng cho lượt đặt mới','room-tile-guest'));
        body.appendChild(card);
    });
}
// NOTE FRONTEND: Xóa trạng thái chỉnh sửa và chuẩn bị form tạo phòng với cơ sở mặc định.
function openAddModal() {
    if (!roomAdmin() || roomSaving) return;
    clearRoomErrors();
    document.getElementById('modal-title').textContent = 'Thêm phòng mới';
    ['id','code','name','type','price','weekend-price','hourly-price','package-2','package-4','package-8','desc'].forEach(key => { document.getElementById('room-'+key).value = ''; });
    document.getElementById('room-weekend-days').value='6,7';
    roomPropertyOptions(selectedPropertyId() || 1);
    openModal('room-modal');
}
// NOTE FRONTEND: Nạp thông tin và bảng giá của phòng vào form để cập nhật đúng ID.
function openEditModal(room) {
    if (!roomAdmin() || roomSaving || roomPending.has(room.roomId)) return;
    clearRoomErrors();
    document.getElementById('modal-title').textContent = 'Sửa phòng';
    roomPropertyOptions(room.propertyId || 1);
    for (const [key,value] of Object.entries({id:room.roomId,code:room.roomCode,name:room.roomName,type:room.roomType,price:room.price,'hourly-price':room.hourlyPrice ?? '',desc:room.description || ''})) {
        document.getElementById('room-'+key).value = String(value);
    }
    openModal('room-modal');
    document.getElementById('room-hourly-price').focus();
    for(const [hours,key] of [[2,'twoHours'],[4,'fourHours'],[8,'eightHours']])document.getElementById('room-package-'+hours).value=String(room.hourlyPackages?.[key] ?? '');
    document.getElementById('room-weekend-price').value=String(room.nightlyPricing?.weekendPrice ?? '');
    const days=room.nightlyPricing?.weekendDays.join(',') || '6,7';
    const select=document.getElementById('room-weekend-days');
    select.replaceChildren(selectOption('6,7','Đêm thứ Bảy và Chủ nhật'),selectOption('5,6','Đêm thứ Sáu và thứ Bảy'));
    if(!['6,7','5,6'].includes(days))select.append(selectOption(days,weekendDaysLabel(room.nightlyPricing.weekendDays)));
    select.value=days;
}
// NOTE FRONTEND: Đóng form nếu không có thao tác lưu đang chạy; không cho cắt ngang yêu cầu ghi.
function closeRoomModal() { if (!roomSaving) document.getElementById('room-modal').classList.remove('show'); }
// NOTE FRONTEND: Dọn thông báo/đánh dấu sai cũ trước khi kiểm tra hoặc đổi form.
function clearRoomErrors() {
    document.getElementById('room-form-error').hidden = true;
    ['code','name','type','price','hourly-price','desc'].forEach(key => {
        document.getElementById('room-'+key).setAttribute('aria-invalid','false');
        document.getElementById('room-'+key+'-error').hidden = true;
    });
}
// NOTE FRONTEND: Kiểm tra thông tin, giá giờ/gói/cuối tuần và cơ sở; dùng POST khi thêm, PUT khi sửa.
async function saveRoom() {
    if (!roomAdmin() || roomSaving) return;
    clearRoomErrors();
    const get = key => document.getElementById('room-'+key).value.trim();
    const id = get('id');
    const data = {};
    const property=Number(get('property') || document.getElementById('room-property').dataset.propertyId || selectedPropertyId() || 1);
    if(!Number.isInteger(property) || property<1){showAlert('Chọn cơ sở hợp lệ.','error');return;}
    data.propertyId=property;
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
        const values=[2,4,8].map(h=>get('package-'+h));
        data.hourlyPackages=values.some(Boolean)?{twoHours:validMoney(values[0],'Gói 2 giờ',1),fourHours:validMoney(values[1],'Gói 4 giờ',1),eightHours:validMoney(values[2],'Gói 8 giờ',1)}:null;
        if(data.hourlyPackages)quoteHourly(data.hourlyPrice,data.hourlyPackages,2);
        data.nightlyPricing=get('weekend-price')?{weekendPrice:validMoney(get('weekend-price'),'Giá cuối tuần',1),weekendDays:get('weekend-days').split(',').map(Number)}:null;
        if(data.nightlyPricing)quoteNightly(data.price,data.nightlyPricing,'2031-01-01','2031-01-08');
    }catch(err){const error=document.getElementById('room-form-error');error.textContent=err.message;error.hidden=false;return;}
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
// NOTE FRONTEND: Chạy thao tác ghi một phòng với khóa chống gửi lặp, xử lý lỗi và tải lại danh mục.
async function roomMutation(id,write) {
    if (!roomAdmin() || roomPending.has(id)) return;
    roomPending.add(id); roomsVersion++; renderTable(currentRooms);
    try { const res = await write(); if (res.success !== true) throw new Error(res.message || 'Chưa xác nhận kết quả.'); showAlert(res.message || 'Đã cập nhật phòng.','success'); }
    catch (err) { showRequestError(err,'Không thể cập nhật phòng.'); }
    finally { roomPending.delete(id); await loadRooms(); }
}
// NOTE FRONTEND: Yêu cầu đổi Trống/Bảo trì sau xác nhận; server kiểm tra lịch trước khi chấp nhận.
async function changeRoomStatus(room) {
    if (!roomAdmin() || roomPending.has(room.roomId)) return;
    const status = room.status === 'Bảo trì' ? 'Trống' : 'Bảo trì';
    if (!await askConfirmation((status === 'Bảo trì' ? 'Bật bảo trì phòng ' : 'Kích hoạt lại phòng ')+room.roomCode+'? Trạng thái sẽ được tính lại từ booking.')) return;
    await roomMutation(room.roomId,() => apiPut('/rooms/'+room.roomId+'/status',{status}));
}
// NOTE FRONTEND: Yêu cầu xóa sau xác nhận; phòng đã có lịch sử đặt được server giữ lại và trả lỗi phù hợp.
async function deleteRoom(id,code) {
    if (!roomAdmin() || roomPending.has(id) || !await askConfirmation('Xóa phòng ['+code+']? Phòng có lịch sử đặt sẽ không thể xóa.')) return;
    await roomMutation(id,() => apiDelete('/rooms/'+id));
}
