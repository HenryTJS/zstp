$ErrorActionPreference = 'Stop'

$backendRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $backendRoot

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

$mvnPath = Resolve-MavenCommand
if (-not $mvnPath) {
    Write-Host 'Maven not found. Install Apache Maven 3.9+ first.'
    exit 1
}

$javaCmd = Get-Command java -ErrorAction SilentlyContinue
if (-not $javaCmd) {
    Write-Host 'Java not found. Install Java 17+ first.'
    exit 1
}

if (Test-Path '.env') {
    Get-Content '.env' | ForEach-Object {
        if ($_ -match '^\s*#' -or $_ -match '^\s*$') {
            return
        }

        $parts = $_ -split '=', 2
        if ($parts.Count -eq 2) {
            $name = $parts[0].Trim()
            $value = $parts[1].Trim()
            Set-Item -Path ("Env:{0}" -f $name) -Value $value
        }
    }
}

Write-Host 'Building backend jar (skip tests)...'
& $mvnPath '-DskipTests' 'package'
if ($LASTEXITCODE -ne 0) {
    Write-Host 'Maven build failed. Backend was not started.'
    exit $LASTEXITCODE
}

$jarCandidates = @(
    Get-ChildItem -Path 'target' -Filter 'backend-*.jar' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*.jar.original' } |
        Sort-Object LastWriteTime -Descending
)

if ($jarCandidates.Count -eq 0) {
    Write-Host 'No runnable jar found at target/backend-*.jar.'
    exit 1
}

$jarPath = [string]$jarCandidates[0].FullName
if ([string]::IsNullOrWhiteSpace($jarPath)) {
    $jarPath = [string]$jarCandidates[0]
}
if ([string]::IsNullOrWhiteSpace($jarPath)) {
    Write-Host 'Failed to resolve runnable jar path.'
    exit 1
}
if (-not (Test-Path -Path $jarPath)) {
    Write-Host ("Jar file does not exist: {0}" -f $jarPath)
    exit 1
}

Write-Host ("Starting backend with jar: {0}" -f $jarPath)
& $javaCmd.Source '-jar' $jarPath
