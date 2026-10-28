# Dùng chung cho các script: nạp ../../.env vào biến môi trường của tiến trình và chọn JDK 21+.
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path

function Import-DotEnv {
    $file = Join-Path $RepoRoot '.env'
    if (-not (Test-Path $file)) { return }
    foreach ($line in Get-Content $file -Encoding UTF8) {
        if ($line -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*)$') {
            # Biến đã đặt sẵn trong phiên shell được ưu tiên hơn .env
            if (-not (Test-Path "Env:$($Matches[1])")) {
                Set-Item -Path "Env:$($Matches[1])" -Value $Matches[2].Trim()
            }
        }
    }
}

function Get-JavaMajor($javaHome) {
    if (-not $javaHome) { return 0 }
    $exe = Join-Path $javaHome 'bin\java.exe'
    if (-not (Test-Path $exe)) { return 0 }
    # java -version ghi ra stderr; ở PowerShell 5.1 điều đó thành lỗi nếu giữ 'Stop'
    $ErrorActionPreference = 'Continue'
    $line = (& $exe -version 2>&1 | Select-Object -First 1).ToString()
    if ($line -match '"(\d+)') { return [int]$Matches[1] }
    return 0
}

function Use-Jdk21 {
    if ((Get-JavaMajor $env:JAVA_HOME) -ge 21) { return }
    $roots = @('D:\Program Files', 'C:\Program Files\Java', 'C:\Program Files\Eclipse Adoptium', 'C:\Program Files\Microsoft') |
        Where-Object { Test-Path $_ }
    $found = $roots | ForEach-Object { Get-ChildItem $_ -Directory -Filter 'jdk-2*' } |
        Where-Object { (Get-JavaMajor $_.FullName) -ge 21 } | Select-Object -First 1
    if (-not $found) { throw 'Không tìm thấy JDK 21+. Cài JDK 21 và đặt JAVA_HOME.' }
    $env:JAVA_HOME = $found.FullName
    Write-Host "Dùng JDK: $($env:JAVA_HOME)"
}

function Invoke-Checked([scriptblock]$block) {
    & $block
    if ($LASTEXITCODE -ne 0) { throw "Lệnh thất bại (exit $LASTEXITCODE)" }
}

# Maven trong thư mục api/: dùng mvn trên PATH nếu có (cần >= 3.6.3), nếu không thì Maven Wrapper.
function Invoke-Maven {
    $mvn = Get-Command mvn -ErrorAction SilentlyContinue
    Push-Location (Join-Path $RepoRoot 'api')
    try {
        if ($mvn) { & $mvn.Source @args } else { & .\mvnw.cmd @args }
        if ($LASTEXITCODE -ne 0) { throw "Maven thất bại (exit $LASTEXITCODE)" }
    } finally {
        Pop-Location
    }
}
