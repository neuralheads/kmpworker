# ==============================================================================
# KMPWorker — One-click Maven Central publisher
# Usage: .\publish.ps1
#
# Reads ALL credentials automatically from:
#   - local.properties           (Sonatype username/password, key password)
#   - ~/.gradle/gradle.properties (full multi-line PGP signing key)
# ==============================================================================

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

# ── 1. Read ALL credentials from shared credentials file ──────────────────────
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

$sonatypeUser      = $creds["sonatypeUsername"]
$sonatypePass      = $creds["sonatypePassword"]
$signingKeyPassword = $creds["signingInMemoryKeyPassword"]
# Key is stored as a single line with literal \n — unescape to real newlines
$signingKey        = $creds["signingInMemoryKey"] -replace "\\n", "`n"

if (-not $sonatypeUser -or -not $sonatypePass) {
    Write-Error "sonatypeUsername / sonatypePassword missing from $credsFile"
    exit 1
}
if (-not $signingKey) {
    Write-Error "signingInMemoryKey missing from $credsFile"
    exit 1
}
Write-Host "✔  Credentials loaded from $credsFile"
Write-Host "✔  Signing key: $($signingKey.Length) chars"

# ── 3. Read current version from gradle.properties ───────────────────────────
$gradleProps = @{}
Get-Content "$PSScriptRoot\gradle.properties" | ForEach-Object {
    if ($_ -match "^\s*([^#=]+?)\s*=\s*(.*)$") { $gradleProps[$Matches[1].Trim()] = $Matches[2].Trim() }
}
$version = $gradleProps["VERSION_NAME"]
Write-Host "✔  Publishing version: $version"

# ── 4. Clean staging folder ───────────────────────────────────────────────────
$stagingDir = "C:\kmpworker-release"
if (Test-Path $stagingDir) { Remove-Item $stagingDir -Recurse -Force }
New-Item -ItemType Directory $stagingDir | Out-Null
Write-Host "✔  Staging dir ready: $stagingDir"

# ── 5. Build & publish signed artifacts to local staging dir ─────────────────
Write-Host "`n▶  Building and signing artifacts..."
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
Write-Host "✔  Build SUCCESS — $ascCount signed artifacts"

# ── 6. Zip the bundle ─────────────────────────────────────────────────────────
Add-Type -AssemblyName System.IO.Compression.FileSystem
$bundlePath = "$PSScriptRoot\kmpworker-bundle.zip"
if (Test-Path $bundlePath) { Remove-Item $bundlePath }
[System.IO.Compression.ZipFile]::CreateFromDirectory($stagingDir, $bundlePath)
$bundleSize = [Math]::Round((Get-Item $bundlePath).Length / 1KB, 1)
Write-Host "✔  Bundle zipped: $bundleSize KB"

# ── 7. Upload to Maven Central ────────────────────────────────────────────────
Write-Host "`n▶  Uploading to Maven Central..."
$token = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("${sonatypeUser}:${sonatypePass}"))
$deploymentName = "kmpworker-$version"
$uploadUrl = "https://central.sonatype.com/api/v1/publisher/upload?publishingType=AUTOMATIC&name=$deploymentName"

$result = curl.exe -s -w "`n%{http_code}" -X POST $uploadUrl `
    -H "Authorization: Bearer $token" `
    -F "bundle=@$bundlePath;type=application/zip"

$lines    = $result -split "`n"
$httpCode = $lines[-1].Trim()
$body     = ($lines[0..($lines.Length - 2)] -join "`n").Trim()

if ($httpCode -eq "201") {
    $deploymentId = $body
    Write-Host "✔  Uploaded! Deployment ID: $deploymentId"

    # ── 8. Poll for status ────────────────────────────────────────────────────
    Write-Host "`n▶  Waiting for validation..."
    $statusUrl = "https://central.sonatype.com/api/v1/publisher/status?id=$deploymentId"
    $maxWait = 12  # ~60 seconds
    for ($attempt = 1; $attempt -le $maxWait; $attempt++) {
        Start-Sleep -Seconds 5
        $statusJson = curl.exe -s -X POST $statusUrl -H "Authorization: Bearer $token" | ConvertFrom-Json
        $state = $statusJson.deploymentState
        Write-Host "  [$attempt/$maxWait] State: $state"
        if ($state -eq "PUBLISHED") {
            Write-Host "`n🎉  SUCCESS! com.neuralheads:kmpworker:$version is LIVE on Maven Central"
            Write-Host "    https://central.sonatype.com/artifact/com.neuralheads/kmpworker/$version"
            break
        }
        if ($state -eq "FAILED") {
            Write-Host "`n❌  Deployment FAILED. Errors:"
            $statusJson.errors | ConvertTo-Json -Depth 3
            exit 1
        }
    }
} else {
    Write-Host "❌  Upload failed (HTTP $httpCode): $body"
    exit 1
}
