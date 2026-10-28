# Tạo người dùng bằng CLI của backend (mật khẩu băm Argon2id).
# Ví dụ: ./ops/scripts/create-user.ps1 -Email a@congty.vn -Name "Nguyễn Văn A" -Role member -Temp
param(
    [Parameter(Mandatory)] [string] $Email,
    [Parameter(Mandatory)] [string] $Name,
    [ValidateSet('member', 'lead', 'admin')] [string] $Role = 'member',
    [string] $Password,
    [switch] $Temp
)
. (Join-Path $PSScriptRoot '_env.ps1')
Import-DotEnv
Use-Jdk21
if (-not $Password) {
    # Hỏi mật khẩu khi không truyền vào để không lưu trong lịch sử shell
    $secure = Read-Host -AsSecureString 'Mật khẩu'
    $Password = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
}
$cliArgs = @('--spring.profiles.active=cli', 'create-user', '--email', $Email, '--role', $Role)
if ($Temp) { $cliArgs += '--temp' }
# Tên và mật khẩu qua biến môi trường: JVM trên Windows làm hỏng tiếng Việt trong đối số dòng lệnh,
# và mật khẩu không lộ trong danh sách tiến trình.
$env:CLI_NAME = $Name
$env:CLI_PASSWORD = $Password

# Chạy jar trực tiếp bằng java.exe (mvn.cmd không truyền được đối số có khoảng trắng/ngoặc kép).
$jar = Join-Path $RepoRoot 'api\target\api.jar'
Invoke-Maven -B -q -DskipTests package
try {
    & (Join-Path $env:JAVA_HOME 'bin\java.exe') '-Dfile.encoding=UTF-8' '-Dstdout.encoding=UTF-8' -jar $jar @cliArgs
    if ($LASTEXITCODE -ne 0) { throw "Tạo người dùng thất bại (exit $LASTEXITCODE)" }
} finally {
    Remove-Item Env:CLI_NAME, Env:CLI_PASSWORD -ErrorAction SilentlyContinue
}
