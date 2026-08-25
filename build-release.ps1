[CmdletBinding()]
param(
    [switch]$SkipWindows,
    [switch]$SkipLinux,
    [switch]$InstallOnly,
    [string]$JbrHome,
    [string]$LinuxImage = "hstyi/jbr:25.0.2b329.111"
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$RepoRoot = $PSScriptRoot
$ArtifactsRoot = Join-Path $RepoRoot "artifacts"
$ToolchainRoot = Join-Path $RepoRoot ".toolchains"

function Test-Command([string]$Name) {
    return $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

function Install-WingetPackage([string]$Id) {
    Write-Host "Installing $Id ..."
    & winget install --id $Id --exact --source winget --silent --accept-package-agreements --accept-source-agreements
    if ($LASTEXITCODE -ne 0) {
        throw "winget could not install $Id (exit code $LASTEXITCODE)."
    }
}

function Add-GitToolsToPath {
    $gitRoots = @()
    foreach ($base in @($env:ProgramFiles, ${env:ProgramFiles(x86)}, $env:LOCALAPPDATA)) {
        if ($base) {
            $gitRoots += Join-Path $base $(if ($base -eq $env:LOCALAPPDATA) { "Programs\\Git\\usr\\bin" } else { "Git\\usr\\bin" })
        }
    }
    $gitRoots = $gitRoots | Where-Object { Test-Path $_ }

    foreach ($path in $gitRoots) {
        if (($env:Path -split ';') -notcontains $path) {
            $env:Path = "$path;$env:Path"
        }
    }
}

function Get-JbrHome([string]$RequestedHome) {
    if ($RequestedHome) {
        if (-not (Test-Path (Join-Path $RequestedHome "bin\\jpackage.exe"))) {
            throw "JbrHome does not contain bin\\jpackage.exe: $RequestedHome"
        }
        return (Resolve-Path $RequestedHome).Path
    }

    $architecture = if ([Environment]::Is64BitOperatingSystem -and $env:PROCESSOR_ARCHITECTURE -eq "ARM64") { "aarch64" } else { "x64" }
    $archive = Join-Path $ToolchainRoot "jbrsdk-25.0.2-windows-$architecture-b329.111.zip"
    $extractRoot = Join-Path $ToolchainRoot "jbrsdk-25.0.2-windows-$architecture-b329.111"
    $existing = Get-ChildItem -Path $extractRoot -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path (Join-Path $_.FullName "bin\\jpackage.exe") } |
        Select-Object -First 1

    if (-not $existing) {
        New-Item -ItemType Directory -Force -Path $ToolchainRoot, $extractRoot | Out-Null
        $url = "https://cache-redirector.jetbrains.com/intellij-jbr/jbrsdk-25.0.2-windows-$architecture-b329.111.zip"
        Write-Host "Downloading JetBrains Runtime 25 for $architecture ..."
        Invoke-WebRequest -Uri $url -OutFile $archive
        Expand-Archive -Path $archive -DestinationPath $extractRoot -Force
        $existing = Get-ChildItem -Path $extractRoot -Directory |
            Where-Object { Test-Path (Join-Path $_.FullName "bin\\jpackage.exe") } |
            Select-Object -First 1
    }

    if (-not $existing) {
        throw "The downloaded JetBrains Runtime does not contain jpackage.exe."
    }
    return $existing.FullName
}

function Invoke-Gradle([string[]]$Arguments) {
    & (Join-Path $RepoRoot "gradlew.bat") @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "Gradle failed: $($Arguments -join ' ')"
    }
}

function Copy-Artifacts([string]$Platform, [string[]]$Patterns) {
    $destination = Join-Path $ArtifactsRoot $Platform
    New-Item -ItemType Directory -Force -Path $destination | Out-Null
    $files = Get-ChildItem -Path (Join-Path $RepoRoot "build\\distributions") -File |
        Where-Object {
            $name = $_.Name
            $null -ne ($Patterns | Where-Object { $name -like $_ } | Select-Object -First 1)
        }
    if (-not $files) {
        throw "No $Platform package was produced in build\\distributions."
    }
    $files | Copy-Item -Destination $destination -Force
}

Push-Location $RepoRoot
try {
    if (-not (Test-Command "winget")) {
        throw "winget is required to install build dependencies. Install Microsoft App Installer, then run this script again."
    }

    if (-not (Test-Command "git")) {
        Install-WingetPackage "Git.Git"
    }
    Add-GitToolsToPath
    if (-not (Test-Command "zip") -or -not (Test-Command "unzip")) {
        throw "Git for Windows must provide zip.exe and unzip.exe. Close this PowerShell window, open a new one, then run the script again."
    }

    if (-not (Test-Command "iscc")) {
        Install-WingetPackage "JRSoftware.InnoSetup"
    }
    if (-not (Test-Command "iscc")) {
        $innoCompiler = if (${env:ProgramFiles(x86)}) { Join-Path ${env:ProgramFiles(x86)} "Inno Setup 6\\ISCC.exe" }
        if ($innoCompiler -and (Test-Path $innoCompiler)) {
            $env:Path = "$(Split-Path $innoCompiler);$env:Path"
        }
    }
    if (-not (Test-Command "iscc")) {
        throw "Inno Setup was installed but ISCC.exe is not available on PATH. Open a new PowerShell window and retry."
    }

    if (-not $SkipLinux -and -not (Test-Command "docker")) {
        Install-WingetPackage "Docker.DockerDesktop"
    }
    if (-not $SkipLinux -and -not (Test-Command "docker")) {
        throw "Docker Desktop was installed but is not available yet. Start Docker Desktop, complete its first-run setup, then retry."
    }

    $javaHome = Get-JbrHome $JbrHome
    $env:JAVA_HOME = $javaHome
    $env:Path = "$(Join-Path $javaHome 'bin');$env:Path"
    & (Join-Path $javaHome "bin\\java.exe") -version

    if ($InstallOnly) {
        Write-Host "Build environment is ready. JAVA_HOME=$javaHome"
        exit 0
    }

    if (-not $SkipWindows) {
        Write-Host "Building Windows packages ..."
        Invoke-Gradle @("clean", ":check-license", "classes", "-x", "test", ":jar", ":copy-dependencies", ":plugins:migration:build", ":jlink", ":jpackage", ":dist")
        Copy-Artifacts "windows" @("*.exe", "*.zip")
    }

    if (-not $SkipLinux) {
        Write-Host "Building Linux packages in Docker ..."
        & docker info *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Docker Desktop is not running. Start it, then rerun this script."
        }
        & docker run --rm -v "${RepoRoot}:/app" -w /app -e "TERMORA_TYPE=deb" $LinuxImage bash -lc "./gradlew clean :check-license classes -x test :jar :copy-dependencies :plugins:migration:build :jlink :jpackage :dist"
        if ($LASTEXITCODE -ne 0) {
            throw "Linux Gradle build failed."
        }
        Copy-Artifacts "linux" @("*.deb")
    }

    Write-Host "Packages were written to $ArtifactsRoot"
}
finally {
    Pop-Location
}
