# NOTE BACKEND: Biên dịch mã chính và mã kiểm thử rồi xuất danh sách thư viện vào target/security-classpath.txt để các công cụ Java chạy ngoài Maven.
# Đường dẫn được tính từ vị trí script; MavenRepository cho phép dùng bộ thư viện đã tải trong workspace.
param([string]$MavenRepository = '')
$ErrorActionPreference = 'Stop'
if (!$MavenRepository) {
    $workspaceCache = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../.maven-cache'))
    $MavenRepository = if (Test-Path -LiteralPath $workspaceCache) { $workspaceCache } else { Join-Path $env:USERPROFILE '.m2/repository' }
}
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & .\mvnw.cmd "-Dmaven.repo.local=$MavenRepository" -q test-compile org.apache.maven.plugins:maven-dependency-plugin:3.6.1:build-classpath '-Dmdep.outputFile=target/security-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Security build/classpath failed.' }
} finally { Pop-Location }
