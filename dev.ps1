[CmdletBinding(PositionalBinding=$false)]
param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not (Get-Command wsl.exe -ErrorAction SilentlyContinue)) { throw 'WSL is required.' }
function Convert-ToWslPath([string]$Path) {
    $full = [IO.Path]::GetFullPath($Path)
    if ($full -notmatch '^([A-Za-z]):\\(.*)$') { throw "Cannot convert path to WSL form: $full" }
    return '/mnt/' + $Matches[1].ToLowerInvariant() + '/' + $Matches[2].Replace('\','/')
}
$linuxRoot = Convert-ToWslPath $projectRoot
& wsl.exe bash "$linuxRoot/dev.sh" @Arguments
exit $LASTEXITCODE
