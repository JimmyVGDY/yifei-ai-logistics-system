@echo off
powershell -NoProfile -ExecutionPolicy Bypass -Command "try { Invoke-RestMethod -Uri 'http://127.0.0.1:6333/readyz' -TimeoutSec 3; Write-Host 'Qdrant OK http://127.0.0.1:6333' } catch { Write-Host 'Qdrant not ready:' $_.Exception.Message; exit 1 }"
