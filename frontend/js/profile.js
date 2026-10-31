function renderAccountProfile(user){
    const role=user.role==='admin'?'Quản trị viên':'Nhân viên';
    const name=user.fullName || user.username;
    document.getElementById('profile-name').textContent=name;
    document.getElementById('profile-avatar').textContent=name.trim().split(/\s+/).slice(-2).map(part=>part[0]).join('').toLocaleUpperCase('vi');
    document.getElementById('profile-role').textContent=role;
    document.getElementById('profile-full-name').textContent=user.fullName || 'Chưa cập nhật';
    document.getElementById('profile-username').textContent=user.username;
    document.getElementById('profile-role-detail').textContent=role;
    document.getElementById('profile-id').textContent='TK'+user.userId;
    document.getElementById('profile-description').textContent=user.role==='admin'?'Quản lý hệ thống và theo dõi hoạt động lưu trú.':'Thực hiện đặt phòng, tiếp nhận khách và theo dõi lượt lưu trú.';
    document.getElementById('quan-tri').hidden=user.role!=='admin';
}
document.addEventListener('DOMContentLoaded',async()=>{
    const user=await checkLogin();if(!user)return;
    renderAccountProfile(user);
    document.getElementById('profile-password-button').addEventListener('click',()=>document.getElementById('account-change-btn').click());
});
