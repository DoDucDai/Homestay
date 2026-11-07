// NOTE MOCHI: Dựng nút Mochi nổi, chỉ tải bộ chat khi mở lần đầu; phần thông báo Support hoạt động riêng.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
document.addEventListener('DOMContentLoaded',()=>{
 const wrap=document.createElement('div');wrap.className='ai-mascot-wrap';
 const button=document.createElement('button');button.type='button';button.className='ai-mascot';button.setAttribute('aria-label','Mở chat nhanh với Homestay AI');button.setAttribute('aria-expanded','false');button.setAttribute('aria-controls','ai-quick-panel');
 const mascot=document.createElement('img');mascot.src='/ai/mascot.svg';mascot.alt='';mascot.width=92;mascot.height=108;mascot.className='ai-mascot-image';button.replaceChildren(mascot);
 const hint=document.createElement('span');hint.className='ai-mascot-hint';hint.textContent='Mochi đây! Bạn cần tìm phòng?';wrap.append(hint,button);
 const panel=document.createElement('section');panel.id='ai-quick-panel';panel.className='ai-quick-panel';panel.hidden=true;panel.setAttribute('role','region');panel.setAttribute('aria-label','Chat nhanh Homestay AI');
 const head=document.createElement('header');head.className='ai-chat-heading';const title=document.createElement('strong');title.textContent='Homestay AI';const full=document.createElement('a');full.href='assistant.html';full.textContent='Mở trang đầy đủ ↗';const close=document.createElement('button');close.type='button';close.textContent='×';close.setAttribute('aria-label','Đóng chat nhanh');head.append(title,full,close);const host=document.createElement('div');host.className='ai-chat-root';panel.append(head,host);document.body.append(wrap,panel);
 let chat,loading;
 // NOTE MOCHI: Đóng panel chat, cập nhật aria-expanded và trả focus về nút mở.
 function hide(){panel.hidden=true;wrap.classList.remove('is-open');button.setAttribute('aria-expanded','false');button.focus();}
 close.onclick=hide;
 document.addEventListener('keydown',e=>{if(e.key==='Escape'&&!panel.hidden)hide();});
 button.onclick=async()=>{
  if(!panel.hidden){hide();return;}
  panel.hidden=false;wrap.classList.add('is-open');button.setAttribute('aria-expanded','true');hint.hidden=true;
  try{
   if(!window.HomestayChat){if(!loading)loading=new Promise((resolve,reject)=>{const script=document.createElement('script');script.src='/ai/chat-client.js';script.onload=resolve;script.onerror=()=>{loading=null;reject(new Error());};document.head.append(script);});await loading;}
   if(!chat){host.replaceChildren();chat=HomestayChat.mount(host,{compact:true});}if(!panel.hidden)chat.focus();
  }catch{host.textContent='Chưa tải được trợ lý. Vui lòng thử lại.';}
 };
 // Support notifications stay separate from the AI mascot.
 // NOTE MOCHI: Khi trang đang hiển thị, kiểm tra admin rồi đọc số ticket chưa xem để cập nhật liên kết Support.
 async function refresh(){if(document.hidden)return;try{const me=await fetch('/api/auth/me',{credentials:'include',cache:'no-store'});if(!me.ok)return;const account=await me.json();if(account.user?.role!=='admin')return;const response=await fetch('/api/assistant/support',{credentials:'include',cache:'no-store'});if(!response.ok)return;const data=await response.json();for(const link of document.querySelectorAll('.stay-top-nav a[href="support.html"], .policy-header nav a[href="support.html"]'))link.textContent=data.unread?'Support ('+data.unread+')':'Support';}catch{}}
 refresh();const timer=setInterval(refresh,30000);window.addEventListener('pagehide',()=>clearInterval(timer));
});
