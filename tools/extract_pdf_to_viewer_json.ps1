param(
  [Parameter(Mandatory = $true)]
  [string]$Pdf,

  [Parameter(Mandatory = $true)]
  [string]$Out,

  [ValidateSet("viewer", "pre-git")]
  [string]$Format = "viewer",

  [string]$Profile = "auto",

  [string]$Subject = "GEN",

  [int]$Dpi = 200,

  [string]$PopplerPath = ""
)

$repoRoot = Split-Path -Parent $PSScriptRoot
$scriptPath = Join-Path $repoRoot "tools\pdf_structured_extract.py"

if (-not (Test-Path -LiteralPath $scriptPath)) {
  throw "Extractor script not found: $scriptPath"
}

$argsList = @(
  $scriptPath,
  "--pdf", $Pdf,
  "--out", $Out,
  "--format", $Format,
  "--profile", $Profile,
  "--dpi", $Dpi.ToString(),
  "--subject", $Subject
)

if ($PopplerPath) {
  $argsList += @("--poppler-path", $PopplerPath)
}

python $argsList
