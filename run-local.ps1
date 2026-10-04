# One-time setup (Windows PowerShell):
#   Copy-Item .env.example .env.local
#   # Fill in the private values.
#   .\run-local.ps1

$ErrorActionPreference = 'Stop'

$RideLinkRoot = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($RideLinkRoot)) {
    $RideLinkRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
}

$PreferredEnvironmentFile = Join-Path $RideLinkRoot '.env.local'
$LegacyEnvironmentFile = Join-Path $RideLinkRoot '.env'
$LogDirectory = Join-Path $RideLinkRoot '.logs'
$RequiredVariables = @(
    'ACCOUNT_MONGODB_URI',
    'DRIVER_MONGODB_URI',
    'RIDE_MONGODB_URI',
    'FARE_PAYMENT_MONGODB_URI',
    'JWT_SECRET',
    'INTERNAL_SERVICE_KEY'
)

if (Test-Path -LiteralPath $PreferredEnvironmentFile -PathType Leaf) {
    $EnvironmentFile = $PreferredEnvironmentFile
    $EnvironmentFileName = '.env.local'
}
elseif (Test-Path -LiteralPath $LegacyEnvironmentFile -PathType Leaf) {
    $EnvironmentFile = $LegacyEnvironmentFile
    $EnvironmentFileName = '.env'
    Write-Host '.env.local not found; using legacy .env'
}
else {
    Write-Error "No local environment file found.`nCreate .env.local from .env.example."
    exit 1
}

$Configuration = @{}
$LineNumber = 0
foreach ($RawLine in [System.IO.File]::ReadAllLines($EnvironmentFile)) {
    $LineNumber++
    $Line = $RawLine.Trim()

    if ($Line.Length -eq 0 -or $Line.StartsWith('#')) {
        continue
    }

    $EqualsIndex = $Line.IndexOf('=')
    if ($EqualsIndex -lt 1) {
        Write-Error "Invalid $EnvironmentFileName entry at line $LineNumber. Expected KEY=value."
        exit 1
    }

    $Key = $Line.Substring(0, $EqualsIndex).Trim()
    $Value = $Line.Substring($EqualsIndex + 1).Trim()

    if ($Key -notmatch '^[A-Za-z_][A-Za-z0-9_]*$') {
        Write-Error "Invalid variable name in $EnvironmentFileName at line $LineNumber."
        exit 1
    }

    if ($Value.Length -ge 2) {
        $FirstCharacter = $Value.Substring(0, 1)
        $LastCharacter = $Value.Substring($Value.Length - 1, 1)
        if (($FirstCharacter -eq "'" -and $LastCharacter -eq "'") -or
            ($FirstCharacter -eq '"' -and $LastCharacter -eq '"')) {
            $Value = $Value.Substring(1, $Value.Length - 2)
        }
    }

    $Configuration[$Key] = $Value
}

if ((-not $Configuration.ContainsKey('FARE_PAYMENT_MONGODB_URI') -or
     [string]::IsNullOrWhiteSpace([string]$Configuration['FARE_PAYMENT_MONGODB_URI'])) -and
    $Configuration.ContainsKey('PAYMENT_MONGODB_URI') -and
    -not [string]::IsNullOrWhiteSpace([string]$Configuration['PAYMENT_MONGODB_URI'])) {
    $Configuration['FARE_PAYMENT_MONGODB_URI'] = $Configuration['PAYMENT_MONGODB_URI']
    Write-Warning 'Using legacy PAYMENT_MONGODB_URI; prefer FARE_PAYMENT_MONGODB_URI.'
}

$PortDefaults = @{
    ACCOUNT_PORT = '8081'
    DRIVER_PORT = '8082'
    RIDE_PORT = '8083'
    PAYMENT_PORT = '8084'
}
foreach ($PortName in $PortDefaults.Keys) {
    if (-not $Configuration.ContainsKey($PortName) -or
        [string]::IsNullOrWhiteSpace([string]$Configuration[$PortName])) {
        $Configuration[$PortName] = $PortDefaults[$PortName]
    }

    $ParsedPort = 0
    if (-not [int]::TryParse([string]$Configuration[$PortName], [ref]$ParsedPort) -or
        $ParsedPort -lt 1 -or $ParsedPort -gt 65535) {
        Write-Error "$PortName must be an integer from 1 to 65535."
        exit 1
    }
    $Configuration[$PortName] = $ParsedPort.ToString()
}

