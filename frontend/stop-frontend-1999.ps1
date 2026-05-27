$connections = Get-NetTCPConnection -LocalPort 1999 -ErrorAction SilentlyContinue
$processIds = $connections |
  Where-Object { $_.OwningProcess -and $_.OwningProcess -ne 0 } |
  Select-Object -ExpandProperty OwningProcess -Unique

foreach ($processId in $processIds) {
  Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
}
