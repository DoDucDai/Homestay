param([string]$MavenRepository = '')
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/security-classpath.ps1" -MavenRepository $MavenRepository
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $testClasspath = 'target/test-classes;target/classes;' + [IO.File]::ReadAllText((Join-Path (Get-Location) 'target/security-classpath.txt')).Trim()
    & java -cp $testClasspath com.homestay.security.ValidationHttpSqlServerIT
    if ($LASTEXITCODE -ne 0) { throw 'FIX-03 HTTP/SQL Server checks failed.' }
} finally { Pop-Location }
