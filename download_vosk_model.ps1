# Download Vosk small English model and place in app/src/main/assets/model-en-us/
# Run from project root: powershell -ExecutionPolicy Bypass -File download_vosk_model.ps1

$url = "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip"
$zipPath = "$env:TEMP\vosk-model-small-en-us-0.15.zip"
$assetsModel = "app\src\main\assets\model-en-us"
$extractRoot = "$env:TEMP\vosk-model-extract"

Write-Host "Downloading Vosk model (~40 MB)..."
$ProgressPreference = 'SilentlyContinue'
Invoke-WebRequest -Uri $url -OutFile $zipPath -UseBasicParsing

Write-Host "Extracting..."
if (Test-Path $extractRoot) { Remove-Item -Recurse -Force $extractRoot }
Expand-Archive -Path $zipPath -DestinationPath $extractRoot -Force

$innerFolder = Get-ChildItem $extractRoot -Directory | Select-Object -First 1
if (-not $innerFolder) {
    Write-Host "ERROR: No folder inside zip."
    exit 1
}

if (Test-Path $assetsModel) { Remove-Item -Recurse -Force $assetsModel }
New-Item -ItemType Directory -Path $assetsModel -Force
Copy-Item -Path "$($innerFolder.FullName)\*" -Destination $assetsModel -Recurse -Force

Remove-Item $zipPath -Force -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force $extractRoot -ErrorAction SilentlyContinue

Write-Host "Done. Model is in $assetsModel"
Write-Host "Rebuild the app and voice recognition will use the bundled model."
