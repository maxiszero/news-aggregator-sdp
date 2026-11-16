param(
    [ValidateRange(0, 65535)] [int] $Port = 8080,
    [switch] $Test,
    [switch] $Web
)

$ErrorActionPreference = 'Stop'
$classes = Join-Path $PSScriptRoot 'build/classes'
$resources = Join-Path $PSScriptRoot 'src/main/resources'
$sources = @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/main/java') -Recurse -Filter '*.java' -File |
    ForEach-Object FullName)
if ($Test) {
    $sources += @(Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot 'src/test/java') -Recurse -Filter '*.java' -File |
        ForEach-Object FullName)
}

$null = New-Item -Path $classes -ItemType Directory -Force
& javac --release 17 -encoding UTF-8 -Xlint:all -d $classes @sources
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$classpath = $classes + [System.IO.Path]::PathSeparator + $resources
if ($Test) {
    & java -cp $classpath newsaggregator.ProjectTest
} else {
    $serverArguments = @([string] $Port)
    if ($Web) { $serverArguments += '--web' }
    & java -cp $classpath newsaggregator.Main @serverArguments
}
exit $LASTEXITCODE
