# ==============================================================================
# KMPWorker -- One-click Maven Central publisher
# Usage: .\publish.ps1
#
# Reads ALL credentials from:
#   C:\projects\plugins\credentials.properties
# ==============================================================================

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── 1. Read credentials ───────────────────────────────────────────────────────
$credsFile = "C:\projects\plugins\credentials.properties"
if (-not (Test-Path $credsFile)) {
    Write-Error "Credentials file not found: $credsFile"
    exit 1
}

$creds = @{}
Get-Content $credsFile | ForEach-Object {
    if ($_ -match "^\s*([^#=]+?)\s*=\s*(.*)$") {
        $creds[$Matches[1].Trim()] = $Matches[2].Trim()
    }
}

$sonatypeUser       = $creds["sonatypeUsername"]
$sonatypePass       = $creds["sonatypePassword"]
$signingKeyPassword = $creds["signingInMemoryKeyPassword"]
$signingKey         = $creds["signingInMemoryKey"] -replace "\\n", "`n"

if (-not $sonatypeUser -or -not $sonatypePass) {
    Write-Error "sonatypeUsername / sonatypePassword missing from $credsFile"
    exit 1
}
if (-not $signingKey) {
    Write-Error "signingInMemoryKey missing from $credsFile"
    exit 1
}
Write-Host "OK  Credentials loaded ($($signingKey.Length) chars key)"

# ── 2. Read version ───────────────────────────────────────────────────────────
$gradleProps = @{}
Get-Content "$PSScriptRoot\gradle.properties" | ForEach-Object {
    if ($_ -match "^\s*([^#=]+?)\s*=\s*(.*)$") { $gradleProps[$Matches[1].Trim()] = $Matches[2].Trim() }
}
$version = $gradleProps["VERSION_NAME"]
Write-Host "OK  Publishing version: $version"

# ── 3. Clean staging dir ──────────────────────────────────────────────────────
$stagingDir = "C:\kmpworker-release"
if (Test-Path $stagingDir) { Remove-Item $stagingDir -Recurse -Force }
New-Item -ItemType Directory $stagingDir | Out-Null
Write-Host "OK  Staging dir ready: $stagingDir"

# ── 4. Build and sign artifacts ───────────────────────────────────────────────
Write-Host ""
Write-Host "Building and signing artifacts..."
$env:ORG_GRADLE_PROJECT_signingInMemoryKey = $signingKey
if ($signingKeyPassword) { $env:ORG_GRADLE_PROJECT_signingInMemoryKeyPassword = $signingKeyPassword }

$gradleArgs = @(
    "publishAllPublicationsToLocalReleaseRepository",
    "--no-daemon", "--no-configuration-cache",
    "-x", "test", "-x", "lint"
)
& "$PSScriptRoot\gradlew.bat" @gradleArgs
if ($LASTEXITCODE -ne 0) { Write-Error "Gradle build failed"; exit 1 }

$ascCount = (Get-ChildItem $stagingDir -Recurse -Filter "*.asc" | Measure-Object).Count
Write-Host "OK  Build SUCCESS -- $ascCount signed artifacts"

# ── 5. Zip the bundle ─────────────────────────────────────────────────────────
Add-Type -AssemblyName System.IO.Compression.FileSystem
$bundlePath = "$PSScriptRoot\kmpworker-bundle.zip"
if (Test-Path $bundlePath) { Remove-Item $bundlePath }
[System.IO.Compression.ZipFile]::CreateFromDirectory($stagingDir, $bundlePath)
$bundleSize = [Math]::Round((Get-Item $bundlePath).Length / 1KB, 1)
Write-Host "OK  Bundle zipped: $bundleSize KB"

# ── 6. Upload to Maven Central ────────────────────────────────────────────────
Write-Host ""
Write-Host "Uploading to Maven Central..."
$token       = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${sonatypeUser}:${sonatypePass}"))
$deployName  = "kmpworker-$version"
$uploadUrl   = "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC&name=$deployName"

$result = curl.exe --silent --write-out "`n%{http_code}" `
    -X POST $uploadUrl `
    -H "Authorization: Bearer $token" `
    -F "bundle=@${bundlePath};type=application/zip"

$lines    = $result -split "`n"
$httpCode = $lines[-1].Trim()
$body     = ($lines[0..($lines.Length - 2)] -join "`n").Trim()

if ($httpCode -ne "201") {
    Write-Host "FAIL  Upload failed (HTTP $httpCode): $body"
    exit 1
}

$deploymentId = $body
Write-Host "OK  Uploaded! Deployment ID: $deploymentId"

# ── 7. Poll for PUBLISHED status ──────────────────────────────────────────────
Write-Host ""
Write-Host "Waiting for Maven Central validation..."
$statusUrl = "https://central.sonatype.com/api/v1/publisher/status?id=$deploymentId"
$maxWait   = 24

for ($i = 1; $i -le $maxWait; $i++) {
    Start-Sleep -Seconds 5
    $statusJson = curl.exe --silent -X POST $statusUrl -H "Authorization: Bearer $token" | ConvertFrom-Json
    $state = $statusJson.deploymentState
    Write-Host "  [$i/$maxWait] $state"

    if ($state -eq "PUBLISHED") {
        Write-Host ""
        Write-Host "SUCCESS! com.neuralheads:kmpworker:$version is LIVE on Maven Central"
        Write-Host "  https://central.sonatype.com/artifact/com.neuralheads/kmpworker/$version"
        exit 0
    }
    if ($state -eq "FAILED") {
        Write-Host "FAIL  Deployment FAILED:"
        $statusJson.errors | ConvertTo-Json -Depth 3
        exit 1
    }
}

Write-Host "WARN  Timed out waiting -- check status manually: $statusUrl"
