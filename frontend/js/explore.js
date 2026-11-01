let exploreRooms=[];
function explorePhoto(room) {
    const photo=textNode('div','','explore-photo');
    const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('viewBox','0 0 24 24');svg.setAttribute('aria-hidden','true');
    const use=document.createElementNS('http://www.w3.org/2000/svg','use');use.setAttribute('href','../css/icons.svg#bed');svg.append(use);
    photo.append(svg,textNode('strong',room.roomCode),textNode('small','Hình ảnh phòng đang được cập nhật'));
    return photo;
}
function exploreLink(room) {return 'explore.html?room='+encodeURIComponent(room.roomId);}
function filterExploreRooms(rows,search,type,sort) {
    const query=search.trim().toLocaleLowerCase('vi');
    return rows.filter(room=>(!type || room.roomType===type) && (!query || [room.roomCode,room.roomName].some(value=>value.toLocaleLowerCase('vi').includes(query))))
        .sort((a,b)=>sort==='price-up'?Number(a.price)-Number(b.price):sort==='price-down'?Number(b.price)-Number(a.price):a.roomCode.localeCompare(b.roomCode,'vi',{numeric:true}));
}
function renderExploreRooms() {
    const rows=filterExploreRooms(exploreRooms,document.getElementById('explore-search').value,document.getElementById('explore-type').value,document.getElementById('explore-sort').value);
    const grid=document.getElementById('explore-grid');grid.replaceChildren();
    document.getElementById('explore-count').textContent=rows.length+' phòng phù hợp · Xem chi tiết trước khi chọn thời gian lưu trú';
    if(!rows.length) {grid.append(textNode('p','Không tìm thấy phòng phù hợp. Hãy thử từ khóa hoặc loại phòng khác.','explore-empty'));return;}
    for(const room of rows) {
        const card=textNode('article','','explore-room-card'),copy=textNode('div','','explore-room-copy');
        const title=textNode('h2',''),name=textNode('a',room.roomName);name.href=exploreLink(room);title.append(name);
        const link=textNode('a','Xem chi tiết →','explore-room-link');link.href=exploreLink(room);link.setAttribute('aria-label','Xem chi tiết '+room.roomName);
        copy.append(textNode('p',room.roomType+' · '+room.roomCode,'explore-eyebrow'),title,
            textNode('p',room.description||'Thông tin mô tả phòng đang được cập nhật.','explore-description'),
            textNode('p',formatMoney(room.price)+' / đêm','explore-room-price'),
            textNode('p',room.hourlyPrice==null?'Chưa có giá theo giờ':formatMoney(room.hourlyPrice)+' / giờ','explore-hourly'),link);
        card.append(explorePhoto(room),copy);grid.append(card);
    }
}
function renderExploreDetail(room) {
    document.title=room.roomName+' — Homestay';
    document.getElementById('explore-room-title').textContent=room.roomName;
    document.getElementById('explore-room-type').textContent=room.roomType;
    document.getElementById('explore-room-code').textContent='Mã phòng · '+room.roomCode;
    document.getElementById('explore-room-description').textContent=room.description||'Thông tin mô tả phòng đang được cập nhật.';
    document.getElementById('explore-night-price').textContent=formatMoney(room.price)+' / đêm';
    document.getElementById('explore-hour-price').textContent=room.hourlyPrice==null?'Chưa có giá theo giờ':formatMoney(room.hourlyPrice)+' / giờ';
    document.getElementById('explore-detail-photo').replaceChildren(explorePhoto(room));
    document.getElementById('explore-book-link').href='bookings.html?'+new URLSearchParams({type:room.roomType,room:room.roomCode});
}
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
    for(const id of ['explore-search','explore-type','explore-sort'])document.getElementById(id).addEventListener('input',renderExploreRooms);
    document.getElementById('explore-retry').addEventListener('click',loadExplore);
    loadExplore();
    const user=await checkLogin();
    const link=textNode('a',user?(user.fullName||user.username):'Đăng nhập','stay-button stay-button-gold');link.href=user?'account.html':'login.html?next='+encodeURIComponent(window.location.pathname+window.location.search);
    const host=document.getElementById('explore-account');host.replaceChildren(link);host.setAttribute('aria-busy','false');
    window.addEventListener('pageshow',event=>{if(event.persisted)window.location.reload();});
});
