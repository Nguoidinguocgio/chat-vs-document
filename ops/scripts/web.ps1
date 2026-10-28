# Chạy frontend tại http://localhost:5173 (Node 20+). Vite proxy /api và /healthz sang http://localhost:8080
. (Join-Path $PSScriptRoot '_env.ps1')
Import-DotEnv
Set-Location (Join-Path $RepoRoot 'web')
if (-not (Test-Path node_modules)) { Invoke-Checked { npm ci } }
Invoke-Checked { npm run dev }
