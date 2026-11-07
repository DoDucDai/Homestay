# NOTE BACKEND: Gọi bộ chạy kiểm thử SQL chung với BookingPriceSnapshotSqlServerIT để kiểm tra giữ giá và nội dung hóa đơn cũ.
# Đường dẫn được tính từ vị trí script; MavenRepository cho phép dùng bộ thư viện đã tải trong workspace.
param(
    [string]$MavenRepository = ''
)

& "$PSScriptRoot/test-fix02.ps1" -MavenRepository $MavenRepository -TestMainClass 'com.homestay.controller.BookingPriceSnapshotSqlServerIT'
