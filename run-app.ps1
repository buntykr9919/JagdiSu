$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$frontend = Join-Path $root "frontend"
$backend = Join-Path $root "backend"
$maven = Join-Path $root "apache-maven-3.9.11\bin\mvn.cmd"

function Stop-PortProcess {
  param([int] $Port)

  $connections = Get-NetTCPConnection -LocalPort $Port -ErrorAction SilentlyContinue
  $processIds = $connections |
    Where-Object { $_.OwningProcess -and $_.OwningProcess -ne 0 } |
    Select-Object -ExpandProperty OwningProcess -Unique

  foreach ($processId in $processIds) {
    Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
  }
}

function Wait-HttpOk {
  param(
    [string] $Url,
    [int] $TimeoutSeconds = 45,
    [System.Diagnostics.Process] $Process = $null
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    if ($Process -and $Process.HasExited) {
      throw "Process exited while waiting for $Url"
    }

    try {
      $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
      if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
        return
      }
    } catch {
      Start-Sleep -Milliseconds 800
    }
  } while ((Get-Date) -lt $deadline)

  throw "Timed out waiting for $Url"
}

function Show-RecentLog {
  param(
    [string] $Path,
    [int] $Lines = 80
  )

  if (Test-Path -LiteralPath $Path) {
    Write-Host ""
    Write-Host "Last $Lines lines from $Path"
    Get-Content -LiteralPath $Path -Tail $Lines
  }
}

function Start-MySqlIfAvailable {
  $mysqlService = Get-Service -ErrorAction SilentlyContinue |
    Where-Object { $_.Name -like "MySQL*" -or $_.DisplayName -like "MySQL*" } |
    Select-Object -First 1

  if (-not $mysqlService) {
    Write-Host "MySQL service not found. If backend fails, install/start MySQL and check backend/.env."
    return
  }

  if ($mysqlService.Status -ne "Running") {
    Write-Host "Starting MySQL service: $($mysqlService.Name)"
    try {
      Start-Service -Name $mysqlService.Name
    } catch {
      Write-Host "Could not start MySQL service automatically. Start $($mysqlService.Name) manually, then run this script again."
      throw
    }
  }
}

function Wait-TcpPort {
  param(
    [string] $HostName,
    [int] $Port,
    [int] $TimeoutSeconds = 45
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
    $connection = Test-NetConnection -ComputerName $HostName -Port $Port -WarningAction SilentlyContinue
    if ($connection.TcpTestSucceeded) {
      return
    }
    Start-Sleep -Milliseconds 800
  } while ((Get-Date) -lt $deadline)

  throw "Timed out waiting for $HostName`:$Port"
}

Start-MySqlIfAvailable
Wait-TcpPort -HostName "localhost" -Port 3306

Stop-PortProcess -Port 1999
Stop-PortProcess -Port 2000
Start-Sleep -Seconds 2

$nextDir = Join-Path $frontend ".next"
if (Test-Path -LiteralPath $nextDir) {
  Remove-Item -LiteralPath $nextDir -Recurse -Force
}

$backendOutLog = Join-Path $backend "backend-run.out.log"
$backendErrLog = Join-Path $backend "backend-run.err.log"
$frontendOutLog = Join-Path $frontend "next-1999.live.log"
$frontendErrLog = Join-Path $frontend "next-1999.err.log"

$backendProcess = Start-Process -FilePath $maven `
  -ArgumentList "spring-boot:run" `
  -WorkingDirectory $backend `
  -RedirectStandardOutput $backendOutLog `
  -RedirectStandardError $backendErrLog `
  -WindowStyle Hidden `
  -PassThru

try {
  Wait-HttpOk -Url "http://localhost:2000/" -Process $backendProcess
  Wait-HttpOk -Url "http://localhost:2000/api/health" -Process $backendProcess
} catch {
  Write-Host ""
  Write-Host "Backend did not start on http://localhost:2000."
  Show-RecentLog -Path $backendErrLog
  Show-RecentLog -Path $backendOutLog
  throw
}

Start-Process -FilePath "npm.cmd" `
  -ArgumentList "run", "dev" `
  -WorkingDirectory $frontend `
  -RedirectStandardOutput $frontendOutLog `
  -RedirectStandardError $frontendErrLog `
  -WindowStyle Hidden

Wait-HttpOk -Url "http://localhost:1999/"

Write-Host "JagdiSu is running:"
Write-Host "Frontend: http://localhost:1999"
Write-Host "Backend:  http://localhost:2000"
