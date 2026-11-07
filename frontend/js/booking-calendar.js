// NOTE FRONTEND: Lịch phòng theo ngày/giờ, bộ lọc nhanh, chi tiết booking và form đặt theo giờ; dữ liệu thật vẫn được kiểm tra ở backend.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
// The calendar uses server-owned bookings; no business state is stored in the browser.
let calendarRooms = [], calendarMode = 'hours', calendarStart = vietnamToday();
let calendarType = '', calendarAttention = '', calendarReady = false, calendarLoading = null;
let calendarFingerprint = '', calendarDetailId = null, calendarDetailBusy = false;
let calendarTimer = null, calendarWantedRoom = null, calendarWantedDate = null;
let calendarPreferredHour=null;
const calendarDay = 86400000;
const calendarStatusLabels = {Confirmed:'Đã xác nhận',CheckedIn:'Đang ở',CheckedOut:'Đã trả phòng',Cancelled:'Đã hủy'};

// NOTE FRONTEND: Cộng/trừ ngày bằng mốc UTC của chuỗi YYYY-MM-DD để không bị lệch khi qua tháng/năm.
function calendarDateAdd(date, days) { return new Date(Date.parse(date+'T00:00:00Z')+days*calendarDay).toISOString().slice(0,10); }
// NOTE FRONTEND: Đổi chuỗi ngày thành nhãn ngắn cho tiêu đề lịch.
function calendarDateLabel(date) { return date.slice(8,10)+'/'+date.slice(5,7); }
// NOTE FRONTEND: Đổi thời điểm thành chuỗi giờ hiển thị theo múi giờ và cách dùng của lịch.
function calendarTime(value) { return Date.parse(value.length===10 ? value+'T00:00:00+07:00' : value+'+07:00'); }
// NOTE FRONTEND: Lấy hai biên thời gian thật của booking giờ hoặc ngày nhận/trả của booking đêm.
function calendarBounds(booking) {
    return [calendarTime(booking.checkinAt || booking.checkinDate),calendarTime(booking.checkoutAt || booking.checkoutDate)];
}
// NOTE FRONTEND: Chọn ngày nhận/trả để phân nhóm booking, ưu tiên thời điểm thực của lượt đặt theo giờ.
function calendarBookingDate(booking,which) { return (booking[which+'At'] || booking[which+'Date']).slice(0,10); }
// NOTE FRONTEND: Lọc booking theo bộ lọc cơ sở/phòng đang áp dụng trên lịch.
function calendarRoomScope() { return propertyRooms(calendarRooms); }
// NOTE FRONTEND: Chọn lượt đặt có sự kiện liên quan hôm nay trong phạm vi đang xem.
function calendarTodayBookings() { return bookingRows.filter(b=>b.status!=='Cancelled'); }
// NOTE FRONTEND: Kiểm tra booking thuộc nhóm cần chú ý đang chọn, như cần nhận hoặc cần trả.
function calendarAttentionMatches(b, attention) {
    const today=vietnamToday();
    if(attention==='arrivals') return b.status==='Confirmed' && calendarBookingDate(b,'checkin')===today;
    if(attention==='departures') return b.status==='CheckedIn' && calendarBookingDate(b,'checkout')<=today;
    if(attention==='occupied') return b.status==='CheckedIn';
    if(attention==='confirmed') return b.status==='Confirmed';
    return true;
}
// NOTE FRONTEND: Thay bộ lọc cần chú ý và dựng lại phần số liệu/lịch liên quan.
function setCalendarAttention(value) {
    calendarAttention=calendarAttention===value?'':value;
    if(value) {calendarStart=vietnamToday();document.getElementById('calendar-date').value=calendarStart;}
    renderCalendarSummary();renderBookingCalendar();
}
// NOTE FRONTEND: Tổng hợp các chỉ số và nhóm công việc nhanh từ tập phòng/lượt đặt hiện có.
function renderCalendarSummary() {
    const target=document.getElementById('calendar-summary');if(!target)return;
    const rows=calendarTodayBookings(),rooms=calendarRoomScope();
    const occupied=rooms.filter(r=>r.status==='Đang sử dụng').length;
    const arrivals=rows.filter(b=>calendarAttentionMatches(b,'arrivals')).length;
    const departures=rows.filter(b=>calendarAttentionMatches(b,'departures')).length;
    const overdue=rows.filter(b=>b.status==='CheckedIn' && calendarBookingDate(b,'checkout')<vietnamToday()).length;
    const upcoming=rows.filter(b=>b.status==='Confirmed').length;
    const available=rooms.filter(r=>r.status==='Trống').length;
    target.replaceChildren();
    for(const [key,label,value,help,icon] of [
        ['occupied','Đang ở',occupied,'phòng có khách','bed'],
        ['arrivals','Đến hôm nay',arrivals,'lượt chờ nhận phòng','arrow-right'],
        ['departures','Cần trả phòng',departures,overdue?overdue+' lượt đã quá ngày trả':'hôm nay và quá hạn','print'],
        ['confirmed','Đã xác nhận',upcoming,'lượt đặt chưa nhận phòng','calendar'],
        ['available','Còn trống',available+'/'+rooms.length,'phòng sẵn sàng hiện tại','house']
    ]) {
        const card=actionButton('','summary-card'+(calendarAttention===key?' selected':''),()=>setCalendarAttention(key));
        card.disabled=!calendarReady;card.setAttribute('aria-pressed',String(calendarAttention===key));
        const top=textNode('span',label,'summary-label');
        card.append(top,textNode('strong',calendarReady?value:'—','summary-value'),textNode('span',help,'summary-help'));
        target.append(card);
    }
}
// NOTE FRONTEND: Lọc phòng theo loại, từ khóa và điều kiện đang chọn trước khi dựng hàng lịch.
function calendarVisibleRooms() {
    const query=document.getElementById('booking-search').value.trim().toLocaleLowerCase('vi');
    const status=document.getElementById('booking-status-filter').value;
    const start=calendarTime(calendarStart),end=start+(calendarMode==='days'?10:1)*calendarDay;
    return calendarRoomScope().filter(room=>{
        if(calendarType && room.roomType!==calendarType)return false;
        if(calendarAttention==='available' && room.status!=='Trống')return false;
        const related=bookingRows.filter(b=>{
            if(b.roomId!==room.roomId)return false;
            const [a,z]=calendarBounds(b);
            return a<end && (z>start || b.status==='CheckedIn') && (status?b.status===status:b.status!=='Cancelled');
        });
        if(calendarAttention && calendarAttention!=='available' && !related.some(b=>calendarAttentionMatches(b,calendarAttention)))return false;
        if(status && !related.some(b=>b.status===status))return false;
        return !query || [room.roomCode,room.roomName,room.roomType,...related.flatMap(b=>[b.customerName,'BK'+b.bookingId,b.phone])]
            .some(value=>String(value || '').toLocaleLowerCase('vi').includes(query));
    }).sort((a,b)=>a.roomCode.localeCompare(b.roomCode,'vi',{numeric:true}));
}
// NOTE FRONTEND: Tạo các lựa chọn loại phòng từ danh mục thật và giữ lựa chọn còn tồn tại.
function renderCalendarTypes() {
    const target=document.getElementById('calendar-types');target.replaceChildren();
    for(const type of ['',...new Set(calendarRooms.map(r=>r.roomType))]) {
        const button=actionButton(type || 'Tất cả','filter-pill'+(calendarType===type?' active':''),()=>{calendarType=type;renderCalendarTypes();renderBookingCalendar();});
        button.setAttribute('aria-pressed',String(calendarType===type));target.append(button);
    }
}
// NOTE FRONTEND: Chuyển lịch ngày/giờ rồi tính lại phạm vi và hình thức hiển thị.
function setCalendarMode(mode) {
    calendarMode=mode;
    for(const [id,value] of [['view-days','days'],['view-hours','hours']]) {
        const button=document.getElementById(id);button.classList.toggle('active',value===mode);button.setAttribute('aria-pressed',String(value===mode));
    }
    renderBookingCalendar();scrollCalendarToNow();
}
// NOTE FRONTEND: Dịch khoảng thời gian đang xem về trước/sau hoặc trở lại hiện tại.
function moveCalendar(direction) {
    calendarPreferredHour=null;
    calendarStart=direction?calendarDateAdd(calendarStart,direction*(calendarMode==='days'?7:1)):vietnamToday();
    document.getElementById('calendar-date').value=calendarStart;renderBookingCalendar();scrollCalendarToNow();
}
// NOTE FRONTEND: Bật/tắt vùng lịch mở rộng để có thêm không gian xem, không đổi dữ liệu booking.
function toggleCalendarExpand(){
    const expanded=document.body.classList.toggle('calendar-expanded');
    const button=document.getElementById('calendar-expand');button.textContent=expanded?'Thu gọn lịch':'Mở rộng lịch';button.setAttribute('aria-pressed',String(expanded));
    if(expanded){const sidebar=document.getElementById('app-sidebar');sidebar.inert=true;sidebar.setAttribute('aria-hidden','true');}
    else if(typeof setShellMenu==='function')setShellMenu(false,false);
}
// NOTE FRONTEND: Tạo nhãn và nội dung nhận biết booking để đặt lên khối thời gian.
function calendarBookingLabel(b) {
    if(b.billingUnit==='Hour')return (b.checkinAt || '').slice(11,16)+'–'+(b.checkoutAt || '').slice(11,16)+' · Theo giờ';
    const nights=Math.round((calendarTime(b.checkoutDate)-calendarTime(b.checkinDate))/calendarDay);
    return nights+' đêm · '+calendarStatusLabels[b.status];
}
// NOTE FRONTEND: Dựng hàng phòng, cột ngày/giờ và thanh booking theo biên thời gian; nối mở chi tiết và tạo lượt đặt.
function renderBookingCalendar() {
    const target=document.getElementById('booking-calendar');if(!target)return;
    const scroll=document.getElementById('calendar-scroll'),savedX=scroll.scrollLeft,savedY=scroll.scrollTop;
    // Background refresh preserves focus, so keyboard users can continue opening bookings.
    const focusedId=document.activeElement?.dataset?.calendarBooking;
    const count=calendarMode==='days'?10:24,slot=calendarMode==='days'?calendarDay:3600000;
    const start=calendarTime(calendarStart),end=start+count*slot;
    const today=vietnamToday(),rooms=calendarVisibleRooms();
    const status=document.getElementById('booking-status-filter').value;
    const attention=document.getElementById('calendar-attention');attention.hidden=!calendarAttention;
    document.getElementById('calendar-attention-label').textContent={occupied:'Đang xem phòng có khách lưu trú',arrivals:'Đang xem khách đến hôm nay',departures:'Đang xem phòng cần trả hôm nay hoặc quá hạn',confirmed:'Đang xem phòng có đặt đã xác nhận',available:'Đang xem phòng hiện đang trống'}[calendarAttention] || '';
    document.getElementById('calendar-range').textContent=calendarDateLabel(calendarStart)+(calendarMode==='days'?' – '+calendarDateLabel(calendarDateAdd(calendarStart,count-1)):' · 00:00–24:00')+' · '+rooms.length+'/'+calendarRooms.length+' phòng';
    target.style.setProperty('--calendar-columns',count);target.classList.toggle('hour-mode',calendarMode==='hours');target.replaceChildren();
    const head=textNode('div','','calendar-grid-row calendar-grid-head');
    head.append(textNode('div','PHÒNG','calendar-room-label calendar-room-head'));
    const dates=textNode('div','','calendar-slots');
    for(let i=0;i<count;i++) {
        const date=calendarDateAdd(calendarStart,calendarMode==='days'?i:0),isToday=date===today;
        const cell=textNode('div','','calendar-slot-head'+(isToday?' today':'')+([0,6].includes(new Date(date+'T00:00Z').getUTCDay())?' weekend':''));
        if(calendarMode==='days') {
            cell.append(textNode('span',['CN','T2','T3','T4','T5','T6','T7'][new Date(date+'T00:00Z').getUTCDay()]),textNode('strong',date.slice(8,10)),textNode('small',isToday?'Hôm nay':date.slice(8)==='01'?'Tháng '+Number(date.slice(5,7)):''));
        } else cell.append(textNode('strong',String(i).padStart(2,'0')+':00'));
        dates.append(cell);
    }
    head.append(dates);target.append(head);
    if(!calendarReady)target.append(textNode('p','Đang tải lịch phòng…','calendar-empty'));
    else if(!rooms.length)target.append(textNode('p','Không có phòng phù hợp. Thử bỏ bộ lọc hoặc đổi từ khóa.','calendar-empty'));
    for(const room of rooms) {
        const row=textNode('div','','calendar-grid-row');
        const roomLabel=actionButton('','calendar-room-label',()=>openCalendarRoom(room));
        roomLabel.append(textNode('strong',room.roomName),textNode('span',room.roomCode+' · '+room.roomType,'calendar-room-meta'));
        roomLabel.append(textNode('span',room.hourlyPackages?formatMoney(room.hourlyPackages.twoHours)+'/2 giờ':Number(room.hourlyPrice)>0?formatMoney(room.hourlyPrice)+'/giờ':'Chưa thiết lập giá giờ','calendar-room-rate'+(Number(room.hourlyPrice)>0?'':' missing')));
        roomLabel.append(textNode('span',room.status==='Đang sử dụng'?'Đang ở':room.status==='Trống'?'Còn trống':room.status,'room-state '+(room.status==='Trống'?'free':room.status==='Bảo trì'?'maintenance':'')));
        row.append(roomLabel);
        const track=textNode('div','','calendar-track');
        const background=textNode('div','','calendar-slots calendar-cell-grid');
        for(let i=0;i<count;i++) {
            const date=calendarDateAdd(calendarStart,calendarMode==='days'?i:0);
            const cell=actionButton('','calendar-cell'+(date===today?' today':''),()=>startCalendarBooking(room,date,calendarMode==='hours'?i:null));
            cell.setAttribute('aria-label','Đặt '+room.roomCode+' · '+calendarDateLabel(date)+(calendarMode==='hours'?' '+i+':00':''));
            cell.disabled=!calendarReady || room.status==='Bảo trì' || date<today;
            background.append(cell);
        }
        track.append(background);
        const list=bookingRows.filter(b=>{
            if(b.roomId!==room.roomId || (status?b.status!==status:b.status==='Cancelled'))return false;
            if(calendarAttention && !['available','confirmed'].includes(calendarAttention) && !calendarAttentionMatches(b,calendarAttention))return false;
            const [a,z]=calendarBounds(b);
            return a<end && (z>start || b.status==='CheckedIn');
        }).sort((a,b)=>calendarBounds(a)[0]-calendarBounds(b)[0]);
        const laneEnds=[];
        for(const b of list) {
            let [a,z]=calendarBounds(b);
            const overdue=b.status==='CheckedIn' && z<calendarTime(today);
            if(b.status==='CheckedIn')z=Math.max(z,calendarTime(today)+calendarDay);
            const left=Math.max(0,(a-start)/slot),right=Math.min(count,(z-start)/slot);
            if(right<=left)continue;
            const displayRight=Math.min(count,Math.max(right,left+(calendarMode==='days'?.65:.85)));
            let lane=laneEnds.findIndex(value=>value<=left);if(lane<0)lane=laneEnds.length;laneEnds[lane]=displayRight;
            const button=actionButton('','calendar-reservation state-'+b.status+(overdue?' overdue':''),()=>openBookingDetail(b.bookingId));
            button.dataset.calendarBooking=String(b.bookingId);
            button.style.left='calc('+left/count*100+'% + 4px)';button.style.width='calc('+(displayRight-left)/count*100+'% - 8px)';button.style.top=(10+lane*53)+'px';
            button.append(textNode('strong',b.customerName),textNode('span',overdue?'Quá ngày trả · Cần kiểm tra':calendarBookingLabel(b)));
            button.setAttribute('aria-label','BK'+b.bookingId+' · '+b.customerName+' · '+room.roomCode+' · '+bookingTime(b,'checkin')+' đến '+bookingTime(b,'checkout')+' · '+calendarStatusLabels[b.status]);
            button.title=button.getAttribute('aria-label');track.append(button);
        }
        if(room.status==='Bảo trì') {
            const maintenance=textNode('div','Bảo trì · Chưa mở nhận đặt','calendar-maintenance');track.append(maintenance);
        }
        track.style.minHeight=Math.max(94,laneEnds.length*53+20)+'px';row.append(track);target.append(row);
    }
    scroll.scrollLeft=savedX;scroll.scrollTop=savedY;
    if(focusedId)target.querySelector('[data-calendar-booking="'+focusedId+'"]')?.focus({preventScroll:true});
}
// NOTE FRONTEND: Tải phòng/booking, chống dùng phản hồi cũ và cập nhật đồng bộ lịch, bảng cùng trạng thái đang tải.
async function loadCalendarData(force=false) {
    if(calendarLoading) {await calendarLoading;if(force)return loadCalendarData();return;}
    const task=(async()=>{
        const sync=document.getElementById('calendar-sync');
        try {
            const [rooms,bookings]=await Promise.all([apiGet('/rooms'),apiGet('/bookings')]);
            const nextRooms=expectList(rooms),nextBookings=expectList(bookings);
            const fingerprint=JSON.stringify([vietnamToday(),nextRooms,nextBookings]);
            calendarRooms=nextRooms;bookingRows=nextBookings;calendarReady=true;bookingTableReady=true;
            const error=document.getElementById('calendar-error');error.hidden=true;
            sync.className='sync-status';sync.textContent='Đã đồng bộ · '+vietnamNow().slice(11,19);
            document.getElementById('calendar-export').disabled=false;
            if(fingerprint!==calendarFingerprint) {
                calendarFingerprint=fingerprint;renderCalendarTypes();renderCalendarSummary();filterBookings();
                if(calendarDetailId && !calendarDetailBusy)renderBookingDetail();
            }
        } catch(err) {
            sync.className='sync-status offline';sync.textContent='Chưa đồng bộ · Thử lại';
            const error=document.getElementById('calendar-error');error.hidden=false;
            error.textContent=(calendarReady?'Đang hiển thị dữ liệu lần đồng bộ trước. ':'')+(err.message || 'Không kết nối được máy chủ.')+' Lịch sẽ tự thử lại.';
            document.getElementById('calendar-export').disabled=true;
        }
    })();
    calendarLoading=task;
    try {await task;} finally {if(calendarLoading===task)calendarLoading=null;}
}
// NOTE FRONTEND: Chuẩn bị bộ lọc, nút chuyển lịch, phạm vi ban đầu và các sự kiện cho trang đặt phòng.
function initializeBookingCalendar() {
    const query=new URLSearchParams(window.location.search),date=query.get('date'),hour=query.get('hour'),type=query.get('type');
    const requestedRoom=query.get('room');
    if(requestedRoom && requestedRoom.length<=20)document.getElementById('booking-search').value=requestedRoom;
    if(date && /^\d{4}-\d{2}-\d{2}$/.test(date) && Number.isFinite(Date.parse(date+'T00:00Z')) && new Date(Date.parse(date+'T00:00Z')).toISOString().slice(0,10)===date)calendarStart=date;
    if(hour!==null && /^\d{1,2}$/.test(hour) && Number(hour)>=0 && Number(hour)<=23)calendarPreferredHour=Number(hour);
    if(['Single','Double','VIP','Family'].includes(type))calendarType=type;
    document.getElementById('calendar-date').value=calendarStart;
    document.getElementById('calendar-date').addEventListener('change',event=>{
        if(/^\d{4}-\d{2}-\d{2}$/.test(event.target.value)){calendarPreferredHour=null;calendarStart=event.target.value;renderBookingCalendar();scrollCalendarToNow();}
    });
    renderCalendarSummary();renderBookingCalendar();
    loadCalendarData().then(()=>scrollCalendarToNow());
    startCalendarPolling();
    document.addEventListener('visibilitychange',()=>{if(!document.hidden)loadCalendarData();});
    window.addEventListener('online',()=>loadCalendarData());
    window.addEventListener('offline',()=>{const sync=document.getElementById('calendar-sync');sync.className='sync-status offline';sync.textContent='Mất kết nối';});
    window.addEventListener('pagehide',()=>clearInterval(calendarTimer));
    window.addEventListener('pageshow',event=>{if(event.persisted){startCalendarPolling();loadCalendarData();}});
}
// NOTE FRONTEND: Cuộn lịch đến mốc thời gian hiện tại thay vì yêu cầu người dùng tìm thủ công.
function scrollCalendarToNow(){
    if(calendarMode!=='hours' || (calendarStart!==vietnamToday() && calendarPreferredHour===null))return;
    const scroll=document.getElementById('calendar-scroll');
    const hours=Math.max(0,(calendarPreferredHour===null?Number(vietnamNow().slice(11,13)):calendarPreferredHour)-2);
    const track=document.querySelector('.calendar-track');
    if(track)scroll.scrollLeft=hours*track.getBoundingClientRect().width/24;
}
// NOTE FRONTEND: Lên lịch tải lại dữ liệu khi trang có thể cập nhật để lịch theo kịp thay đổi từ người khác.
function startCalendarPolling(){
    clearInterval(calendarTimer);
    calendarTimer=setInterval(()=>{if(!document.hidden && !bookingSubmitting && !calendarDetailBusy)loadCalendarData();},5000);
}
// NOTE FRONTEND: Đi từ hàng lịch sang phòng tương ứng để xem hoặc thao tác lưu trú.
async function openCalendarRoom(room) {
    const relevant=bookingRows.filter(b=>b.roomId===room.roomId && ['Confirmed','CheckedIn'].includes(b.status))
        .sort((a,b)=>(a.status==='CheckedIn'?-1:b.status==='CheckedIn'?1:calendarBounds(a)[0]-calendarBounds(b)[0]));
    if(relevant.length)openBookingDetail(relevant[0].bookingId);
    else startCalendarBooking(room,calendarStart);
}
// NOTE FRONTEND: Mở luồng đặt phòng với phòng/ngày/giờ đã chọn trên một ô lịch.
async function startCalendarBooking(room,date,hour=null) {
    if(room.status==='Bảo trì'){showAlert('Phòng đang bảo trì, chưa thể nhận đặt.','error');return;}
    if(date<vietnamToday()){showAlert('Chọn ngày hiện tại hoặc tương lai để đặt phòng.','error');return;}
    if(calendarMode==='hours') {await openHourlyBooking(room,date,hour);return;}
    calendarWantedRoom=room.roomId;calendarWantedDate=date;
    await openAddModal();
    if(!bookingModalOpen)return;
    if(date!==vietnamToday()) {
        document.getElementById('booking-checkin-date').value=date;
        document.getElementById('booking-checkout-date').value=calendarDateAdd(date,1);
        await refreshAvailableRooms(true);
    }
    if(bookingRooms.has(room.roomId)){document.getElementById('booking-room').value=String(room.roomId);calculateEstimate();updateBookingControls();}
    else document.getElementById('booking-room-status').textContent='Phòng '+room.roomCode+' không còn trống cho toàn bộ khoảng ngày này. Chọn khoảng khác hoặc phòng khác.';
}
// NOTE FRONTEND: Đóng chi tiết và xóa ID đang xem để dữ liệu cũ không giữ trạng thái chọn.
function closeBookingDetail() {
    if(calendarDetailBusy)return;
    calendarDetailId=null;document.getElementById('booking-detail-modal').classList.remove('show');
}
// NOTE FRONTEND: Lưu bookingId, dựng nội dung và mở hộp chi tiết.
function openBookingDetail(id) {calendarDetailId=id;renderBookingDetail();openModal('booking-detail-modal');}
// NOTE FRONTEND: Đọc booking đang chọn, liệt kê lịch liên quan và dựng các thao tác đúng trạng thái hiện tại.
function renderBookingDetail() {
    const b=bookingRows.find(row=>row.bookingId===calendarDetailId);
    const content=document.getElementById('booking-detail-content'),actions=document.getElementById('booking-detail-actions');
    content.replaceChildren();actions.replaceChildren();document.getElementById('detail-message').textContent='';
    if(!b){content.append(textNode('p','Lượt đặt không còn trong dữ liệu. Vui lòng làm mới lịch.'));return;}
    document.getElementById('detail-code').textContent='BK'+b.bookingId+' · '+calendarStatusLabels[b.status];
    document.getElementById('detail-name').textContent=b.customerName;
    content.append(textNode('p',(b.phone || 'Chưa có số điện thoại')+' · '+(b.billingUnit==='Hour'?'Đặt theo giờ':'Đặt theo đêm'),'detail-subtitle'));
    const info=textNode('dl','','detail-info');
    for(const [key,value] of [['Nhận phòng',bookingTime(b,'checkin')],['Trả phòng',bookingTime(b,'checkout')],['Phòng',b.roomCode+' · '+b.roomName],['Loại phòng',b.roomType]]) {
        const item=textNode('div');item.append(textNode('dt',key),textNode('dd',value));info.append(item);
    }
    content.append(info);
    const [a,z]=calendarBounds(b),hours=b.billingUnit==='Hour',units=hours?Math.ceil((z-a)/3600000):Math.round((z-a)/calendarDay);
    const rate=Number(hours?b.pricePerHour:b.price),quote=hours?quoteHourly(rate,b.hourlyPackages,units):quoteNightly(rate,b.nightlyPricing,b.checkinDate,b.checkoutDate),total=quote.total;
    const cost=textNode('section','','detail-cost');
    cost.append(textNode('h4','Tiền phòng theo giá đã chốt'),textNode('strong',Number.isSafeInteger(total)?formatMoney(total):'Chưa xác định'),textNode('p',hours?hourlyChargeLabel(quote,units):nightlyChargeLabel(quote,b.nightlyPricing),'text-muted'));
    cost.append(textNode('p',b.status==='CheckedOut'?'Đã trả phòng. Xem hóa đơn để đối chiếu số tiền đã thu.':'Chưa gồm dịch vụ. Tổng thanh toán được tính khi trả phòng.','form-help'));
    content.append(cost);
    if(b.note){content.append(textNode('h4','Ghi chú'),textNode('p',b.note,'detail-note'));}
    const related=bookingRows.filter(row=>row.roomId===b.roomId && row.bookingId!==b.bookingId && ['Confirmed','CheckedIn'].includes(row.status))
        .sort((x,y)=>calendarBounds(x)[0]-calendarBounds(y)[0]);
    if(related.length){
        const section=textNode('section','','detail-related');section.append(textNode('h4','Các lượt đặt khác của phòng'));
        for(const next of related)section.append(actionButton('BK'+next.bookingId+' · '+next.customerName+' · '+bookingTime(next,'checkin'),'related-booking',()=>openBookingDetail(next.bookingId)));
        content.append(section);
    }
    if(b.status==='Confirmed') {
        const check=actionButton('Nhận phòng','btn btn-primary',()=>calendarCheckIn(b.bookingId));
        const now=calendarTime(vietnamNow());check.disabled=now<a || now>=z;
        if(check.disabled)content.append(textNode('p',now<a?'Chưa đến thời gian nhận phòng.':'Lượt đặt đã quá thời gian nhận. Kiểm tra lịch với khách trước khi xử lý.','detail-notice'));
        actions.append(check,actionButton('Hủy đặt phòng','btn btn-danger',async()=>{await cancelBooking(b.bookingId);if(calendarDetailId)renderBookingDetail();}));
    }
    if(b.status==='CheckedIn') {
        const link=textNode('a','Dịch vụ & thanh toán trả phòng','btn btn-primary');link.href='checkout.html?bookingId='+b.bookingId;actions.append(link);
    }
    if(b.status==='CheckedOut')actions.append(actionButton('Xem hóa đơn đã thu','btn btn-primary',async()=>{
        try {const rows=expectList(await apiGet('/payments?bookingId='+b.bookingId));if(!rows.length)throw new Error('Chưa tìm thấy hóa đơn đã thu.');window.location.href='checkout.html?paymentId='+rows[0].paymentId;}
        catch(err){document.getElementById('detail-message').textContent=err.message;}
    }));
}
// NOTE FRONTEND: Gửi yêu cầu nhận phòng cho booking trên lịch sau kiểm tra/xác nhận, rồi làm mới trạng thái server.
async function calendarCheckIn(id) {
    if(calendarDetailBusy || !await askConfirmation('Xác nhận khách của BK'+id+' đã đến và nhận phòng?'))return;
    calendarDetailBusy=true;
    document.querySelectorAll('#booking-detail-modal button').forEach(button=>button.disabled=true);
    try {
        const result=await apiPut('/checkin/'+id);if(result.success!==true)throw new Error(result.message || 'Chưa xác nhận nhận phòng.');
        await loadCalendarData(true);showAlert('Đã nhận phòng BK'+id+'.');
    }catch(err){showRequestError(err,'Không thể nhận phòng.');await loadCalendarData(true);}
    finally{calendarDetailBusy=false;document.querySelectorAll('#booking-detail-modal [data-modal-close]').forEach(button=>button.disabled=false);renderBookingDetail();}
}
// NOTE FRONTEND: Xuất CSV các lượt đặt thuộc phòng và khoảng thời gian đang xem; không xuất trong trạng thái chưa sẵn sàng.
function exportCalendar() {
    const visible=new Set(calendarVisibleRooms().map(room=>room.roomId));
    const start=calendarTime(calendarStart),end=start+(calendarMode==='days'?10:1)*calendarDay;
    const status=document.getElementById('booking-status-filter').value;
    const rows=bookingRows.filter(b=>{
        const [a,z]=calendarBounds(b);
        return visible.has(b.roomId) && (status?b.status===status:b.status!=='Cancelled') && a<end && (z>start || b.status==='CheckedIn') &&
            (!calendarAttention || calendarAttention==='available' || calendarAttentionMatches(b,calendarAttention));
    });
    downloadCsv('lich-dat-phong-'+vietnamToday()+'.csv',[
        {label:'Mã đặt',value:b=>'BK'+b.bookingId},{label:'Phòng',value:b=>b.roomCode,text:true},
        {label:'Khách hàng',value:b=>b.customerName,text:true},{label:'Nhận phòng',value:b=>b.checkinAt || b.checkinDate},
        {label:'Trả phòng',value:b=>b.checkoutAt || b.checkoutDate},{label:'Trạng thái',value:b=>calendarStatusLabels[b.status],text:true}
    ],rows,calendarReady && !document.getElementById('calendar-export').disabled);
}

