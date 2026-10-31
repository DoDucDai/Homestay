param(
    [string]$Database = '',
    [ValidateSet('preview','apply','test')][string]$Operation = 'preview',
    [string]$EvidenceDirectory = '',
    [string]$MavenRepository = ''
)
$ErrorActionPreference = 'Stop'
if (!$Database) { throw 'Specify -Database explicitly; it must match the configured database.' }
$backendRoot = Split-Path $PSScriptRoot -Parent
if (!$EvidenceDirectory) { $EvidenceDirectory = Join-Path $backendRoot ('target/demo-import-' + (Get-Date -Format 'yyyyMMdd-HHmmss')) }
$EvidenceDirectory = [IO.Path]::GetFullPath($EvidenceDirectory)
New-Item -ItemType Directory -Force -Path $EvidenceDirectory | Out-Null
& python (Join-Path $PSScriptRoot 'validate-demo-data.py') (Join-Path $backendRoot '../contributions') (Join-Path $EvidenceDirectory 'validated-data.json')
if ($LASTEXITCODE -ne 0) { throw 'CSV validation failed; no database connection was made.' }
& "$PSScriptRoot/security-classpath.ps1" -MavenRepository $MavenRepository
Push-Location $backendRoot
try {
    $classpath = 'target/test-classes;target/classes;' + [IO.File]::ReadAllText((Join-Path $backendRoot 'target/security-classpath.txt')).Trim()
    $classes = Join-Path $backendRoot 'target/demo-import-classes'
    New-Item -ItemType Directory -Force -Path $classes | Out-Null
    & javac -encoding UTF-8 -cp $classpath -d $classes (Join-Path $PSScriptRoot 'DemoDataImport.java')
    if ($LASTEXITCODE -ne 0) { throw 'Import tool compilation failed.' }
    & java -cp "$classes;$classpath" com.homestay.controller.DemoDataImport $Operation (Join-Path $EvidenceDirectory 'validated-data.json') $Database $EvidenceDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Import failed; inspect the exception and receipt. Transactional writes were rolled back.' }
} finally { Pop-Location }
