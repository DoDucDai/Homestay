// NOTE FRONTEND: Hiển thị và sửa hồ sơ của tài khoản hiện tại; không có trường cho người dùng tự đổi role.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
// NOTE FRONTEND: Đổ họ tên, ảnh chữ viết tắt, ID và quyền vào trang; đồng bộ trạng thái email qua profile-email.js nếu đã được nạp.
function renderAccountProfile(user){
    const operation=document.getElementById('profile-operation-link');
    operation.href=user.role==='admin'?'bookings.html':'my-bookings.html';
    operation.textContent=user.role==='admin'?'Mở khu vận hành ↗':'Đặt phòng của tôi ↗';
    document.getElementById('verification-return').href=user.role==='admin'?'bookings.html':'reserve.html';
    const role=user.role==='admin'?'Quản trị viên':'Khách hàng';
    const name=user.fullName || user.username;
    document.getElementById('profile-name').textContent=name;
    document.getElementById('profile-avatar').textContent=name.trim().split(/\s+/).slice(-2).map(part=>part[0]).join('').toLocaleUpperCase('vi');
    document.getElementById('profile-role').textContent=role;
    document.getElementById('profile-full-name').textContent=user.fullName || 'Chưa cập nhật';
    document.getElementById('profile-username').textContent=user.username;
    document.getElementById('profile-role-detail').textContent=role;
    document.getElementById('profile-id').textContent='TK'+user.userId;
    document.getElementById('profile-description').textContent=user.role==='admin'?'Quản lý hệ thống và theo dõi hoạt động lưu trú.':'Quản lý thông tin cá nhân, đặt phòng, dịch vụ và thanh toán.';
    if(typeof renderEmailVerification==='function')renderEmailVerification(user);
}
document.addEventListener('DOMContentLoaded',async()=>{
    const user=await checkLogin();if(!user)return;
    renderAccountProfile(user);
    document.getElementById('profile-password-button').addEventListener('click',()=>document.getElementById('account-change-btn').click());
});
window.addEventListener('pageshow',event=>{if(event.persisted)window.location.reload();});

document.addEventListener('DOMContentLoaded',async()=>{
    const form=document.getElementById('profile-edit-form');
    const keys=['fullName','phone','email','birthDate','city'];
    const fill=user=>keys.forEach(key=>{document.getElementById('edit-'+key).value=user[key]||'';});
    document.getElementById('edit-birthDate').max=new Intl.DateTimeFormat('en-CA',{timeZone:'Asia/Ho_Chi_Minh',year:'numeric',month:'2-digit',day:'2-digit'}).format(new Date());
    const status=document.getElementById('profile-edit-status');
    const button=form.querySelector('button');button.disabled=true;
    try {const result=await apiGet('/auth/me');fill(result.user);}
    catch(error){status.textContent=error.message;return;}
    button.disabled=false;
    // NOTE FRONTEND: Đọc các trường hồ sơ cho phép, kiểm tra điện thoại/email rồi PUT /auth/profile; dùng hồ sơ server trả lại để hiển thị.
    form.addEventListener('submit',async event=>{
        event.preventDefault();if(button.disabled || !form.reportValidity())return;
        const body=Object.fromEntries(keys.map(key=>[key,document.getElementById('edit-'+key).value.trim()]));
        if(!validAccountPhone(body.phone)) { status.textContent='Số điện thoại cần đúng 10 chữ số, bắt đầu bằng 0.';document.getElementById('edit-phone').focus();return; }
        if(!validAccountEmail(body.email)) { status.textContent='Nhập email hợp lệ.';document.getElementById('edit-email').focus();return; }
        button.disabled=true;status.textContent='Đang lưu...';
        try {const result=await apiPut('/auth/profile',body);fill(result.user);renderAccountProfile(result.user);addAccountControls(result.user);status.textContent='Đã cập nhật thông tin tài khoản.';}
        catch(error){status.textContent=error.message||'Không thể lưu thông tin.';}
        finally {button.disabled=false;}
    });
});
