# NOTE BACKEND: Gọi bộ chạy kiểm thử SQL chung với BookingScheduleSqlServerIT để kiểm tra trùng lịch, hủy đặt và các thao tác đồng thời.
# Đường dẫn được tính từ vị trí script; MavenRepository cho phép dùng bộ thư viện đã tải trong workspace.
param(
    [string]$MavenRepository = ''
)

& "$PSScriptRoot/test-fix02.ps1" -MavenRepository $MavenRepository -TestMainClass 'com.homestay.controller.BookingScheduleSqlServerIT'
