param([switch] $Web, [switch] $NoBrowser)

$ErrorActionPreference = 'Stop'
$browserOpened = $false
if ($Web) {
    Write-Host 'Starting The Signal with the web interface. Keep this window open.'
} else {
    Write-Host 'Starting The Signal API. Keep this window open.'
}
Write-Host 'Press Ctrl+C or close this window to stop the server.'

try {
    & (Join-Path $PSScriptRoot 'run.ps1') -Port 0 -Web:$Web | ForEach-Object {
        $line = [string] $_
        Write-Host $line
        if (!$browserOpened -and $line -match '^The Signal API is running at (http://127\.0\.0\.1:\d+/)$') {
            $browserOpened = $true
            $baseUrl = $Matches[1]
            $launchUrl = if ($Web) { $baseUrl } else { $baseUrl + 'api/feed' }
            Write-Host "Open: $launchUrl"
            Write-Host "API routes: $($baseUrl)api"
            if (!$NoBrowser) {
                try {
                    Start-Process $launchUrl
                } catch {
                    Write-Warning "Open this address in your browser: $launchUrl"
                }
            }
        }
    }
    $result = $LASTEXITCODE
    exit $result
} catch {
    Write-Host $_.Exception.Message -ForegroundColor Red
    Write-Host 'The project needs JDK 17 or newer, with java and javac available in PATH.'
    exit 1
}
