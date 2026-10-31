let stayRoom = null, stayVersion = 0, stayBusy = false, stayReady = false, stayPreview = null;
let roomBookings = [], stayCustomers = [];
const stayEl = id => document.getElementById('stay-'+id);
function roomStayBookings(roomId) {
    const now = vietnamNow();
    return roomBookings.filter(b => b.roomId === roomId && (b.status === 'CheckedIn'
        || (b.status === 'Confirmed' && (b.checkoutAt || b.checkoutDate+'T00:00') > now)))
        .sort((a,b) => (a.status === 'CheckedIn' ? -1 : b.status === 'CheckedIn' ? 1
            : (a.checkinAt || a.checkinDate).localeCompare(b.checkinAt || b.checkinDate)));
}
function roomCardStatus(room) {
    if (room.status === 'Bảo trì') return ['Bảo trì','badge-maintenance'];
    const bookings = roomStayBookings(room.roomId);
    if (bookings.some(b => b.status === 'CheckedIn') || room.status === 'Đang sử dụng') return ['Có khách đang ở','badge-occupied'];
    if (bookings.length || room.status === 'Đã đặt') return ['Đã đặt · Chưa nhận phòng','badge-booked'];
    return ['Còn trống','badge-available'];
}
function closeRoomStay() {
    if (stayBusy) return;
    stayVersion++; stayReady = false; stayRoom = null; stayPreview = null;
    document.getElementById('room-stay-modal').classList.remove('show');
}
function stayMessage(message, error = false) {
    stayEl('message').textContent = message;
    stayEl('message').className = error ? 'form-error' : 'text-muted';
}
function lockStay(locked) {
    stayBusy = locked;
    if (locked) stayEl('title').focus();
    document.querySelectorAll('#room-stay-modal button, #room-stay-modal input, #room-stay-modal select, #room-stay-modal textarea').forEach(el => { el.disabled = locked; });
    stayEl('save').disabled = locked || !stayReady || !stayRoom?.hourlyPrice || stayRoom?.status === 'Bảo trì';
    stayEl('close').disabled = locked;
    stayEl('save').setAttribute('aria-busy',String(locked));
    if (!locked && stayReady && stayRoom) renderStayBookings();
    if (!locked) stayEl('close').focus();
}
async function openRoomStay(room) {
    if (stayBusy) return;
    const version = ++stayVersion; stayRoom = room; stayReady = false; stayPreview = null;
    stayEl('title').textContent = room.roomCode+' · '+room.roomName;
    stayEl('summary').textContent = room.roomType+' · '+(room.hourlyPrice ? formatMoney(room.hourlyPrice)+'/giờ' : 'Chưa có giá giờ');
    stayEl('bookings').replaceChildren(); stayEl('invoice').hidden = true;
    stayEl('customer').replaceChildren(); stayEl('note').value = '';
    const start = new Date(Date.now()+7*3600000+5*60000).toISOString().slice(0,16);
    stayEl('checkin').value = start; stayEl('checkin').min = vietnamNow().slice(0,16);
    stayEl('checkout').value = new Date(Date.parse(start+'+07:00')+7*3600000+2*3600000).toISOString().slice(0,16);
    stayEl('estimate').textContent = '';stayEl('form').hidden = false;
    openModal('room-stay-modal');lockStay(true);stayMessage('Đang tải lịch phòng và khách hàng…');
    try {
        const [fresh, bookings, customers] = await Promise.all([apiGet('/rooms/'+room.roomId),apiGet('/bookings'),apiGet('/customers')]);
        if (version !== stayVersion) return;
        if (fresh.roomId !== room.roomId) throw new Error('Chưa xác nhận được thông tin phòng.');
        roomBookings = expectList(bookings);stayCustomers = expectList(customers);stayRoom = fresh;stayReady = true;
        stayEl('summary').textContent = fresh.roomType+' · '+roomCardStatus(fresh)[0]+' · '+(fresh.description || 'Chưa có mô tả');
        const placeholder = textNode('option','Chọn khách hàng');placeholder.value = '';stayEl('customer').appendChild(placeholder);
        stayCustomers.forEach(c => { const option=textNode('option',c.fullName+' · '+(c.phone || c.cccd || ''));option.value=String(c.customerId);stayEl('customer').appendChild(option); });
        renderStayBookings();renderTable(currentRooms);
        stayMessage(fresh.hourlyPrice ? 'Giờ Việt Nam · Giá '+formatMoney(fresh.hourlyPrice)+'/giờ. Khung giờ đặt được làm tròn lên mỗi giờ.'
            : 'Chưa khai báo giá giờ. Quản lý chọn “Sửa thông tin phòng” để nhập giá trước khi đặt.');
        stayEl('form').hidden = fresh.status === 'Bảo trì';
        stayEl('edit').hidden = !roomAdmin();
        const admin=stayEl('admin-extra');admin.replaceChildren();
        if(roomAdmin()) {
            admin.appendChild(actionButton(fresh.status==='Bảo trì'?'Kích hoạt':'Bảo trì','btn btn-secondary',async()=>{closeRoomStay();await changeRoomStatus(fresh);}));
            admin.appendChild(actionButton('Xóa phòng','btn btn-danger',async()=>{closeRoomStay();await deleteRoom(fresh.roomId,fresh.roomCode);}));
        }
        updateStayEstimate();
    } catch (err) { if (version === stayVersion) {stayReady = false;stayMessage(err.message || 'Không tải được lịch phòng. Hãy đóng và mở lại.',true);} }
    finally { if (version === stayVersion) lockStay(false); }
}
function renderStayBookings() {
    const target=stayEl('bookings');target.replaceChildren();
    const list=roomStayBookings(stayRoom.roomId);
    if (!list.length) {target.appendChild(textNode('p','Chưa có lịch đặt sắp tới.','text-muted'));return;}
    list.forEach(b => {
        const item=textNode('article','','stay-booking');
        item.appendChild(textNode('strong','BK'+b.bookingId+' · '+b.customerName));
        item.appendChild(textNode('p',bookingTime(b,'checkin')+' → '+bookingTime(b,'checkout'),'text-muted'));
        item.appendChild(textNode('span',b.status === 'CheckedIn' ? 'Có khách đang ở' : 'Đã đặt · Chưa nhận phòng','badge '+(b.status === 'CheckedIn' ? 'badge-occupied' : 'badge-booked')));
        const actions=textNode('div','','room-actions');
        if (b.status === 'Confirmed') {
            const check=actionButton('Nhận phòng','btn btn-primary',() => stayTransition(b,'checkin'));
            const now=vietnamNow();check.disabled=now<(b.checkinAt || b.checkinDate+'T00:00') || now>=(b.checkoutAt || b.checkoutDate+'T00:00');
            if(check.disabled) check.title='Chỉ nhận trong khung ngày/giờ đã đặt.';
            actions.appendChild(check);
            actions.appendChild(actionButton('Hủy đặt','btn btn-secondary',() => stayTransition(b,'cancel')));
        } else actions.appendChild(actionButton('Trả phòng & thanh toán','btn btn-primary',() => previewStayCheckout(b)));
        item.appendChild(actions);target.appendChild(item);
    });
}
function updateStayEstimate() {
    stayEl('estimate').textContent='';
    try {
        const {hours,total}=stayInput();
        stayEl('estimate').textContent=hours+' giờ × '+formatMoney(stayRoom.hourlyPrice)+' = '+formatMoney(total)+' tiền phòng. Dịch vụ tính riêng khi trả phòng.';
    } catch (_) { /* Inline validation is shown on submission. */ }
}
function stayInput() {
    if (!stayReady || !stayRoom?.hourlyPrice) throw new Error('Phòng chưa có giá giờ hoặc chưa tải xong.');
    const start=stayEl('checkin').value,end=stayEl('checkout').value;
    const parse=value => {
        if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) throw new Error('Chọn đủ ngày và giờ nhận/trả phòng.');
        const time=Date.parse(value+'+07:00');
        if (!Number.isFinite(time) || new Date(time+7*3600000).toISOString().slice(0,16)!==value) throw new Error('Ngày giờ không hợp lệ.');
        return time;
    };
    const a=parse(start),b=parse(end);
    if (start<vietnamNow().slice(0,16) || b<=a) throw new Error('Giờ nhận không trong quá khứ; giờ trả phải sau giờ nhận.');
    const hours=Math.ceil((b-a)/3600000),total=validMoney(hours*validMoney(stayRoom.hourlyPrice,'Giá giờ',1),'Tiền phòng',1);
    return {start,end,hours,total};
}
async function saveRoomStay() {
    if (stayBusy || !stayReady || stayRoom?.status === 'Bảo trì') return;
    let data;
    try {
        const range=stayInput(),customerId=Number(stayEl('customer').value);
        if (!stayCustomers.some(c => c.customerId===customerId)) throw new Error('Hãy chọn khách hàng. Nếu chưa có, thêm tại mục Khách hàng.');
        data={billingUnit:'Hour',roomId:stayRoom.roomId,customerId,checkinAt:range.start,checkoutAt:range.end,note:validText(stayEl('note').value,'Ghi chú',500,false)};
    } catch(err) {stayMessage(err.message,true);return;}
    lockStay(true);stayMessage('Đang ghi nhận đặt phòng…');
    try {
        const result=await apiPost('/bookings',data);
        if(result.success!==true) throw new Error(result.message || 'Chưa xác nhận được đặt phòng.');
        showAlert(result.message || 'Đã đặt phòng.','success');
        stayBusy=false;await openRoomStay(stayRoom);await loadRooms();
    } catch(err) {
        // Do not repeat a write after an uncertain network result. Reload authoritative state first.
        stayReady=false;stayMessage((err.message || 'Chưa xác nhận kết quả.')+' Đóng và mở lại phòng để kiểm tra lịch trước khi đặt lại.',true);
        lockStay(false);await loadRooms();
    }
}
async function stayTransition(booking, action) {
    if(stayBusy || !stayReady) return;
    if(!await askConfirmation((action==='checkin'?'Nhận phòng cho ':'Hủy đặt phòng của ')+booking.customerName+' (BK'+booking.bookingId+')?')) return;
    if(stayBusy || !stayReady) return;
    lockStay(true);
    try {
        const res=await apiPut(action==='checkin'?'/checkin/'+booking.bookingId:'/bookings/'+booking.bookingId+'/cancel',{});
        if(res.success!==true) throw new Error(res.message || 'Chưa xác nhận kết quả.');
        showAlert(res.message,'success');stayBusy=false;await openRoomStay(stayRoom);await loadRooms();
    } catch(err) {stayReady=false;stayMessage(err.message+' Hãy đóng và mở lại để kiểm tra trạng thái.',true);lockStay(false);await loadRooms();}
}
async function previewStayCheckout(booking) {
    if(stayBusy || !stayReady) return;
    lockStay(true);stayPreview=null;stayEl('invoice').hidden=true;
    try {
        const p=await apiGet('/checkout/preview/'+booking.bookingId);
        if(p.success!==true || !validStayInvoice(p,booking.bookingId)) throw new Error('Không tải được hóa đơn hợp lệ. Hãy tải lại.');
        stayPreview=p;stayEl('invoice').hidden=false;
        stayEl('invoice-summary').textContent='BK'+p.bookingId+' · '+p.customerName+' · Tiền phòng '+formatMoney(p.roomTotal)+' + dịch vụ '+formatMoney(p.serviceTotal)+' = '+formatMoney(p.grandTotal);
        const lines=stayEl('invoice-lines');lines.replaceChildren();
        lines.appendChild(textNode('p',p.billingUnit==='Hour' ? p.billedHours+' giờ × '+formatMoney(p.pricePerHour)+'/giờ (khung giờ đã đặt, làm tròn lên).' : p.nights+' đêm × '+formatMoney(p.pricePerNight)+'/đêm.'));
        p.services.forEach(s=>lines.appendChild(textNode('p',s.serviceName+' × '+s.quantity+' · '+formatMoney(s.totalPrice))));
        stayEl('services-link').href='checkout.html?bookingId='+p.bookingId;
        stayMessage('Kiểm tra tiền phòng, dịch vụ và phương thức thanh toán trước khi xác nhận.');
    } catch(err) {stayMessage(err.message,true);}
    finally {lockStay(false);}
}
function validStayInvoice(p,id) {
    if(!p || p.bookingId!==id || !Array.isArray(p.services)) return false;
    const money=n=>Number.isSafeInteger(n) && n>=0 && n<=999999999999;
    if(![p.roomTotal,p.serviceTotal,p.grandTotal].every(money)) return false;
    let units=p.nights,rate=p.pricePerNight;
    if(p.billingUnit==='Hour') {
        if(!p.checkinAt || !p.checkoutAt) return false;
        units=p.billedHours;rate=p.pricePerHour;
        const elapsed=Date.parse(p.checkoutAt+'+07:00')-Date.parse(p.checkinAt+'+07:00');
        if(elapsed<=0 || Math.ceil(elapsed/3600000)!==units) return false;
    } else if((Date.parse(p.checkoutDate)-Date.parse(p.checkinDate))/86400000!==units) return false;
    return Number.isSafeInteger(units) && units>0 && money(rate) && p.roomTotal===units*rate
        && p.grandTotal===p.roomTotal+p.serviceTotal && p.services.every(s=>money(s.totalPrice))
        && p.services.reduce((sum,s)=>sum+s.totalPrice,0)===p.serviceTotal;
}
async function payRoomStay() {
    if(stayBusy || !stayReady || !stayPreview) return;
    const p=stayPreview,method=stayEl('payment').value;
    if(!await askConfirmation('Xác nhận đã thu '+formatMoney(p.grandTotal)+' bằng '+method+' và trả phòng BK'+p.bookingId+'?')) return;
    if(stayBusy || stayPreview!==p) return;
    lockStay(true);
    try {
        const result=await apiPost('/checkout/'+p.bookingId,{paymentMethod:method});
        if(result.success!==true) throw new Error(result.message || 'Chưa xác nhận thanh toán.');
        stayEl('invoice').hidden=true;showAlert('Đã thanh toán và trả phòng. Hóa đơn HD'+result.paymentId+' đã lưu.','success');
        stayBusy=false;await openRoomStay(stayRoom);await loadRooms();
    } catch(err) {
        stayPreview=null;stayReady=false;stayEl('invoice').hidden=true;
        stayMessage(err.message+' Kiểm tra hóa đơn đã thu tại mục Trả phòng trước khi thử lại.',true);lockStay(false);await loadRooms();
    }
}
function editStayRoom() { if(stayRoom && !stayBusy) {const room=stayRoom;closeRoomStay();openEditModal(room);} }
document.addEventListener('DOMContentLoaded',()=>{
    ['checkin','checkout'].forEach(id=>stayEl(id).addEventListener('input',updateStayEstimate));
});
