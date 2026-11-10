param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $MavenArguments
)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

# Repair JAVA_HOME for this process only; leave Windows settings untouched.
if (-not $env:JAVA_HOME -or -not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    $jdkCandidates = @()
    if ($javaCommand) {
        $jdkCandidates += Split-Path (Split-Path $javaCommand.Source)
    }
    foreach ($jdkRoot in @("$env:ProgramFiles/Eclipse Adoptium", "$env:ProgramFiles/Java", "$env:USERPROFILE/.jdks")) {
        if (Test-Path -LiteralPath $jdkRoot) {
            $jdkCandidates += Get-ChildItem -LiteralPath $jdkRoot -Directory | Sort-Object Name -Descending | Select-Object -ExpandProperty FullName
        }
    }
    $selectedJdk = $jdkCandidates | Where-Object { Test-Path -LiteralPath (Join-Path $_ 'bin/javac.exe') } | Select-Object -First 1
    if (-not $selectedJdk) { throw 'JDK not found. Install JDK 17 or newer and set JAVA_HOME to its directory.' }
    $env:JAVA_HOME = $selectedJdk
}
if (-not $env:MAVEN_USER_HOME) { $env:MAVEN_USER_HOME = Join-Path $env:USERPROFILE '.m2' }
if (-not $MavenArguments) { $MavenArguments = @('spring-boot:run') }
$applicationPort = 8080
if ($env:SERVER_PORT -match '^\d{1,5}$' -and [int]$env:SERVER_PORT -ge 1 -and [int]$env:SERVER_PORT -le 65535) {
    $applicationPort = [int]$env:SERVER_PORT
}
$applicationUrl = "http://localhost:$applicationPort/"
Write-Host "Using JDK: $env:JAVA_HOME"
Write-Host "Bbcinema - Login: $applicationUrl"
Write-Host "Swagger: ${applicationUrl}swagger-ui/index.html"

# Main opens the browser after application startup, for both IntelliJ and this script.
& "$PSScriptRoot/mvnw.cmd" "-Dmaven.repo.local=$env:MAVEN_USER_HOME/repository" @MavenArguments
exit $LASTEXITCODE
