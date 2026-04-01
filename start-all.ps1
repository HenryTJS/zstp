$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root

function Resolve-MavenCommand {
    $mvnCmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($mvnCmd) {
        return $mvnCmd.Source
    }

    $candidateRoots = @(
        'D:\apache-maven-3.9.14',
        'D:\apache-maven-3.9.13',
        'D:\apache-maven-3.9.12'
    )

    foreach ($candidate in $candidateRoots) {
        $candidateCmd = Join-Path $candidate 'bin\mvn.cmd'
        if (Test-Path $candidateCmd) {
            $env:Path = (Join-Path $candidate 'bin') + ';' + $env:Path
            return $candidateCmd
        }
    }

    $autoFound = Get-ChildItem -Path 'D:\' -Directory -Filter 'apache-maven*' -ErrorAction SilentlyContinue |
        Sort-Object Name -Descending |
        Select-Object -First 1

    if ($autoFound) {
        $candidateCmd = Join-Path $autoFound.FullName 'bin\mvn.cmd'
        if (Test-Path $candidateCmd) {
            $env:Path = (Join-Path $autoFound.FullName 'bin') + ';' + $env:Path
            return $candidateCmd
        }
    }

    return $null
}

if (-not (Test-Path "backend/.env")) {
    Copy-Item "backend/.env.example" "backend/.env"
}

$mvnPath = Resolve-MavenCommand
if (-not $mvnPath) {
    Write-Host "Maven 未安装，无法启动 Spring Boot 后端。"
    Write-Host "请先安装 Apache Maven 3.9+，或确认 D 盘 apache-maven 目录存在。"
    exit 1
}

Write-Host "[1/2] Starting backend on http://localhost:5000 ..."
$backendProcess = Start-Process powershell -PassThru -ArgumentList @(
    '-NoExit',
    '-ExecutionPolicy', 'Bypass',
    '-File',
    "$root/backend/start-backend.ps1"
)

$maxWaitSeconds = 120
$backendReady = $false

for ($second = 1; $second -le $maxWaitSeconds; $second++) {
    Start-Sleep -Seconds 1

    if ($backendProcess.HasExited) {
        Write-Host "Backend terminal exited unexpectedly before service became ready." -ForegroundColor Red
        Write-Host "Please check backend startup logs in the opened backend terminal." -ForegroundColor Yellow
        exit 1
    }

    $portOpen = Test-NetConnection -ComputerName 'localhost' -Port 5000 -WarningAction SilentlyContinue
    if ($portOpen.TcpTestSucceeded) {
        $backendReady = $true
        break
    }
}

if (-not $backendReady) {
    Write-Host "Backend did not become ready on http://localhost:5000 within $maxWaitSeconds seconds." -ForegroundColor Red
    Write-Host "Please inspect backend terminal output, then run frontend manually if needed." -ForegroundColor Yellow
    exit 1
}

Write-Host "[2/2] Starting frontend on http://localhost:5173 ..."
Start-Process powershell -ArgumentList @(
    '-NoExit',
    '-ExecutionPolicy', 'Bypass',
    '-Command',
    "Set-Location '$root/frontend'; npm install; npm run dev"
)

Write-Host ""
Write-Host "Services are launching in two new terminals."
Write-Host "Open: http://localhost:5173"
