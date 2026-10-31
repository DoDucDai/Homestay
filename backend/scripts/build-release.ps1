param([string]$MavenRepository = '')
$ErrorActionPreference = 'Stop'
$backendRoot = Split-Path $PSScriptRoot -Parent
if (!$MavenRepository) { $MavenRepository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../../.maven-cache')) }
Push-Location $backendRoot
try {
    & .\mvnw.cmd "-Dmaven.repo.local=$MavenRepository" -q clean package
    if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    $releaseDirectory = Join-Path $backendRoot 'target/release'
    New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
    Copy-Item -LiteralPath (Join-Path $backendRoot 'target/homestay-backend-1.0.0.jar') -Destination (Join-Path $releaseDirectory 'homestay.jar')
    Copy-Item -LiteralPath (Join-Path $backendRoot '../DOCS.md') -Destination $releaseDirectory
    Copy-Item -LiteralPath (Join-Path $backendRoot '../.env.example') -Destination $releaseDirectory
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'start-release.ps1') -Destination $releaseDirectory
    Copy-Item -LiteralPath (Join-Path $backendRoot '../database') -Destination $releaseDirectory -Recurse
    $checksum = (Get-FileHash -Algorithm SHA256 -LiteralPath (Join-Path $releaseDirectory 'homestay.jar')).Hash.ToLowerInvariant()
    [IO.File]::WriteAllText((Join-Path $releaseDirectory 'homestay.jar.sha256'), "$checksum  homestay.jar`n")
    Compress-Archive -Path (Join-Path $releaseDirectory '*') -DestinationPath (Join-Path $backendRoot 'target/homestay-release.zip') -Force
    Write-Output "Release ready: $releaseDirectory"
} finally { Pop-Location }
