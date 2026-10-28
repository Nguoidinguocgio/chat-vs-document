# Chạy toàn bộ test như CI: web (vitest) + api (./mvnw verify, cần Docker cho Testcontainers).
. (Join-Path $PSScriptRoot '_env.ps1')
Use-Jdk21
Set-Location (Join-Path $RepoRoot 'web')
Invoke-Checked { npm test }
Invoke-Maven -B verify
