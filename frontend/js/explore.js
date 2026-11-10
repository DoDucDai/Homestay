// NOTE FRONTEND: Danh mục phòng cho người xem công khai; giá niêm yết không đồng nghĩa phòng còn trống cho ngày đang muốn đặt.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
let exploreRooms=[];
// NOTE FRONTEND: Tạo ảnh/khung ảnh phòng từ thư viện chung, có ảnh thay thế khi mã chưa có nguồn ảnh.
function explorePhoto(room, gallery=false) {
    const illustration=RoomPhotos.create(room,{gallery});
    if(illustration)return illustration;
    const photo=textNode('div','','explore-photo');
    const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('aria-hidden','true');
    const use=document.createElementNS('http://www.w3.org/2000/svg','use');use.setAttribute('href','../css/icons.svg#bed');svg.append(use);
    photo.append(svg,textNode('strong',room.roomCode),textNode('small','Hình ảnh phòng đang được cập nhật'));
    return photo;
}
// NOTE FRONTEND: Tạo đường dẫn chi tiết bằng ID phòng để trang tải đúng dữ liệu công khai.
function exploreLink(room) {return 'explore.html?room='+encodeURIComponent(room.roomId);}
// NOTE FRONTEND: Định dạng tiền niêm yết để hiển thị, không quyết định giá thanh toán.
function exploreMoney(amount) { return Number(amount).toLocaleString('vi-VN')+' ₫'; }
// NOTE FRONTEND: Tạo dòng mô tả một mức giá cho phần trình bày chi tiết.
function exploreRate(label,amount,unit,description='',tone='') {
    const card=textNode('div','','explore-rate-card'+(tone?' '+tone:''));
    const value=textNode('dd','','explore-rate-value');
    value.append(textNode('strong',exploreMoney(amount)),textNode('span',unit,'explore-rate-unit'));
    if(description)value.append(textNode('small',description,'explore-rate-caption'));
    card.append(textNode('dt',label,'explore-rate-label'),value);return card;
}
// NOTE FRONTEND: Diễn giải các ngày áp dụng giá cuối tuần theo cấu hình phòng.
function exploreWeekendLabel(days) {
    const names=['','Thứ Hai','Thứ Ba','Thứ Tư','Thứ Năm','Thứ Sáu','Thứ Bảy','Chủ nhật'];
    return days.map(day=>names[day]).join(' & ');
}
// NOTE FRONTEND: Dựng các dòng giá đêm, cuối tuần, giờ/gói hiện có; không tự suy ra giá còn thiếu.
function renderExploreRates(room) {
    const night=document.getElementById('explore-night-price');night.replaceChildren();
    if(room.nightlyPricing) {
        const days=room.nightlyPricing.weekendDays;
        night.append(exploreRate('Ngày thường',room.price,'/ đêm',days.join(',')==='6,7'?'Thứ Hai – thứ Sáu':'Các đêm còn lại','explore-rate-weekday'),
            exploreRate('Cuối tuần',room.nightlyPricing.weekendPrice,'/ đêm',exploreWeekendLabel(days),'explore-rate-weekend'));
    } else night.append(exploreRate('Giá qua đêm',room.price,'/ đêm','','explore-rate-weekday'));
    const hour=document.getElementById('explore-hour-price'),extra=document.getElementById('explore-extra-hour'),rule=document.getElementById('explore-hour-rule');
    hour.replaceChildren();extra.hidden=true;rule.hidden=true;
    if(room.hourlyPackages) {
        for(const [hours,key] of [[2,'twoHours'],[4,'fourHours'],[8,'eightHours']])hour.append(exploreRate(hours+' giờ',room.hourlyPackages[key],'/ gói'));
        extra.replaceChildren(textNode('span','Giờ lẻ ngoài gói'),textNode('strong',exploreMoney(room.hourlyPrice)+' / giờ'));extra.hidden=false;
        rule.textContent='Tối thiểu gói 2 giờ · Làm tròn lên từng giờ. Tự áp dụng gói có tổng tiền thấp nhất.';rule.hidden=false;
    } else if(Number(room.hourlyPrice)>0)hour.append(exploreRate('Giá theo giờ',room.hourlyPrice,'/ giờ'));
    else hour.textContent='Chưa có giá theo giờ';
}
// NOTE FRONTEND: Tạo phần giá rút gọn trên thẻ phòng từ dữ liệu danh mục.
function exploreCardPricing(room) {
    const prices=textNode('div','','explore-card-pricing');
    for(const [label,amount] of room.nightlyPricing?[['Đêm ngày thường',room.price],['Đêm cuối tuần',room.nightlyPricing.weekendPrice]]:[['Qua đêm',room.price]]) {
        const row=textNode('p','','explore-card-rate');row.append(textNode('span',label),textNode('strong',exploreMoney(amount)));prices.append(row);
    }
    const hour=room.hourlyPackages?exploreMoney(room.hourlyPackages.twoHours)+' / 2 giờ':Number(room.hourlyPrice)>0?exploreMoney(room.hourlyPrice)+' / giờ':'Chưa có giá giờ';
    prices.append(textNode('p','Theo giờ · '+hour,'explore-card-hour'));return prices;
}
// NOTE FRONTEND: Kết hợp tìm kiếm, cơ sở, loại và thứ tự giá/mã phòng trên danh sách đã tải.
function filterExploreRooms(rows,search,type,sort,property='') {
    const query=search.trim().toLocaleLowerCase('vi');
    return rows.filter(room=>(!property || room.propertyId===Number(property)) && (!type || room.roomType===type) && (!query || [room.roomCode,room.roomName,room.propertyName,room.propertyAddress].filter(Boolean).some(value=>value.toLocaleLowerCase('vi').includes(query))))
        .sort((a,b)=>sort==='price-up'?Number(a.price)-Number(b.price):sort==='price-down'?Number(b.price)-Number(a.price):a.roomCode.localeCompare(b.roomCode,'vi',{numeric:true}));
}
// NOTE FRONTEND: Dựng thẻ danh sách theo bộ lọc hiện tại, hiển thị trạng thái không có kết quả rõ ràng.
function renderExploreRooms() {
    const rows=filterExploreRooms(exploreRooms,document.getElementById('explore-search').value,document.getElementById('explore-type').value,document.getElementById('explore-sort').value,document.getElementById('explore-property').value);
    const grid=document.getElementById('explore-grid');grid.replaceChildren();
    document.getElementById('explore-count').textContent=rows.length+' phòng phù hợp · Xem chi tiết trước khi chọn thời gian lưu trú';
    if(!rows.length) {grid.append(textNode('p','Không tìm thấy phòng phù hợp. Hãy thử từ khóa hoặc loại phòng khác.','explore-empty'));return;}
    for(const room of rows) {
        const card=textNode('article','','explore-room-card'),copy=textNode('div','','explore-room-copy');
        const title=textNode('h2',''),name=textNode('a',room.roomName);name.href=exploreLink(room);title.append(name);
        const link=textNode('a','Xem chi tiết →','explore-room-link');link.href=exploreLink(room);link.setAttribute('aria-label','Xem chi tiết '+room.roomName);
        copy.append(textNode('p',[room.propertyName,room.roomType,room.roomCode].filter(Boolean).join(' · '),'explore-eyebrow'),title);
        if(room.propertyAddress)copy.append(textNode('p',room.propertyAddress,'explore-property-address'));
        copy.append(
            textNode('p',room.description||'Thông tin mô tả phòng đang được cập nhật.','explore-description'),
            exploreCardPricing(room),link);
        card.append(explorePhoto(room),copy);grid.append(card);
    }
}
// NOTE FRONTEND: Điền tên, mô tả, ảnh, giá và cơ sở; nút đặt giữ mã phòng/loại/cơ sở trong URL chuyển tiếp.
function renderExploreDetail(room) {
    document.title=room.roomName+' — Homestay';
    document.getElementById('explore-room-title').textContent=room.roomName;
    document.getElementById('explore-room-type').textContent=room.roomType;
    document.getElementById('explore-room-code').textContent='Mã phòng · '+room.roomCode;
    const property=document.getElementById('explore-room-property');
    property.textContent=[room.propertyName,room.propertyAddress].filter(Boolean).join(' · ');property.hidden=!room.propertyName;
    document.getElementById('explore-room-description').textContent=room.description||'Thông tin mô tả phòng đang được cập nhật.';
    renderExploreRates(room);
    document.getElementById('explore-detail-photo').replaceChildren(explorePhoto(room,true));
    const bookingParams=new URLSearchParams({type:room.roomType,room:room.roomCode});
    if(room.propertyId)bookingParams.set('property',room.propertyId);
    document.getElementById('explore-book-link').href=(currentUser?.role==='admin'?'bookings.html':'reserve.html')+'?'+bookingParams;
}
// NOTE FRONTEND: Tải danh sách hoặc một phòng theo query room, kiểm tra ID và phân biệt trạng thái lỗi/chi tiết/danh sách.
async function loadExplore() {
    const id=new URLSearchParams(window.location.search).get('room');
    document.getElementById('explore-error').hidden=true;
    document.getElementById('explore-list-view').hidden=!!id;
    document.getElementById('explore-detail-view').hidden=true;
    try {
        if(id && (!/^[1-9][0-9]*$/.test(id)||!Number.isSafeInteger(Number(id)))) throw new Error('Mã phòng không hợp lệ.');
        const response=await fetch('/api/public/rooms'+(id?'/'+encodeURIComponent(id):''),{cache:'no-store'});
        const data=await responseJson(response);
        if(id) {renderExploreDetail(data);document.getElementById('explore-detail-view').hidden=false;}
        else {
            exploreRooms=expectList(data);
            const properties=document.getElementById('explore-property'),chosenProperty=properties.value || new URLSearchParams(window.location.search).get('property') || '';
            properties.replaceChildren(selectOption('','Tất cả cơ sở'));
            [...new Map(exploreRooms.filter(r=>r.propertyId).map(r=>[r.propertyId,r.propertyName])).entries()].sort((a,b)=>a[0]-b[0]).forEach(([id,name])=>properties.append(selectOption(String(id),name)));
            properties.value=chosenProperty;
            const select=document.getElementById('explore-type'),selected=select.value;
            select.replaceChildren(selectOption('','Tất cả loại phòng'));
            [...new Set(exploreRooms.map(room=>room.roomType))].sort().forEach(type=>select.append(selectOption(type,type)));
            select.value=selected;renderExploreRooms();
        }
    } catch(error) {
        document.getElementById('explore-list-view').hidden=true;
        document.getElementById('explore-error-message').textContent=error.message||'Không tải được danh mục phòng.';
        document.getElementById('explore-error').hidden=false;
    }
}
document.addEventListener('DOMContentLoaded',async()=>{
    document.getElementById('explore-filters').addEventListener('submit',event=>event.preventDefault());
    for(const id of ['explore-search','explore-property','explore-type','explore-sort'])document.getElementById(id).addEventListener('input',()=>{
        renderExploreRooms();
        if(id==='explore-property'){
            const selected=document.getElementById(id).value;setSelectedProperty(selected?Number(selected):null);
            const url=new URL(window.location.href);selected?url.searchParams.set('property',selected):url.searchParams.delete('property');window.history.replaceState(null,'',url.href);
        }
    });
    document.getElementById('explore-retry').addEventListener('click',loadExplore);
    loadExplore();
    const user=await checkLogin();
    renderPublicAccount(document.getElementById('explore-account'),user);
    window.addEventListener('pageshow',event=>{if(event.persisted)window.location.reload();});
});
