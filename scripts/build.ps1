param(
    [string]$Jdk25 = $env:JAVA_HOME,
    [switch]$FullRecovery
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
if (!$Jdk25) {
    $localJdk = Join-Path $projectRoot '.tools/jdk-25.0.4.1+1'
    if (Test-Path -LiteralPath $localJdk) { $Jdk25 = $localJdk }
}
if (!$Jdk25 -or !(Test-Path -LiteralPath (Join-Path $Jdk25 'bin/java.exe'))) {
    throw 'JDK 25 angeben: ./scripts/build.ps1 -Jdk25 C:/Pfad/zum/jdk-25. JDK 21 muss ebenfalls installiert sein.'
}
$env:JAVA_HOME = (Resolve-Path -LiteralPath $Jdk25).Path
$dist = Join-Path $projectRoot 'dist'
New-Item -ItemType Directory -Path $dist -Force | Out-Null
foreach ($minecraftVersion in @('1.21.11','26.3')) {
    $versionRoot = Join-Path $projectRoot "versions/$minecraftVersion"
    $buildArgs = @('-p',$versionRoot,'clean','build','--console=plain')
    if ($FullRecovery) { $buildArgs += '-PegoxFullRecovery' }
    & (Join-Path $versionRoot 'gradlew.bat') @buildArgs
    if ($LASTEXITCODE -ne 0) { throw "Build fehlgeschlagen: $minecraftVersion" }
    $versionProperty = Get-Content -LiteralPath (Join-Path $versionRoot 'gradle.properties') |
        Where-Object { $_ -match '^mod_version=' }
    $modVersion = ($versionProperty -replace '^mod_version=', '').Trim()
    if (!$modVersion) { throw "mod_version fehlt: $versionRoot" }
    $jar = Join-Path $versionRoot "build/libs/EgoX-SeedCracker-$modVersion.jar"
    if (!(Test-Path -LiteralPath $jar)) { throw "Mod-JAR fehlt: $jar" }
    Copy-Item -LiteralPath $jar -Destination $dist -Force
}
Get-FileHash -Algorithm SHA256 -LiteralPath (Get-ChildItem -LiteralPath $dist -Filter '*.jar').FullName |
    ForEach-Object { "$($_.Hash.ToLower())  $(Split-Path $_.Path -Leaf)" } |
    Set-Content -LiteralPath (Join-Path $dist 'SHA256SUMS.txt') -Encoding utf8
Write-Host "Fertige Mod-JARs: $dist"
