// NOTE MOCHI: Dựng danh sách và thao tác sửa/gỡ tài liệu RAG; các yêu cầu quản trị cần quyền admin ở server.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
'use strict';
const byId=id=>document.getElementById(id);
// NOTE MOCHI: Tạo phần tử văn bản an toàn để tên/nội dung tài liệu hoặc ticket không trở thành HTML.
function node(tag,value,cls){const e=document.createElement(tag);e.textContent=value||'';if(cls)e.className=cls;return e;}
// NOTE MOCHI: Ghi thông báo tác vụ vào vùng trạng thái của trang.
function report(value){byId('support-status').textContent=value;}
// NOTE MOCHI: Khóa nút khi callback bất đồng bộ đang chạy, báo lỗi nếu thất bại và khôi phục nút ở finally.
async function submitForm(form,action){const controls=[...form.querySelectorAll('button')];controls.forEach(b=>b.disabled=true);try{await action();}catch(e){report(e.message);}finally{controls.forEach(b=>b.disabled=false);}}
// NOTE MOCHI: Tải tài liệu, dựng thẻ kèm phiên bản và nối các nút đưa dữ liệu lên form hoặc gỡ khỏi kho RAG.
async function loadDocuments(){const data=await apiGet('/assistant/documents');const host=byId('ai-documents');host.replaceChildren();for(const doc of data.documents){const card=node('article','','ai-document');const edit=node('button','Chỉnh sửa','btn btn-secondary');edit.onclick=()=>{byId('document-id').value=doc.id;byId('document-title').value=doc.title;byId('document-content').value=doc.content;byId('document-title').focus();};const remove=node('button','Gỡ khỏi RAG','btn btn-secondary');remove.onclick=async()=>{if(!confirm('Gỡ tài liệu này khỏi kho RAG?'))return;remove.disabled=true;try{await apiPost('/assistant/documents',{id:doc.id,delete:true});await loadDocuments();}catch(e){report(e.message);remove.disabled=false;}};card.append(node('h3',doc.title+' · v'+doc.version),node('p',doc.content),edit,remove);host.append(card);}}
