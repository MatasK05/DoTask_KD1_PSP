# Nukopijuokite į config/local.ps1 ir pakeiskite reikšmes. Failas pats neįkeliamas.
# PowerShell terminale vykdykite: . ./config/local.ps1
$env:JAVA_HOME = 'C:/kelias/iki/jdk-21'
$env:DB_HOST = 'localhost'
$env:DB_PORT = '5432'
$env:DB_NAME = 'dotask'
$env:DB_USER = 'dotask'
$env:DB_PASSWORD = 'PAKEISKITE_VIETINIU_SLAPTAZODZIU'
# Tik atskira testų bazė. Įprastos programos bazės čia nenaudokite.
$env:TEST_DB_URL = 'jdbc:postgresql://localhost:5432/dotask_test'
$env:TEST_DB_USER = 'dotask_test'
$env:TEST_DB_PASSWORD = 'PAKEISKITE_TESTU_SLAPTAZODZIU'
