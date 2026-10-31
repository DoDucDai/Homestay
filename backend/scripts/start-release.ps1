param([string]$JarPath = (Join-Path $PSScriptRoot 'homestay.jar'), [ValidateSet('dev','prod')][string]$Profile = 'prod')
$ErrorActionPreference = 'Stop'
foreach ($setting in @('DB_HOST','DB_PORT','DB_NAME','DB_USER','DB_PASSWORD')) {
    if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable($setting))) { throw "Missing environment variable: $setting" }
}
if ($Profile -eq 'prod') {
    if ($env:COOKIE_SECURE -ne 'true' -or $env:DB_ENCRYPT -ne 'true' -or $env:DB_TRUST_SERVER_CERTIFICATE -ne 'false') {
        throw 'Production requires COOKIE_SECURE=true, DB_ENCRYPT=true, DB_TRUST_SERVER_CERTIFICATE=false and HTTPS infrastructure.'
    }
}
& java -jar $JarPath "--spring.profiles.active=$Profile"
exit $LASTEXITCODE
