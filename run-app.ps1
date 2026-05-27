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
    [int] $TimeoutSeconds = 45
  )

  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  do {
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

Stop-PortProcess -Port 1999
Stop-PortProcess -Port 2000
Start-Sleep -Seconds 2

$nextDir = Join-Path $frontend ".next"
if (Test-Path -LiteralPath $nextDir) {
  Remove-Item -LiteralPath $nextDir -Recurse -Force
}

Start-Process -FilePath $maven `
  -ArgumentList "spring-boot:run" `
  -WorkingDirectory $backend `
  -RedirectStandardOutput (Join-Path $backend "backend-run.out.log") `
  -RedirectStandardError (Join-Path $backend "backend-run.err.log") `
  -WindowStyle Hidden

Start-Process -FilePath "npm.cmd" `
  -ArgumentList "run", "dev" `
  -WorkingDirectory $frontend `
  -RedirectStandardOutput (Join-Path $frontend "next-1999.live.log") `
  -RedirectStandardError (Join-Path $frontend "next-1999.err.log") `
  -WindowStyle Hidden

Wait-HttpOk -Url "http://localhost:2000/"
Wait-HttpOk -Url "http://localhost:1999/"

Write-Host "JagdiSu is running:"
Write-Host "Frontend: http://localhost:1999"
Write-Host "Backend:  http://localhost:2000"
