# NOTE BACKEND: Chạy ReleaseSqlServerIT bằng bộ chạy chung để kiểm tra JAR, migration và quy trình backup/restore trong database thử.
# Đường dẫn được tính từ vị trí script; MavenRepository cho phép dùng bộ thư viện đã tải trong workspace.
param([string]$MavenRepository = '')
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/test-fix02.ps1" -MavenRepository $MavenRepository -TestMainClass 'com.homestay.controller.ReleaseSqlServerIT'
