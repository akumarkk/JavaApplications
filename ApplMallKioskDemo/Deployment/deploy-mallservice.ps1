[CmdletBinding()]
param(
    [string]$DeployDir = "$env:LOCALAPPDATA\MallKioskDemo\MallService",
    [int]$ServicePort = 0,
    [string]$AppVersion = "1.0.0",
    [switch]$SkipDeploy
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$RepoRoot = Split-Path $PSScriptRoot -Parent
Push-Location $RepoRoot

try {
    function Write-Stage([string]$Name) {
        Write-Host "`n=== $Name ===" -ForegroundColor Cyan
    }

    function Assert-Command([string]$Cmd) {
        if (-not (Get-Command $Cmd -ErrorAction SilentlyContinue)) {
            throw "Required tool '$Cmd' was not found on PATH."
        }
    }

    # 1. Preflight
    Write-Stage "1. Preflight Checks"
    Assert-Command "mvn"
    Assert-Command "java"
    Assert-Command "jpackage"

    if ($ServicePort -gt 0) {
        $EffectivePort = $ServicePort
    } elseif ($env:MALL_SERVICE_PORT -and ($env:MALL_SERVICE_PORT -as [int])) {
        $EffectivePort = [int]$env:MALL_SERVICE_PORT
    } else {
        $EffectivePort = 8989
    }
    Write-Host "App         : MallService"
    Write-Host "AppVersion  : $AppVersion"
    Write-Host "ServicePort : $EffectivePort"
    Write-Host "DeployDir   : $DeployDir"

    # 2. CI Build (Maven)
    Write-Stage "2. CI Build (Maven)"
    & mvn clean package
    if ($LASTEXITCODE -ne 0) {
        throw "Maven build failed with exit code $LASTEXITCODE."
    }

    $JarFile = Get-ChildItem -Path (Join-Path $RepoRoot "target") -Filter "mall-kiosk-demo-*.jar" | Select-Object -First 1
    if (-not $JarFile) {
        throw "Built JAR not found in target\."
    }
    Write-Host "Built JAR: $($JarFile.FullName)"

    # 3. Package MallService (jpackage)
    Write-Stage "3. Package MallService (jpackage)"
    $StageInput = Join-Path $RepoRoot "target\jpackage-input-mallservice"
    $DistDir = Join-Path $RepoRoot "dist"
    $DistApp = Join-Path $DistDir "MallService"

    if (Test-Path $StageInput) { Remove-Item $StageInput -Recurse -Force }
    New-Item -ItemType Directory -Path $StageInput | Out-Null
    Copy-Item $JarFile.FullName -Destination $StageInput

    if (Test-Path $DistApp) {
        Get-Process -Name "MallService" -ErrorAction SilentlyContinue | ForEach-Object {
            Write-Host "Stopping running MallService (PID $($_.Id)) before clean..."
            Stop-Process -Id $_.Id -Force
        }
        Start-Sleep -Milliseconds 500
        Remove-Item $DistApp -Recurse -Force
    }
    if (-not (Test-Path $DistDir)) {
        New-Item -ItemType Directory -Path $DistDir | Out-Null
    }

    & jpackage --type app-image `
        --name MallService `
        --app-version $AppVersion `
        --input $StageInput `
        --main-jar $JarFile.Name `
        --main-class com.mall.service.MallServiceApp `
        --dest $DistDir
    if ($LASTEXITCODE -ne 0) {
        throw "jpackage failed for MallService with exit code $LASTEXITCODE."
    }

    if ($SkipDeploy) {
        Write-Stage "4. Deploy Skipped (-SkipDeploy)"
        return
    }

    # 4. CD Deploy MallService
    Write-Stage "4. CD Deploy MallService -> $DeployDir"
    Get-Process -Name "MallService" -ErrorAction SilentlyContinue | ForEach-Object {
        Write-Host "Stopping running MallService (PID $($_.Id))..."
        Stop-Process -Id $_.Id -Force
    }
    Start-Sleep -Milliseconds 500

    $DeployParent = Split-Path $DeployDir -Parent
    if ($DeployParent -and -not (Test-Path $DeployParent)) {
        New-Item -ItemType Directory -Path $DeployParent -Force | Out-Null
    }
    if (Test-Path $DeployDir) { Remove-Item $DeployDir -Recurse -Force }
    Copy-Item $DistApp -Destination $DeployDir -Recurse -Force
    Write-Host "Deployed MallService -> $DeployDir"

    $DeployedExe = Join-Path $DeployDir "MallService.exe"
    $env:MALL_SERVICE_PORT = "$EffectivePort"
    Write-Host "Starting $DeployedExe on port $EffectivePort..."
    $Proc = Start-Process -FilePath $DeployedExe -WorkingDirectory $DeployDir -PassThru

    # 5. Post-deploy Smoke Test
    Write-Stage "5. Post-Deploy Smoke Test"
    $HealthUrl = "http://127.0.0.1:$EffectivePort/health"
    $Healthy = $false
    for ($i = 1; $i -le 15; $i++) {
        if ($Proc.HasExited) {
            throw "MallService.exe exited prematurely (exit code $($Proc.ExitCode))."
        }
        try {
            $Response = Invoke-RestMethod -Uri $HealthUrl -TimeoutSec 2
            if ($Response.status -eq "UP") {
                Write-Host "Health check passed: $($Response | ConvertTo-Json -Compress)" -ForegroundColor Green
                $Healthy = $true
                break
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }

    if (-not $Healthy) {
        throw "Smoke test failed: $HealthUrl did not return status UP within 15 seconds."
    }

    Write-Host "`nMallService deployed and healthy. PID: $($Proc.Id)" -ForegroundColor Green
}
finally {
    Pop-Location
}
