param(
    [string]$MavenRepository = '',
    [string]$TestMainClass = 'com.homestay.controller.BookingControllerSqlServerIT'
)

$ErrorActionPreference = 'Stop'
if (!$MavenRepository) {
    $workspaceCache = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../.maven-cache'))
    $MavenRepository = if (Test-Path -LiteralPath $workspaceCache) { $workspaceCache } else { Join-Path $env:USERPROFILE '.m2/repository' }
}
Push-Location (Split-Path $PSScriptRoot -Parent)
try {
    & "$PSScriptRoot/security-classpath.ps1" -MavenRepository $MavenRepository
    $testClasspath = 'target/test-classes;target/classes;' + [IO.File]::ReadAllText((Join-Path (Get-Location) 'target/security-classpath.txt')).Trim()
    & java -cp $testClasspath $TestMainClass
    if ($LASTEXITCODE -ne 0) { throw 'SQL Server integration checks failed.' }
} finally {
    Pop-Location
}
