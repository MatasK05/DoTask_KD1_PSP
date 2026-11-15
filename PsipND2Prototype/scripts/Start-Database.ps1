param(
    [string]$PgBin = "$env:USERPROFILE/.dotask-tools/pgsql/bin",
    [string]$DataDirectory = "$env:USERPROFILE/.dotask-data"
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath "$PgBin/pg_ctl.exe") -or !(Test-Path -LiteralPath "$DataDirectory/PG_VERSION")) {
    throw 'Nerasta paruošta vietinė PostgreSQL aplinka. Atlikite README aprašytą pradinį paruošimą.'
}
& "$PgBin/pg_ctl.exe" -D $DataDirectory status
if ($LASTEXITCODE -ne 0) {
    & "$PgBin/pg_ctl.exe" -D $DataDirectory -l "$DataDirectory/server.log" -w start
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL nepasileido. Patikrinkite server.log duomenų kataloge.' }
}
