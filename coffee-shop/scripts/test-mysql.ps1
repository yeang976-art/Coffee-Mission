param(
    [string]$MySqlPath = 'mysql'
)

$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME) -or $null -eq $env:DB_PASSWORD) {
    throw '현재 터미널의 DB_USERNAME, DB_PASSWORD를 먼저 설정해주세요.'
}
$taskCommand = Get-Command $MySqlPath -ErrorAction Stop
$taskRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$taskDatabase = 'coffee_mission_test_' + [guid]::NewGuid().ToString('N')
if ($taskDatabase -notmatch '^coffee_mission_test_[a-f0-9]{32}$') {
    throw '테스트 DB 이름 확인 실패'
}
$taskPreviousEnv = @{}
foreach ($taskName in @('MYSQL_PWD', 'MYSQL_TEST_URL', 'MYSQL_TEST_USERNAME', 'MYSQL_TEST_PASSWORD')) {
    $taskPreviousEnv[$taskName] = [Environment]::GetEnvironmentVariable($taskName)
}
$taskCreated = $false
try {
    $env:MYSQL_PWD = $env:DB_PASSWORD
    & $taskCommand.Source --host=127.0.0.1 --port=3306 "--user=$env:DB_USERNAME" --batch --skip-column-names --execute "CREATE DATABASE $taskDatabase CHARACTER SET utf8mb4; SELECT VERSION();"
    if ($LASTEXITCODE -ne 0) {
        throw '별도 테스트 DB 생성 실패'
    }
    $taskCreated = $true
    $env:MYSQL_TEST_URL = "jdbc:mysql://localhost:3306/${taskDatabase}?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
    $env:MYSQL_TEST_USERNAME = $env:DB_USERNAME
    $env:MYSQL_TEST_PASSWORD = $env:DB_PASSWORD
    Push-Location -LiteralPath $taskRoot
    try {
        .\gradlew.bat --no-daemon mysqlTest
        if ($LASTEXITCODE -ne 0) {
            throw 'MySQL 동시성 테스트 실패'
        }
    } finally {
        Pop-Location
    }
} finally {
    if ($taskCreated -and $taskDatabase -match '^coffee_mission_test_[a-f0-9]{32}$') {
        & $taskCommand.Source --host=127.0.0.1 --port=3306 "--user=$env:DB_USERNAME" --execute "DROP DATABASE $taskDatabase"
        if ($LASTEXITCODE -ne 0) {
            Write-Warning '생성한 테스트 DB 정리가 필요합니다.'
        }
    }
    foreach ($taskName in $taskPreviousEnv.Keys) {
        [Environment]::SetEnvironmentVariable($taskName, $taskPreviousEnv[$taskName])
    }
}
