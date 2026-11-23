<#
.SYNOPSIS
  Prueba de humo: llama a los endpoints principales de Atlas RAM y verifica que respondan bien.

.DESCRIPTION
  Sirve para comprobar en segundos que un despliegue (local o AWS) funciona de punta a punta: autenticación,
  consultas SQL del antibiograma, tendencia, WISCA, alertas, cargas e inventario. No modifica datos.
  Sale con código 1 si algo falla, así también se puede usar en un pipeline.

.EXAMPLE
  .\scripts\smoke.ps1
  .\scripts\smoke.ps1 -BaseUrl https://3-85-201-152.sslip.io/api/v1
#>
param(
    [string]$BaseUrl = "http://localhost:8080/api/v1",
    [string]$Email = "admin@atlasram.test",
    [string]$Password = "AtlasRam2026!",
    [long]$FacilityId = 6
)

$ErrorActionPreference = "Stop"
$failures = 0

function Check([string]$name, [scriptblock]$call, [scriptblock]$assert) {
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $result = & $call
        $ok = & $assert $result
        $ms = $watch.ElapsedMilliseconds
        if ($ok) {
            Write-Host ("  OK    {0,-48} {1,6} ms" -f $name, $ms) -ForegroundColor Green
        } else {
            Write-Host ("  FALLA {0,-48} respuesta inesperada" -f $name) -ForegroundColor Red
            $script:failures++
        }
    } catch {
        $status = $_.Exception.Response.StatusCode.value__
        Write-Host ("  FALLA {0,-48} HTTP {1} {2}" -f $name, $status, $_.Exception.Message) -ForegroundColor Red
        $script:failures++
    }
}

Write-Host "Atlas RAM · prueba de humo contra $BaseUrl" -ForegroundColor Cyan

$login = Invoke-RestMethod -Method Post -Uri "$BaseUrl/auth/login" -ContentType "application/json" `
    -Body (@{ email = $Email; password = $Password } | ConvertTo-Json)
$headers = @{ Authorization = "Bearer $($login.accessToken)" }
Write-Host "  OK    Inicio de sesión ($($login.role))" -ForegroundColor Green

$from = "2025-09-01"; $to = "2026-08-31"
$get = { param($path) Invoke-RestMethod -Uri "$BaseUrl$path" -Headers $headers }

Check "Catálogo de antibióticos" { & $get "/antibiotics?size=5" } { param($r) $r.content.Count -gt 0 }
Check "Establecimientos accesibles" { & $get "/auth/me/facilities" } { param($r) $r.Count -gt 0 }
Check "Antibiograma CLSI M39" { & $get "/antibiogram?microorganismId=1&from=$from&to=$to" } { param($r) $r.Count -gt 0 -and $null -ne $r[0].susceptibleCiLow }
Check "Antibiograma solo datos reales" { & $get "/antibiogram?microorganismId=1&from=$from&to=$to&origin=REAL" } { param($r) $true }
Check "Mapa por distrito" { & $get "/antibiogram/by-district?microorganismId=1&antibioticId=6&from=$from&to=$to" } { param($r) $r.Count -gt 0 }
Check "Hospitales de referencia" { & $get "/antibiogram/referral?districtId=4&from=2024-09-01&to=$to" } { param($r) $null -ne $r.facilities }
Check "Tendencia Cochran-Armitage" { & $get "/antibiogram/trend?microorganismId=2&antibioticId=11&facilityId=$FacilityId&from=2024-09-01&to=$to" } { param($r) $r.points.Count -gt 0 }
Check "WISCA bacteriemia" { & $get "/antibiogram/wisca?syndrome=BACTEREMIA&from=$from&to=$to" } { param($r) $r.regimens.Count -gt 0 }
Check "Alertas" { & $get "/alerts" } { param($r) $true }
Check "Aislamientos paginados" { & $get "/isolates?size=5" } { param($r) $r.content.Count -gt 0 }
Check "Cargas activas" { & $get "/uploads/active" } { param($r) $true }
Check "Inventario (tablero)" { & $get "/logistics/facilities/$FacilityId/stock" } { param($r) $null -ne $r.itemCount }
Check "Inventario (DDD)" { & $get "/logistics/facilities/$FacilityId/ddd?from=2026-05-01&to=2026-10-31" } { param($r) $null -ne $r.totalDdd }

if ($failures -gt 0) {
    Write-Host "$failures verificación(es) fallaron." -ForegroundColor Red
    exit 1
}
Write-Host "Todo responde correctamente." -ForegroundColor Green
