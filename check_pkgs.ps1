Add-Type -AssemblyName System.IO.Compression.FileSystem
$jars = @(
    @{ name="core"; path="d:\文档\GitHub\arcane_mag\core\build\libs\arcane_mag-1.20.1-1.0.0.jar" },
    @{ name="enchant"; path="d:\文档\GitHub\arcane_mag\enchant\build\libs\arcane_mag_enchant-1.20.1-1.0.0.jar" },
    @{ name="spell"; path="d:\文档\GitHub\arcane_mag\spell\build\libs\arcane_mag_spell-1.20.1-1.0.0.jar" }
)
$packages = @{}
foreach ($j in $jars) {
    $zip = [System.IO.Compression.ZipFile]::OpenRead($j.path)
    $pkgs = @{}
    foreach ($e in $zip.Entries) {
        if ($e.FullName -match "^com/zzdzt/arcanemag/(.+?)/[^/]+\.class$") {
            $pkg = $matches[1] -replace "/", "."
            $fullPkg = "com.zzdzt.arcanemag.$pkg"
            if (-not $pkgs.ContainsKey($fullPkg)) { $pkgs[$fullPkg] = 0 }
            $pkgs[$fullPkg]++
        }
    }
    $packages[$j.name] = $pkgs
    $zip.Dispose()
}
Write-Output "===CORE packages==="
$packages["core"].Keys | Sort-Object | ForEach-Object { Write-Output "  $_" }
Write-Output "===ENCHANT packages==="
$packages["enchant"].Keys | Sort-Object | ForEach-Object { Write-Output "  $_" }
Write-Output "===SPELL packages==="
$packages["spell"].Keys | Sort-Object | ForEach-Object { Write-Output "  $_" }
Write-Output "===CONFLICTS core vs spell==="
foreach ($k in $packages["core"].Keys) { if ($packages["spell"].ContainsKey($k)) { Write-Output "  CONFLICT: $k" } }
Write-Output "===CONFLICTS core vs enchant==="
foreach ($k in $packages["core"].Keys) { if ($packages["enchant"].ContainsKey($k)) { Write-Output "  CONFLICT: $k" } }
Write-Output "===CONFLICTS enchant vs spell==="
foreach ($k in $packages["enchant"].Keys) { if ($packages["spell"].ContainsKey($k)) { Write-Output "  CONFLICT: $k" } }
