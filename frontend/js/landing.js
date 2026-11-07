// NOTE FRONTEND: Trang giới thiệu và phần tài khoản/số liệu theo phiên; thống kê tải từ dữ liệu phòng hiện tại.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
let landingLoading=false,landingReady=false,landingRefreshTimer=null;
// NOTE FRONTEND: Đặt tài khoản đã xác thực hoặc nút đăng nhập lên header, dùng bộ dựng menu chung.
function renderLandingAccount(user) {
    renderPublicAccount(document.getElementById('landing-account'),user);
}
// NOTE FRONTEND: Làm mới số liệu định kỳ và tránh gửi khi trang đang ẩn.
function startLandingRefresh(){clearInterval(landingRefreshTimer);landingRefreshTimer=setInterval(()=>{if(!document.hidden)loadLandingRooms();},30000);}
// NOTE FRONTEND: Tải phòng, đếm tổng/phòng trống/loại; nếu làm mới thất bại thì giữ lần tải trước với cảnh báo số liệu cũ.
async function loadLandingRooms(){
    if(landingLoading)return;landingLoading=true;
    try{
        const rows=expectList(await apiGet('/rooms'));landingReady=true;
        document.getElementById('landing-total').textContent=rows.length;
        document.getElementById('landing-free').textContent=rows.filter(room=>room.status==='Trống').length;
        document.getElementById('landing-types-count').textContent=new Set(rows.map(room=>room.roomType)).size;
        document.getElementById('landing-sync').textContent='Dữ liệu hiện tại · '+vietnamNow().slice(11,16);
        const select=document.getElementById('landing-room-type'),previous=select.value;
        select.replaceChildren(selectOption('','Tất cả loại phòng'));for(const type of new Set(rows.map(room=>room.roomType)))select.append(selectOption(type,type));
        if([...new Set(rows.map(room=>room.roomType))].includes(previous))select.value=previous;
    }catch(err){
        document.getElementById('landing-sync').textContent=landingReady?'Chưa đồng bộ · Đang hiển thị lần tải trước':'Chưa kết nối được dữ liệu phòng';
        showRequestError(err,'Không tải được danh mục phòng.');
    }finally{landingLoading=false;}
}
document.addEventListener('DOMContentLoaded',async()=>{
    const user=await checkLogin();
    renderLandingAccount(user);
    if(!user) {
        document.getElementById('landing-greeting').textContent='Chào mừng bạn đến với Homestay';
        document.getElementById('landing-sync').textContent='Đăng nhập để xem tình trạng phòng hiện tại.';
        const roomType=document.getElementById('landing-room-type');
        ['Single','Double','VIP','Family'].forEach(type=>roomType.append(selectOption(type,type)));
        document.querySelectorAll('a[href]').forEach(link=>{
            const target=new URL(link.href,window.location.href);
            if(target.origin===window.location.origin && target.pathname.startsWith('/pages/') && !['/pages/landing.html','/pages/explore.html','/pages/login.html','/pages/policies.html','/pages/support.html','/pages/assistant.html'].includes(target.pathname))
                link.href='login.html?next='+encodeURIComponent(target.pathname+target.search+target.hash);
        });
    } else {
        document.getElementById('landing-greeting').textContent='Chào '+(user.fullName || user.username)+' · Chúc bạn một ngày thật dễ chịu';
    }
    document.getElementById('landing-year').textContent=new Date().getFullYear();
    const now=vietnamNow(),nextSlot=new Date(Math.ceil((Date.now()+7*3600000)/900000)*900000).toISOString();
    document.getElementById('landing-date').value=nextSlot.slice(0,10);document.getElementById('landing-date').min=now.slice(0,10);document.getElementById('landing-time').value=nextSlot.slice(11,16);
    document.getElementById('landing-search').addEventListener('submit',event=>{
        event.preventDefault();const date=document.getElementById('landing-date').value,time=document.getElementById('landing-time').value,error=document.getElementById('landing-search-error');
        if(!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time) || date+'T'+time<vietnamNow().slice(0,16)){error.textContent='Chọn ngày và giờ hiện tại hoặc tương lai để xem lịch.';error.hidden=false;return;}
        error.hidden=true;const params=new URLSearchParams({date,hour:time.slice(0,2),mode:'hours'});const type=document.getElementById('landing-room-type').value;if(type)params.set('type',type);
        const target='bookings.html?'+params;
        window.location.href=user?target:'login.html?next='+encodeURIComponent('/pages/'+target);
    });
    if(user) { loadLandingRooms(); startLandingRefresh(); }
    window.addEventListener('pagehide',()=>clearInterval(landingRefreshTimer));
    window.addEventListener('pageshow',event=>{if(event.persisted)window.location.reload();});
});
