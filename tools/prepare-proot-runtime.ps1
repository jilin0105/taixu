param(
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $PSScriptRoot
$jniDirectory = Join-Path $projectRoot 'app\src\main\jniLibs\arm64-v8a'
$loaderTarget = Join-Path $jniDirectory 'libproot-loader.so'
$packageSha256 = 'ec9fe38c50cfd49dd31fe360ffbcc3124a945dc1ea16293a8a769303dd724f46'
$packageUrls = @(
    'https://termux.librehat.com/apt/termux-main/pool/main/p/proot/proot_5.1.107.89_aarch64.deb',
    'https://packages.termux.dev/apt/termux-main/pool/main/p/proot/proot_5.1.107.89_aarch64.deb'
)
# The ARM64 tracee loader lives at this exact path inside the Termux package.
$loaderEntry = './data/data/com.termux/files/usr/libexec/proot/loader'

if ((Test-Path -LiteralPath $loaderTarget) -and -not $Force) {
    Write-Host "PRoot loader already exists: $loaderTarget"
    exit 0
}

# Git Bash puts its GNU tar on PATH ahead of the Windows system tar; GNU tar
# misinterprets Windows paths (e.g. "C:\...") as remote host specs and fails
# with "Cannot connect to C: resolve failed". Use the Windows bsdtar directly.
$systemTar = Join-Path $env:SystemRoot 'System32\tar.exe'
if (-not (Test-Path -LiteralPath $systemTar)) {
    throw "Windows system tar not found at $systemTar"
}

$temporaryRoot = Join-Path ([IO.Path]::GetTempPath()) ("taixu-proot-" + [guid]::NewGuid())
$debPath = Join-Path $temporaryRoot 'proot.deb'
$arDirectory = Join-Path $temporaryRoot 'ar'

try {
    New-Item -ItemType Directory -Path $arDirectory -Force | Out-Null
    $lastDownloadError = $null
    foreach ($url in $packageUrls) {
        try {
            Write-Host "Downloading PRoot runtime: $url"
            Invoke-WebRequest -Uri $url -OutFile $debPath
            $actualSha256 = (Get-FileHash -LiteralPath $debPath -Algorithm SHA256).Hash.ToLowerInvariant()
            if ($actualSha256 -ne $packageSha256) {
                throw "Checksum mismatch: expected $packageSha256, got $actualSha256"
            }
            $lastDownloadError = $null
            break
        } catch {
            $lastDownloadError = $_
            Remove-Item -LiteralPath $debPath -Force -ErrorAction SilentlyContinue
        }
    }
    if ($null -ne $lastDownloadError) {
        throw $lastDownloadError
    }

    & $systemTar -xf $debPath -C $arDirectory
    if ($LASTEXITCODE -ne 0) {
        throw "Unable to extract the Termux deb archive (tar exit=$LASTEXITCODE)"
    }
    $dataArchive = Get-ChildItem -LiteralPath $arDirectory -File |
        Where-Object { $_.Name -like 'data.tar.*' } |
        Select-Object -First 1
    if ($null -eq $dataArchive) {
        throw 'The Termux deb does not contain data.tar.*'
    }

    # The package data archive also contains docs and a 32-bit loader; extracting
    # the whole payload on Windows fails with EINVAL on deep paths
    # ('\\?\C:\...\copyright: Invalid argument'). We only need the ARM64 loader,
    # so stream that single entry straight to the target file.
    $listing = & $systemTar -tf $dataArchive.FullName
    if ($LASTEXITCODE -ne 0 -or -not ($listing -contains $loaderEntry)) {
        throw "The Termux package does not contain '$loaderEntry'"
    }

    New-Item -ItemType Directory -Path $jniDirectory -Force | Out-Null
    # Start-Process -RedirectStandardOutput preserves raw bytes; PowerShell's
    # '>' operator would re-encode the binary as UTF-16 and corrupt the ELF.
    $process = Start-Process -FilePath $systemTar `
        -ArgumentList @('-xOf', $dataArchive.FullName, $loaderEntry) `
        -RedirectStandardOutput $loaderTarget `
        -NoNewWindow -Wait -PassThru
    if ($process.ExitCode -ne 0) {
        throw "Unable to extract the PRoot loader (tar exit=$($process.ExitCode))"
    }

    $loader = Get-Item -LiteralPath $loaderTarget
    if ($loader.Length -le 4096 -or $loader.Length -gt 4MB) {
        throw 'The official package does not contain a usable ARM64 proot loader'
    }

    $magic = [byte[]]::new(4)
    $stream = [IO.File]::OpenRead($loader.FullName)
    try {
        [void]$stream.Read($magic, 0, $magic.Length)
    } finally {
        $stream.Dispose()
    }
    if ($magic[0] -ne 0x7f -or $magic[1] -ne 0x45 -or $magic[2] -ne 0x4c -or $magic[3] -ne 0x46) {
        throw 'The extracted PRoot loader is not an ELF file'
    }

    $loaderSha256 = (Get-FileHash -LiteralPath $loaderTarget -Algorithm SHA256).Hash
    Write-Host "Prepared $loaderTarget"
    Write-Host "Loader SHA-256: $loaderSha256"
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        $resolvedTemporaryRoot = (Resolve-Path -LiteralPath $temporaryRoot).Path
        $systemTemporaryRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
        if ($resolvedTemporaryRoot.StartsWith($systemTemporaryRoot, [StringComparison]::OrdinalIgnoreCase)) {
            Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
        }
    }
}