$MissingVariables = @(
    foreach ($VariableName in $RequiredVariables) {
        if (-not $Configuration.ContainsKey($VariableName) -or
            [string]::IsNullOrWhiteSpace([string]$Configuration[$VariableName])) {
            $VariableName
        }
    }
)

if ($MissingVariables.Count -gt 0) {
    foreach ($VariableName in $MissingVariables) {
        Write-Error "Missing $VariableName in $EnvironmentFileName."
    }
    exit 1
}

$MavenCommand = Get-Command 'mvn.cmd' -ErrorAction SilentlyContinue
if ($null -eq $MavenCommand) {
    $MavenCommand = Get-Command 'mvn' -ErrorAction SilentlyContinue
}
if ($null -eq $MavenCommand) {
    Write-Error 'Maven (mvn) was not found on PATH.'
    exit 1
}

$ServiceDirectories = @(
    'account-service',
    'driver-and-vehicle-service',
    'fare-and-payment-service',
    'ride-management-service'
)
foreach ($ServiceDirectory in $ServiceDirectories) {
    $PomPath = Join-Path (Join-Path $RideLinkRoot $ServiceDirectory) 'pom.xml'
    if (-not (Test-Path -LiteralPath $PomPath -PathType Leaf)) {
        Write-Error "Expected service not found: $ServiceDirectory"
        exit 1
    }
}

New-Item -ItemType Directory -Path $LogDirectory -Force | Out-Null

$ChildPowerShell = (Get-Process -Id $PID).Path
$MavenPath = $MavenCommand.Source
$RideLinkProcesses = @()
$IsolatedEnvironmentNames = @(
    'ACCOUNT_MONGODB_URI',
    'DRIVER_MONGODB_URI',
    'RIDE_MONGODB_URI',
    'FARE_PAYMENT_MONGODB_URI',
    'PAYMENT_MONGODB_URI',
    'JWT_SECRET',
    'INTERNAL_SERVICE_KEY',
    'ACCOUNT_PORT',
    'DRIVER_PORT',
    'RIDE_PORT',
    'PAYMENT_PORT',
    'MONGODB_URI',
    'SERVER_PORT',
    'DRIVER_SERVICE_URL',
    'FARE_PAYMENT_SERVICE_URL'
)

function Start-RideLinkService {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Directory,
        [Parameter(Mandatory = $true)][string]$LogName,
        [Parameter(Mandatory = $true)][hashtable]$Environment
    )

    $WorkingDirectory = Join-Path $RideLinkRoot $Directory
    $LogPath = Join-Path $LogDirectory $LogName
    [System.IO.File]::WriteAllText($LogPath, '')

    $OriginalEnvironment = @{}
    try {
        foreach ($EnvironmentName in $IsolatedEnvironmentNames) {
            $OriginalEnvironment[$EnvironmentName] =
                [Environment]::GetEnvironmentVariable($EnvironmentName, 'Process')
            [Environment]::SetEnvironmentVariable($EnvironmentName, $null, 'Process')
        }

        foreach ($Entry in $Environment.GetEnumerator()) {
            [Environment]::SetEnvironmentVariable($Entry.Key, [string]$Entry.Value, 'Process')
        }

        $EscapedMavenPath = $MavenPath.Replace("'", "''")
        $EscapedLogPath = $LogPath.Replace("'", "''")
        $ChildCommand = @"
& '$EscapedMavenPath' spring-boot:run *> '$EscapedLogPath'
exit `$LASTEXITCODE
"@
        $EncodedCommand = [Convert]::ToBase64String([Text.Encoding]::Unicode.GetBytes($ChildCommand))

        $Process = Start-Process `
            -FilePath $ChildPowerShell `
            -ArgumentList @('-NoLogo', '-NoProfile', '-NonInteractive', '-EncodedCommand', $EncodedCommand) `
            -WorkingDirectory $WorkingDirectory `
            -NoNewWindow `
            -PassThru

        $script:RideLinkProcesses += [PSCustomObject]@{
            Name = $Name
            Process = $Process
        }
    }
    finally {
        foreach ($EnvironmentName in $IsolatedEnvironmentNames) {
            [Environment]::SetEnvironmentVariable(
                $EnvironmentName,
                $OriginalEnvironment[$EnvironmentName],
                'Process'
            )
        }
    }
}

