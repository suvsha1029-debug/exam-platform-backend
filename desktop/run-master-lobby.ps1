$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$project = Join-Path $root 'src\MasterAdmin.ExamLobby\MasterAdmin.ExamLobby.csproj'
dotnet run --project $project
