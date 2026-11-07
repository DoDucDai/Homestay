// NOTE FRONTEND: Khi DOM sẵn sàng, kiểm tra phiên và dựng khu tài khoản cho các trang công khai dùng chung header.
// Luồng chung: sự kiện người dùng → kiểm tra dữ liệu/trạng thái → gọi API hoặc dựng giao diện → cập nhật kết quả.
document.addEventListener('DOMContentLoaded',async()=>{
 const user=await checkLogin();
 renderPublicAccount(document.getElementById('public-account'),user);
});
