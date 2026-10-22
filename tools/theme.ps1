# Windows version of theme.sh: push a theme folder (config.json and optionally wallpaper.jpg) to the
# launcher on the TV and restart Home. See themes/README.md for the keys.
#
# Usage: tools\theme.ps1 -TvHost 192.168.1.50 themes\aurora
#        tools\theme.ps1 -TvHost 192.168.1.50 -Reset
param(
    [Parameter(Position = 0)][string]$Theme,
    [switch]$Reset,
    [string]$TvHost = $env:TV_HOST
)
$ErrorActionPreference = 'Stop'
$OutputEncoding = New-Object System.Text.UTF8Encoding $false
if (-not $TvHost) { throw 'pass -TvHost <tv ip> or set $env:TV_HOST' }
$env:TV_HOST = $TvHost
# Resolve the theme folder against the caller's directory before moving into tools\.
if ($Theme) { $Theme = (Resolve-Path $Theme).Path }
Set-Location $PSScriptRoot
$py = 'venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw 'run: python -m venv tools\venv; tools\venv\Scripts\python.exe -m pip install -r tools\requirements.txt' }
$pkg = 'com.gathin.tvhome'
$dir = "/sdcard/Android/data/$pkg/files"
$revision = [guid]::NewGuid().ToString('N')
# Restart Home so it rereads the theme. Preserve the complete accessibility-service list across the force-stop.
# TCL Android 11 builds can leave an enabled service in AccessibilityManager's "crashed" set; allow extra settle
# time and retry the off/on cycle once when our service is still crashed (or nothing at all is bound).
$restart = "a=`$(settings get secure enabled_accessibility_services); am force-stop $pkg; am start -n $pkg/.MainActivity >/dev/null; case `"`$a`" in *$pkg*) sleep 1; i=0; while [ `$i -lt 2 ]; do settings put secure enabled_accessibility_services ''; settings put secure accessibility_enabled 0; sleep 2; settings put secure enabled_accessibility_services `"`$a`"; settings put secure accessibility_enabled 1; sleep 3; s=`$(dumpsys accessibility 2>/dev/null); case `"`$s`" in *'Crashed services:'*'$pkg'*|*'Bound services:{}'*) i=`$((i+1)) ;; *) break ;; esac; done; case `"`$s`" in *'Crashed services:'*'$pkg'*) echo 'warning: Family Edition accessibility service is still crashed' >&2 ;; esac ;; esac"

# Some firmware (TCL G07, Android 11) locks the shell out of Android/data. Files are staged in
# /data/local/tmp and copied in, or written through the launcher's ThemeProvider when that is refused.
$uri = "content://$pkg.theme"
$tmp = '/data/local/tmp/cyt-theme'
# A file's staging path; wallpapers/a.jpg is staged as $tmp-wallpapers-a.jpg.
function Stage($name) { "$tmp-$($name -replace '/', '-')" }
function Put($name) { $t = Stage $name; "cp $t $dir/$name 2>/dev/null || content write --uri $uri/$name < $t; rm -f $t" }
function Drop($name) { "rm -f $dir/$name 2>/dev/null; content delete --uri $uri/$name 2>/dev/null" }

if ($Reset) {
    & $py -I tv.py "mkdir -p $dir 2>/dev/null; $(Drop 'config.json'); $(Drop 'wallpaper.jpg'); printf '%s' '$revision' > $tmp-theme_revision; $(Put 'theme_revision'); $restart; echo reset"
    exit 0
}
if (-not $Theme) { throw 'usage: theme.ps1 <theme folder> | -Reset' }
$config = Join-Path $Theme 'config.json'
if (-not (Test-Path $config)) { throw "no config.json in $Theme" }
try { Get-Content $config -Raw | ConvertFrom-Json | Out-Null } catch { throw "$config is not valid JSON" }

& $py -I tv.py "mkdir -p $dir 2>/dev/null" | Out-Null
& $py -I tv.py --push $config "$tmp-config.json" | Out-Null
& $py -I tv.py (Put 'config.json')
$wall = Join-Path $Theme 'wallpaper.jpg'
if (Test-Path $wall) { & $py -I b64push.py $wall "$tmp-wallpaper.jpg"; & $py -I tv.py (Put 'wallpaper.jpg') } else { & $py -I tv.py (Drop 'wallpaper.jpg') | Out-Null }
& $py -I tv.py "mkdir -p $dir/wallpapers 2>/dev/null; rm -f $dir/wallpapers/*.jpg $dir/wallpapers/*.jpeg $dir/wallpapers/*.png $dir/wallpapers/*.webp $dir/wallpapers/*.JPG $dir/wallpapers/*.JPEG $dir/wallpapers/*.PNG $dir/wallpapers/*.WEBP 2>/dev/null; content delete --uri $uri/wallpapers 2>/dev/null" | Out-Null
$wallpapers = Join-Path $Theme 'wallpapers'
if (Test-Path $wallpapers -PathType Container) {
    Get-ChildItem $wallpapers -File | Where-Object { $_.Extension -match '^\.(jpg|jpeg|png|webp)$' } | ForEach-Object {
        if ($_.Name -notmatch '^[A-Za-z0-9_.-]+$') { throw "unsupported wallpaper filename: $($_.Name)" }
        & $py -I b64push.py $_.FullName (Stage "wallpapers/$($_.Name)")
        & $py -I tv.py (Put "wallpapers/$($_.Name)") | Out-Null
    }
}
& $py -I tv.py "printf '%s' '$revision' > $tmp-theme_revision; $(Put 'theme_revision')" | Out-Null
& $py -I tv.py "$restart; echo applied"
Write-Host "Theme $(Split-Path $Theme -Leaf) is on the TV. Undo with: tools\theme.ps1 -TvHost $TvHost -Reset"
