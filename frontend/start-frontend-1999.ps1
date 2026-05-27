Set-Location -LiteralPath $PSScriptRoot

$nextDir = Join-Path $PSScriptRoot ".next"
if (Test-Path -LiteralPath $nextDir) {
  Remove-Item -LiteralPath $nextDir -Recurse -Force
}

npm run dev *> (Join-Path $PSScriptRoot "next-1999.live.log")
