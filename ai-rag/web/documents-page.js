// NOTE MOCHI: Sau khi kiểm tra phiên, chỉ mở khu quản lý tài liệu cho admin; nối form thêm/sửa với API rồi tải lại danh sách.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
document.addEventListener('DOMContentLoaded',async()=>{
 const user=await checkLogin();if(!user)return;
 if(user.role!=='admin'){byId('documents-denied').hidden=false;return;}
 byId('documents-workspace').hidden=false;
 byId('document-form').addEventListener('reset',()=>{byId('document-id').value='';});
 byId('document-form').addEventListener('submit',event=>{event.preventDefault();const form=event.currentTarget;submitForm(form,async()=>{await apiPost('/assistant/documents',{id:Number(byId('document-id').value)||null,title:byId('document-title').value,content:byId('document-content').value});form.reset();await loadDocuments();report('Đã cập nhật tài liệu.');});});
 try{await loadDocuments();}catch(error){report(error.message);}
});
