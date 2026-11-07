# NOTE TRIỂN KHAI: Nhận BackendUrl, đặt URL backend cho tiến trình Python rồi chạy server.py; không tự khởi động Java.
param([string]$BackendUrl = 'http://127.0.0.1:8080')
$ErrorActionPreference = 'Stop'
$env:HOMESTAY_BACKEND_URL = $BackendUrl
& python (Join-Path $PSScriptRoot 'app/server.py')
