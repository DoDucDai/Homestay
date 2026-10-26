param(
    [string]$MavenRepository = ''
)

& "$PSScriptRoot/test-fix02.ps1" -MavenRepository $MavenRepository -TestMainClass 'com.homestay.controller.BookingPriceSnapshotSqlServerIT'