function Stop-RideLinkServices {
    if ($script:RideLinkProcesses.Count -eq 0) {
        return
    }

    Write-Host ''
    Write-Host 'Stopping RideLink services...'

    $TaskKill = Get-Command 'taskkill.exe' -ErrorAction SilentlyContinue
    foreach ($Record in $script:RideLinkProcesses) {
        try {
            $Record.Process.Refresh()
            if (-not $Record.Process.HasExited) {
                if ($null -ne $TaskKill) {
                    & $TaskKill.Source /PID $Record.Process.Id /T /F 2>$null | Out-Null
                }
                else {
                    Stop-Process -Id $Record.Process.Id -Force -ErrorAction SilentlyContinue
                }
            }
        }
        catch {
            # The process may already have stopped between the status check and cleanup.
        }
    }

    Write-Host 'All RideLink services stopped.'
}

try {
    Start-RideLinkService `
        -Name 'Account Service' `
        -Directory 'account-service' `
        -LogName 'account.log' `
        -Environment @{
            MONGODB_URI = $Configuration['ACCOUNT_MONGODB_URI']
            JWT_SECRET = $Configuration['JWT_SECRET']
            SERVER_PORT = $Configuration['ACCOUNT_PORT']
        }

    Start-RideLinkService `
        -Name 'Driver & Vehicle Service' `
        -Directory 'driver-and-vehicle-service' `
        -LogName 'driver.log' `
        -Environment @{
            MONGODB_URI = $Configuration['DRIVER_MONGODB_URI']
            JWT_SECRET = $Configuration['JWT_SECRET']
            INTERNAL_SERVICE_KEY = $Configuration['INTERNAL_SERVICE_KEY']
            SERVER_PORT = $Configuration['DRIVER_PORT']
        }

    Start-RideLinkService `
        -Name 'Fare & Payment Service' `
        -Directory 'fare-and-payment-service' `
        -LogName 'fare-payment.log' `
        -Environment @{
            MONGODB_URI = $Configuration['FARE_PAYMENT_MONGODB_URI']
            SERVER_PORT = $Configuration['PAYMENT_PORT']
        }

    Start-RideLinkService `
        -Name 'Ride Management Service' `
        -Directory 'ride-management-service' `
        -LogName 'ride.log' `
        -Environment @{
            MONGODB_URI = $Configuration['RIDE_MONGODB_URI']
            INTERNAL_SERVICE_KEY = $Configuration['INTERNAL_SERVICE_KEY']
            SERVER_PORT = $Configuration['RIDE_PORT']
            DRIVER_SERVICE_URL = "http://localhost:$($Configuration['DRIVER_PORT'])"
            FARE_PAYMENT_SERVICE_URL = "http://localhost:$($Configuration['PAYMENT_PORT'])"
        }

    Write-Host @"
RideLink local backend starting...

Account Service           http://localhost:$($Configuration['ACCOUNT_PORT'])
Driver & Vehicle Service  http://localhost:$($Configuration['DRIVER_PORT'])
Ride Management Service   http://localhost:$($Configuration['RIDE_PORT'])
Fare & Payment Service    http://localhost:$($Configuration['PAYMENT_PORT'])

Using environment file: $EnvironmentFileName

Logs:
.logs/account.log
.logs/driver.log
.logs/ride.log
.logs/fare-payment.log

Press Ctrl+C to stop all RideLink services.
"@

    while ($true) {
        foreach ($Record in $RideLinkProcesses) {
            $Record.Process.Refresh()
            if ($Record.Process.HasExited) {
                throw "$($Record.Name) stopped (exit code $($Record.Process.ExitCode)). Check its log for details."
            }
        }
        Start-Sleep -Seconds 1
    }
}
finally {
    Stop-RideLinkServices
}
