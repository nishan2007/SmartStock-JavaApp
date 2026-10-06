param([Parameter(Mandatory=$true)][string]$Destination, [switch]$NoticesOnly)
$ErrorActionPreference = 'Stop'
New-Item -ItemType Directory -Force -Path $Destination | Out-Null
$models = @(
    @{ Name='isnet-general-use.onnx'; Cache='smartstock-isnet-general-use.onnx'; Hash='60920e99c45464f2ba57bee2ad08c919a52bbf852739e96947fbb4358c0d964a'; Url='https://huggingface.co/skillsafe-ai/isnet-general-use/resolve/main/isnet-general-use.onnx' },
    @{ Name='birefnet-general.onnx'; Cache='smartstock-birefnet-general.onnx'; Hash='58f621f00f5d756097615970a88a791584600dcf7c45b18a0a6267535a1ebd3c'; Url='https://github.com/danielgatis/rembg/releases/download/v0.0.0/BiRefNet-general-epoch_244.onnx' }
)
if (-not $NoticesOnly) { foreach ($model in $models) {
    $cachedModel = Join-Path $env:TEMP $model.Cache
    if (-not (Test-Path -LiteralPath $cachedModel) -or (Get-FileHash -LiteralPath $cachedModel -Algorithm SHA256).Hash.ToLowerInvariant() -ne $model.Hash) {
        $partialModel = "$cachedModel.partial"
        Invoke-WebRequest -Uri $model.Url -OutFile $partialModel
        if ((Get-FileHash -LiteralPath $partialModel -Algorithm SHA256).Hash.ToLowerInvariant() -ne $model.Hash) {
            throw "The $($model.Name) photo model failed its SHA-256 check."
        }
        Move-Item -LiteralPath $partialModel -Destination $cachedModel -Force
    }
    Copy-Item -LiteralPath $cachedModel -Destination (Join-Path $Destination $model.Name)
} }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'catalog-studio-model-NOTICE.txt') -Destination $Destination
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'birefnet-LICENSE.txt') -Destination $Destination
