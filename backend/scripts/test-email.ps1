param([string]$MavenRepository = '', [switch]$SqlServer)
$ErrorActionPreference = 'Stop'
$previousMavenUserHome = $env:MAVEN_USER_HOME
$workspaceWrapperHome = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../.maven-cache/wrapper-home'))
try {
    if (!$env:MAVEN_USER_HOME -and (Test-Path -LiteralPath $workspaceWrapperHome)) { $env:MAVEN_USER_HOME = $workspaceWrapperHome }
    & "$PSScriptRoot/security-classpath.ps1" -MavenRepository $MavenRepository
    Push-Location (Split-Path $PSScriptRoot -Parent)
    try {
        $emailClasspath = 'target/test-classes;target/classes;' + [IO.File]::ReadAllText((Join-Path (Get-Location) 'target/security-classpath.txt')).Trim()
        & java -cp $emailClasspath com.homestay.security.EmailDeliveryChecks
        if ($LASTEXITCODE -ne 0) { throw 'Email checks failed.' }
        if ($SqlServer) {
            & java -cp $emailClasspath com.homestay.security.EmailFlowsHttpSqlServerIT
            if ($LASTEXITCODE -ne 0) { throw 'Email HTTP/SQL Server checks failed.' }
        }
    } finally { Pop-Location }
} finally { $env:MAVEN_USER_HOME = $previousMavenUserHome }
