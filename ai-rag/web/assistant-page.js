// NOTE MOCHI: Ghép bộ chat với lịch sử của tài khoản đã xác thực; xử lý trường hợp bộ nhớ bị chặn/đầy để vẫn cho chat.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
document.addEventListener('DOMContentLoaded',async()=>{
 const side=document.querySelector('.ai-side');
 document.getElementById('ai-admin')?.remove();
 const history=document.createElement('details');history.id='ai-history';history.className='ai-history';history.open=window.matchMedia('(min-width:651px)').matches;
 const summary=document.createElement('summary');summary.textContent='Lịch sử chat';
 const note=document.createElement('p');note.className='ai-history-note';note.textContent='30 cuộc trò chuyện gần nhất trên trình duyệt này.';
 const list=document.createElement('div');list.className='ai-history-list';list.setAttribute('aria-label','Các cuộc trò chuyện đã lưu');
 const historyStatus=document.createElement('p');historyStatus.className='ai-history-note';historyStatus.setAttribute('role','status');
 history.append(summary,note,list,historyStatus);side.append(history);
 let user,store,activeId=null,locked=false;
 try{const response=await fetch('/api/auth/me',{credentials:'include',cache:'no-store'});if(response.ok)user=(await response.json()).user;}catch{}
 const session=document.getElementById('ai-session');
 if(!user){session.textContent='Đăng nhập để trò chuyện và đặt phòng';session.href='login.html?next='+encodeURIComponent('/pages/assistant.html');}
 else{session.textContent=user.fullName||user.username;session.href='account.html';try{store=HomestayHistory.create(user.userId);store.read();}catch{store=null;historyStatus.textContent='Không đọc được bộ nhớ lịch sử. Bạn vẫn có thể chat.';}}
 const fresh=document.getElementById('ai-new-chat');
 const chat=HomestayChat.mount(document.getElementById('assistant-chat'),{
  onChange(saved){if(!store)return;try{activeId=store.save(activeId,saved);render();}catch{historyStatus.textContent='Chưa lưu được lịch sử. Bộ nhớ trình duyệt có thể đã đầy hoặc bị chặn.';}},
  onBusy(value){locked=value;fresh.disabled=value;list.querySelectorAll('button').forEach(b=>b.disabled=value);}
 });
 // NOTE MOCHI: Dựng danh sách cuộc trò chuyện, đánh dấu cuộc đang chọn và khóa đổi cuộc khi câu trả lời còn chờ.
 function render(){
  list.replaceChildren();const rows=store?store.read():[];
  if(!rows.length){const empty=document.createElement('p');empty.className='ai-history-note';empty.textContent=user?'Chưa có cuộc trò chuyện.':'Đăng nhập để lưu lịch sử chat.';list.append(empty);return;}
  for(const row of rows){const button=document.createElement('button');button.type='button';button.className='ai-history-item';button.disabled=locked;button.setAttribute('aria-pressed',String(row.id===activeId));button.title=row.title;const title=document.createElement('span');title.textContent=row.title;const date=document.createElement('small');date.textContent=new Date(row.updatedAt).toLocaleDateString('vi-VN');button.append(title,date);button.onclick=()=>{if(!chat.restore(row))return;activeId=row.id;render();if(window.matchMedia('(max-width:650px)').matches)history.open=false;};list.append(button);}
 }
 fresh.onclick=()=>{if(!chat.newChat())return;activeId=null;render();chat.focus();};
 try{render();const latest=store?.read()[0];if(latest){activeId=latest.id;chat.restore(latest);render();}}catch{historyStatus.textContent='Không đọc được lịch sử đã lưu.';}
});
