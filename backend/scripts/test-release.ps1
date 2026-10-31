param([string]$MavenRepository = '')
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/test-fix02.ps1" -MavenRepository $MavenRepository -TestMainClass 'com.homestay.controller.ReleaseSqlServerIT'
