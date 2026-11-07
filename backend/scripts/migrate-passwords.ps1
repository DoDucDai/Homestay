# NOTE BACKEND: Chuẩn bị classpath và gọi PasswordMigration --apply; script chỉ cho chạy khi có -Apply, theo yêu cầu dừng ghi và sao lưu đã ghi trong code.
# Đường dẫn được tính từ vị trí script; MavenRepository cho phép dùng bộ thư viện đã tải trong workspace.
param([switch]$Apply,[string]$MavenRepository = '')
$ErrorActionPreference = 'Stop'
if (!$Apply) { throw 'Stop application writes and back up first. Run with -Apply to migrate the configured database.' }
& "$PSScriptRoot/security-classpath.ps1" -MavenRepository $MavenRepository
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $migrationClasspath = 'target/classes;' + [IO.File]::ReadAllText((Join-Path (Get-Location) 'target/security-classpath.txt')).Trim()
    & java -cp $migrationClasspath com.homestay.security.PasswordMigration --apply
    if ($LASTEXITCODE -ne 0) { throw 'Password migration failed. Review migration instructions.' }
} finally { Pop-Location }
