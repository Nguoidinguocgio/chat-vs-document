# Lint + kiểm tra kiểu web; Spotless + Checkstyle api (không chạy test).
. (Join-Path $PSScriptRoot '_env.ps1')
Use-Jdk21
Set-Location (Join-Path $RepoRoot 'web')
Invoke-Checked { npm run lint }
Invoke-Checked { npm run typecheck }
Invoke-Maven -B -q spotless:check checkstyle:check
