$ErrorActionPreference = 'Stop'
$root = "C:\Users\wangk\Desktop\LinuxAIRuntime"
$decls = Import-Csv (Join-Path $root ".tmp_unusedscan\decls.tsv") -Delimiter "`t" -Header file,line,vis,kind,name,ann
$mainDecls = $decls | Where-Object { $_.file -match '\\src\\main\\' }
Write-Host "main decls: $($mainDecls.Count)"
$names = $mainDecls | ForEach-Object { $_.name } | Sort-Object -Unique
Write-Host "unique names: $($names.Count)"
$names | ForEach-Object { '\b' + $_ + '\b' } | Out-File (Join-Path $root ".tmp_unusedscan\patterns.txt") -Encoding ascii
