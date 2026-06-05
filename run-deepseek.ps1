param(
    [int]$Port = 8090
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$envFile = Join-Path $projectRoot ".env"
$jarFile = Join-Path $projectRoot "target\novel-screenplay-0.0.1-SNAPSHOT.jar"

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "Missing .env file: $envFile"
}

Get-Content -LiteralPath $envFile -Encoding UTF8 |
    Where-Object { $_ -and -not $_.TrimStart().StartsWith("#") } |
    ForEach-Object {
        $name, $value = $_ -split "=", 2
        if ($name -and $null -ne $value) {
            [Environment]::SetEnvironmentVariable($name.Trim(), $value.Trim(), "Process")
        }
    }

if (-not (Test-Path -LiteralPath $jarFile)) {
    throw "Application JAR is missing. Run .\mvnw.cmd package first."
}

Write-Host "Starting MirrorPage with DeepSeek on http://localhost:$Port"
Write-Host "AI model: $env:SCREENPLAY_AI_MODEL"
& java -jar $jarFile "--server.port=$Port"
