# Chạy backend tại http://localhost:8080 (cần CSDL đang chạy: ./ops/scripts/up.ps1).
. (Join-Path $PSScriptRoot '_env.ps1')
Import-DotEnv
Use-Jdk21
Invoke-Maven -B spring-boot:run
