// Test fixture only: exercises confirmation without business writes.
document.getElementById('demo-modal-open').addEventListener('click', async () => {
    const accepted = await askConfirmation('Kiểm tra nội dung, vùng focus và nút thao tác. Đây chỉ là mẫu giao diện, không gửi giao dịch.', 'Xác nhận thông tin');
    const output = document.getElementById('alert-message');
    output.hidden = false;
    output.className = 'alert alert-info';
    output.textContent = accepted ? 'Đã xác nhận mẫu giao diện. Không có dữ liệu được thay đổi.' : 'Đã quay lại mẫu giao diện.';
});
