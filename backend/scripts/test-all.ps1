param([string]$MavenRepository = '')
$ErrorActionPreference = 'Stop'
& "$PSScriptRoot/build-release.ps1" -MavenRepository $MavenRepository
& "$PSScriptRoot/security-classpath.ps1" -MavenRepository $MavenRepository
$backendRoot = Split-Path $PSScriptRoot -Parent
Push-Location $backendRoot
try {
    foreach ($test in Get-ChildItem -LiteralPath 'src/test/js' -Filter '*.test.cjs') {
        & node $test.FullName
        if ($LASTEXITCODE -ne 0) { throw "Frontend checks failed: $($test.Name)" }
    }
    $qaClasspath = 'target/test-classes;target/classes;' + [IO.File]::ReadAllText((Join-Path $backendRoot 'target/security-classpath.txt')).Trim()
    foreach ($test in @('controller.BookingControllerSqlServerIT','controller.BookingPriceSnapshotSqlServerIT','controller.BookingScheduleSqlServerIT','controller.BookingServicesSqlServerIT','controller.HourlyBookingSqlServerIT','security.ValidationHttpSqlServerIT','security.SecurityHttpSqlServerIT','controller.BranchContributionSqlServerIT','controller.ReleaseSqlServerIT')) {
        $log = Join-Path $backendRoot ('target/' + $test.Split('.')[-1] + '.log')
        & java -cp $qaClasspath "com.homestay.$test" *> $log
        $result = $LASTEXITCODE
        Get-Content -LiteralPath $log | Select-String '^PASS|groups passed|Disposed|retained|AssertionError|Exception in thread'
        if ($result -ne 0) { throw "Integration checks failed: $test; inspect $log" }
    }
} finally { Pop-Location }
