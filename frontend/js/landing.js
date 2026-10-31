let landingLoading=false,landingReady=false,landingRefreshTimer=null;
function startLandingRefresh(){clearInterval(landingRefreshTimer);landingRefreshTimer=setInterval(()=>{if(!document.hidden)loadLandingRooms();},30000);}
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
    const user=await checkLogin();if(!user)return;
    const account=document.getElementById('account-controls');
    if(account){
        document.getElementById('landing-account').append(account);
        const profile=account.querySelector('.account-profile'),actions=account.querySelector('.account-actions');
        if(profile && actions){const menu=document.createElement('details');menu.className='stay-account-menu';const summary=textNode('summary','⌄');summary.setAttribute('aria-label','Mở menu tài khoản');menu.append(summary,actions);account.append(menu);}
    }
    document.getElementById('landing-greeting').textContent='Chào '+(user.fullName || user.username)+' · Chúc bạn một ngày thật dễ chịu';
    document.getElementById('landing-year').textContent=new Date().getFullYear();
    const now=vietnamNow(),nextSlot=new Date(Math.ceil((Date.now()+7*3600000)/900000)*900000).toISOString();
    document.getElementById('landing-date').value=nextSlot.slice(0,10);document.getElementById('landing-date').min=now.slice(0,10);document.getElementById('landing-time').value=nextSlot.slice(11,16);
    document.getElementById('landing-search').addEventListener('submit',event=>{
        event.preventDefault();const date=document.getElementById('landing-date').value,time=document.getElementById('landing-time').value,error=document.getElementById('landing-search-error');
        if(!/^\d{4}-\d{2}-\d{2}$/.test(date) || !/^\d{2}:\d{2}$/.test(time) || date+'T'+time<vietnamNow().slice(0,16)){error.textContent='Chọn ngày và giờ hiện tại hoặc tương lai để xem lịch.';error.hidden=false;return;}
        error.hidden=true;const params=new URLSearchParams({date,hour:time.slice(0,2),mode:'hours'});const type=document.getElementById('landing-room-type').value;if(type)params.set('type',type);window.location.href='bookings.html?'+params;
    });
    loadLandingRooms();
    startLandingRefresh();window.addEventListener('pagehide',()=>clearInterval(landingRefreshTimer));
    window.addEventListener('pageshow',event=>{if(event.persisted){loadLandingRooms();startLandingRefresh();}});
});
