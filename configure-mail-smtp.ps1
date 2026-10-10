param(
    [string]$Username
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Username)) {
    $Username = Read-Host 'Gmail address that owns the app password'
}
if ($Username -notmatch '^[^\s@]+@[^\s@]+\.[^\s@]+$') {
    throw 'Enter a valid Gmail address.'
}

$securePassword = Read-Host 'Gmail app password (spaces are allowed)' -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $appPassword = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer) -replace '\s+', ''
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
}

if ($appPassword.Length -lt 16) {
    throw 'The Gmail app password is incomplete. Generate a new 16-character app password and try again.'
}

[Environment]::SetEnvironmentVariable('MAIL_USERNAME', $Username.Trim(), 'User')
[Environment]::SetEnvironmentVariable('MAIL_PASSWORD', $appPassword, 'User')
Remove-Variable appPassword -ErrorAction SilentlyContinue

Write-Host ''
Write-Host 'Mail credentials were saved for the current Windows user.' -ForegroundColor Green
Write-Host 'Restart the Exam Platform, then use the admin mail health check before sending queued mail.'
