$ErrorActionPreference = 'Stop'
$root = "C:\Users\wangk\Desktop\LinuxAIRuntime"
$mods = 'public|private|internal|protected|inline|suspend|override|open|final|abstract|sealed|annotation|data|enum|value|external|infix|operator|tailrec|const|lateinit|actual|expect'
$pattern = '^(?:(?:' + $mods + ')\s+)*(class|interface|object|fun|val|var|typealias)\s+[`]?([A-Za-z_]\w*)'

$out = New-Object System.Collections.Generic.List[string]
$dirs = @("feature\browser","feature\chat","feature\components","feature\custom_iteration","feature\developer","feature\home","feature\navigation","feature\onboarding","feature\settings","feature\terminal","feature\theme","feature\workspace","app")
foreach ($d in $dirs) {
  Get-ChildItem (Join-Path $root $d) -Recurse -Filter *.kt | Where-Object { $_.FullName -match '\\src\\' } | ForEach-Object {
    $file = $_.FullName
    $rel = $file.Substring($root.Length + 1)
    $lines = Get-Content $file
    for ($i = 0; $i -lt $lines.Count; $i++) {
      $line = $lines[$i]
      if ($line -match $pattern) {
        $kind = $Matches[1]; $name = $Matches[2]
        $vis = if ($line -match '^\s*(private|internal|protected)\b') { $Matches[1] } else { 'public' }
        $ann = @()
        for ($j = $i - 1; $j -ge 0 -and $j -ge $i - 6; $j--) {
          $pl = $lines[$j].Trim()
          if ($pl -eq '' -or $pl -match '^(//|\*|/\*)') { continue }
          if ($pl -match '^@') { $ann += $pl } else { break }
        }
        $annStr = ($ann -join ' | ')
        $out.Add("$rel`t$($i+1)`t$vis`t$kind`t$name`t$annStr")
      }
    }
  }
}
$out | Out-File -FilePath (Join-Path $root ".tmp_unusedscan\decls.tsv") -Encoding utf8
Write-Host "Total declarations: $($out.Count)"
