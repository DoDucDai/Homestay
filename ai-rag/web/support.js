// NOTE MOCHI: Gửi yêu cầu hỗ trợ, đọc lịch sử và cho admin phản hồi; phạm vi dữ liệu được server quyết định.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
'use strict';
const byId=id=>document.getElementById(id);
// NOTE MOCHI: Tạo phần tử văn bản an toàn để tên/nội dung tài liệu hoặc ticket không trở thành HTML.
function node(tag,value,cls){const e=document.createElement(tag);e.textContent=value||'';if(cls)e.className=cls;return e;}
// NOTE MOCHI: Ghi thông báo tác vụ vào vùng trạng thái của trang.
function report(value){byId('support-status').textContent=value;}
// NOTE MOCHI: Khóa nút khi callback bất đồng bộ đang chạy, báo lỗi nếu thất bại và khôi phục nút ở finally.
async function submitForm(form,action){const controls=[...form.querySelectorAll('button')];controls.forEach(b=>b.disabled=true);try{await action();}catch(e){report(e.message);}finally{controls.forEach(b=>b.disabled=false);}}
// NOTE MOCHI: Tải ticket; khách xem phần của mình, admin có thông báo mới và form gửi phản hồi cho từng yêu cầu.
async function loadTickets(){
 const data=await apiGet('/assistant/support');const host=byId('support-tickets');host.replaceChildren();
 byId('tickets-heading').textContent=data.admin?'Hỗ trợ khách hàng · '+data.unread+' thông báo mới':'Yêu cầu của bạn';
 for(const ticket of data.tickets){const card=node('article','','support-ticket');card.append(node('h3','#'+ticket.id+' · '+ticket.subject),node('small',ticket.status+' · '+new Date(ticket.created).toLocaleString('vi-VN')),node('p',ticket.message));if(ticket.reply)card.append(node('strong','Admin phản hồi'),node('p',ticket.reply));
 if(data.admin){const form=document.createElement('form');const area=document.createElement('textarea');area.required=true;area.maxLength=3000;area.value=ticket.reply;area.setAttribute('aria-label','Phản hồi yêu cầu '+ticket.id);const button=node('button','Gửi phản hồi','btn btn-secondary');form.append(area,button);form.addEventListener('submit',e=>{e.preventDefault();submitForm(form,async()=>{await apiPost('/assistant/support/reply',{id:ticket.id,reply:area.value});await loadTickets();report('Đã gửi phản hồi.');});});card.append(form);}host.append(card);}
 if(!data.tickets.length)host.append(node('p','Chưa có yêu cầu hỗ trợ.'));
}
document.addEventListener('DOMContentLoaded',async()=>{
 const user=await checkLogin();if(!user)return;byId('support-content').hidden=false;
 byId('support-form').addEventListener('submit',event=>{event.preventDefault();const form=event.currentTarget;submitForm(form,async()=>{const result=await apiPost('/assistant/support',{subject:byId('support-subject').value,message:byId('support-message').value});form.reset();await loadTickets();report('Đã gửi yêu cầu #'+result.id+' cho admin.');});});
 byId('support-refresh').onclick=()=>loadTickets().catch(e=>report(e.message));
 try{await loadTickets();}catch(e){report(e.message);}
});
