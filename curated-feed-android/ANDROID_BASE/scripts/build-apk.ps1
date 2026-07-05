param(
    [ValidateSet("Debug", "Release")]
    [string]$Variant = "Debug",

    [string[]]$GradleArgs = @()
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

function Test-JavaHome {
    param([string]$Path)
    return -not [string]::IsNullOrWhiteSpace($Path) -and (Test-Path (Join-Path $Path "bin\java.exe"))
}

function Find-JavaHome {
    if (Test-JavaHome $env:JAVA_HOME) {
        return $env:JAVA_HOME
    }

    $javaCommand = Get-Command java.exe -ErrorAction SilentlyContinue
    if ($javaCommand) {
        return $null
    }

    $candidates = @(
        "$env:ProgramFiles\Android\Android Studio\jbr",
        "$env:ProgramFiles\Android\Android Studio\jre",
        "$env:ProgramFiles\Eclipse Adoptium",
        "$env:ProgramFiles\Java",
        "${env:ProgramFiles(x86)}\Java"
    )

    foreach ($candidate in $candidates) {
        if (Test-JavaHome $candidate) {
            return $candidate
        }

        if (Test-Path $candidate) {
            $found = Get-ChildItem -Path $candidate -Directory -ErrorAction SilentlyContinue |
                Where-Object { Test-JavaHome $_.FullName } |
                Sort-Object Name -Descending |
                Select-Object -First 1

            if ($found) {
                return $found.FullName
            }
        }
    }

    throw "Nenhum JDK encontrado. Instale JDK 17+ ou configure JAVA_HOME apontando para a pasta do JDK."
}

$detectedJavaHome = Find-JavaHome
if ($detectedJavaHome) {
    $env:JAVA_HOME = $detectedJavaHome
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}

Write-Host "Java em uso:"
& java.exe -version

$task = "assemble$Variant"
Write-Host "Executando Gradle task: $task"
& .\gradlew.bat $task @GradleArgs

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

$apk = Join-Path $root "app\build\outputs\apk\$($Variant.ToLowerInvariant())\app-$($Variant.ToLowerInvariant()).apk"
if (Test-Path $apk) {
    Write-Host "APK gerado: $apk"
}
