# Bật PostgreSQL 16 + pgvector bằng Docker (cách a) và chờ sẵn sàng.
# Nếu dùng PostgreSQL cài trực tiếp (cách b) thì không cần script này — xem README.
. (Join-Path $PSScriptRoot '_env.ps1')
Import-DotEnv
Invoke-Checked { docker compose -f (Join-Path $RepoRoot 'ops\docker-compose.yml') up -d --wait db }
Write-Host 'PostgreSQL sẵn sàng tại localhost:5432 (DB chatdoc).'