let hourlyBookingOpen=false,hourlyBookingBusy=false,hourlyBookingReady=false,hourlyBookingVersion=0;
let hourlyCustomers=[],hourlyRooms=[];
// NOTE FRONTEND: Đóng form đặt giờ khi không có yêu cầu ghi đang chạy.
function closeHourlyBooking(){
    if(hourlyBookingBusy)return;
    hourlyBookingOpen=false;hourlyBookingVersion++;document.getElementById('hourly-booking-modal').classList.remove('show');
}
// NOTE FRONTEND: Tải khách/phòng cho form giờ và điền thời gian từ lựa chọn trên lịch.
async function openHourlyBooking(room=null,date=vietnamToday(),hour=null){
    hourlyBookingOpen=true;hourlyBookingReady=false;const version=++hourlyBookingVersion;
    const now=vietnamNow().slice(0,16);
    const start=hour!==null?date+'T'+String(hour).padStart(2,'0')+':00':date===vietnamToday()?now:date+'T14:00';
    document.getElementById('hourly-start').value=start;document.getElementById('hourly-start').min=now;
    document.getElementById('hourly-end').value=new Date(calendarTime(start)+9*3600000).toISOString().slice(0,16); // +2 hours in Vietnam.
    document.getElementById('hourly-note').value='';
    document.getElementById('hourly-customer').replaceChildren(selectOption('','Đang tải khách hàng…'));
    document.getElementById('hourly-room').replaceChildren(selectOption('','Đang tải phòng…'));
    document.getElementById('hourly-status').textContent='Đang tải dữ liệu…';document.getElementById('hourly-save').disabled=true;
    openModal('hourly-booking-modal');
    try{
        const [rooms,customers]=await Promise.all([apiGet('/rooms'),apiGet('/customers')]);
        if(!hourlyBookingOpen || version!==hourlyBookingVersion)return;
        hourlyRooms=expectList(rooms).filter(r=>r.status!=='Bảo trì' && Number(r.hourlyPrice)>0);
        hourlyCustomers=expectList(customers);
        const select=document.getElementById('hourly-room');select.replaceChildren(selectOption('','Chọn phòng có giá giờ'));
        hourlyRooms.forEach(r=>select.append(selectOption(r.roomId,r.roomCode+' · '+r.roomName+' · '+hourlyPriceLabel(r))));
        const customer=document.getElementById('hourly-customer');customer.replaceChildren(selectOption('','Chọn khách hàng'));
        hourlyCustomers.forEach(c=>customer.append(selectOption(c.customerId,c.fullName+' · '+c.phone)));
        if(room && hourlyRooms.some(r=>r.roomId===room.roomId))select.value=String(room.roomId);
        hourlyBookingReady=true;document.getElementById('hourly-status').textContent=hourlyRooms.length?'Giờ Việt Nam · Làm tròn lên mỗi giờ. Phòng có giá gói: tối thiểu 2 giờ, tự chọn gói có tổng tiền thấp nhất. Máy chủ kiểm tra trùng lịch khi xác nhận.':'Chưa có phòng đang hoạt động được khai báo giá giờ. Quản trị viên có thể nhập giá tại Quản lý phòng.';
        updateHourlyEstimate();
    }catch(err){if(version===hourlyBookingVersion)document.getElementById('hourly-status').textContent=err.message+' Đóng và mở lại để thử.';}
}
// NOTE FRONTEND: Kiểm tra giờ Việt Nam, phòng, thời lượng, lịch chồng và lấy báo giá gói giờ để chuẩn bị dữ liệu gửi.
function hourlyBookingInput(){
    if(!hourlyBookingReady)throw new Error('Chưa tải xong dữ liệu.');
    const room=hourlyRooms.find(r=>r.roomId===Number(document.getElementById('hourly-room').value));
    const customerId=Number(document.getElementById('hourly-customer').value);
    if(!room)throw new Error('Chọn phòng có giá giờ.');
    const start=document.getElementById('hourly-start').value,end=document.getElementById('hourly-end').value;
    if(!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(start) || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(end))throw new Error('Chọn đủ ngày và giờ nhận, trả phòng.');
    const a=calendarTime(start),z=calendarTime(end);
    if(start<vietnamNow().slice(0,16) || z<=a)throw new Error('Giờ nhận không trong quá khứ, giờ trả phải sau giờ nhận.');
    const conflict=bookingRows.some(b=>b.roomId===room.roomId && ['Confirmed','CheckedIn'].includes(b.status) && calendarBounds(b)[0]<z && calendarBounds(b)[1]>a);
    if(conflict)throw new Error('Phòng đã có lượt đặt trong khung giờ này. Chọn khung giờ hoặc phòng khác.');
    const hours=Math.ceil((z-a)/3600000),quote=quoteHourly(room.hourlyPrice,room.hourlyPackages,hours),total=quote.total;
    return {room,customerId,start,end,hours,total};
}
// NOTE FRONTEND: Hiển thị báo giá theo dữ liệu giờ/gói hiện tại hoặc báo trường chưa hợp lệ.
function updateHourlyEstimate(){
    const estimate=document.getElementById('hourly-estimate'),save=document.getElementById('hourly-save');save.disabled=true;
    try{
        const value=hourlyBookingInput();estimate.textContent=hourlyChargeLabel(quoteHourly(value.room.hourlyPrice,value.room.hourlyPackages,value.hours),value.hours)+' = '+formatMoney(value.total)+' · Chưa gồm dịch vụ';
        save.disabled=hourlyBookingBusy || !hourlyCustomers.some(c=>c.customerId===value.customerId);
    }catch(err){estimate.textContent=err.message;}
}
// NOTE FRONTEND: Khóa form và gửi booking billingUnit=Hour; sau kết quả làm mới lịch, không tự coi báo giá client là giá đã chốt.
async function saveHourlyBooking(){
    if(hourlyBookingBusy || !hourlyBookingOpen)return;
    let payload;
    try{
        const value=hourlyBookingInput();
        if(!hourlyCustomers.some(c=>c.customerId===value.customerId))throw new Error('Chọn khách hàng.');
        payload={billingUnit:'Hour',roomId:value.room.roomId,customerId:value.customerId,checkinAt:value.start,checkoutAt:value.end,note:validText(document.getElementById('hourly-note').value,'Ghi chú',500,false)};
    }catch(err){document.getElementById('hourly-status').textContent=err.message;return;}
    hourlyBookingBusy=true;document.querySelectorAll('#hourly-booking-modal input,#hourly-booking-modal select,#hourly-booking-modal button,#hourly-booking-modal textarea').forEach(el=>el.disabled=true);
    document.getElementById('hourly-status').textContent='Đang xác nhận đặt phòng…';
    try{
        const result=await apiPost('/bookings',payload);if(result.success!==true)throw new Error(result.message || 'Chưa xác nhận được kết quả.');
        hourlyBookingBusy=false;closeHourlyBooking();await loadCalendarData(true);showAlert('Đã đặt phòng theo giờ BK'+result.bookingId+'.');
    }catch(err){
        hourlyBookingReady=false;document.getElementById('hourly-status').textContent=err.message+' Đóng và kiểm tra lịch trước khi đặt lại.';await loadCalendarData();
    }finally{
        hourlyBookingBusy=false;document.querySelectorAll('#hourly-booking-modal input,#hourly-booking-modal select,#hourly-booking-modal button,#hourly-booking-modal textarea').forEach(el=>el.disabled=false);updateHourlyEstimate();
    }
}
document.addEventListener('DOMContentLoaded',()=>{
    for(const id of ['hourly-start','hourly-end','hourly-room','hourly-customer'])document.getElementById(id).addEventListener('input',updateHourlyEstimate);
    document.addEventListener('keydown',event=>{if(event.key==='Escape' && document.body.classList.contains('calendar-expanded') && !document.querySelector('.modal-overlay.show'))toggleCalendarExpand();});
});
