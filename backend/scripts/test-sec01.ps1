param([string]$MavenRepository = '')
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/security-classpath.ps1" -MavenRepository $MavenRepository
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    $securityTestClasspath = 'target/test-classes;target/classes;' + [IO.File]::ReadAllText((Join-Path (Get-Location) 'target/security-classpath.txt')).Trim()
    & java -cp $securityTestClasspath com.homestay.security.SecurityHttpSqlServerIT
    if ($LASTEXITCODE -ne 0) { throw 'SEC-01 HTTP/SQL Server integration checks failed.' }
} finally { Pop-Location }
