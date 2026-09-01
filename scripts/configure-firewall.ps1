<#
Configura el Firewall de Windows en el PC Ops para exponer únicamente
los puertos necesarios: SSH (entrada del despliegue del equipo par),
Backend y Frontend de la calculadora.

Windows Firewall ya bloquea por defecto todo el tráfico entrante sin
una regla explícita; este script solo agrega las reglas de permiso
necesarias, sin tocar el resto de la configuración.

Parámetros opcionales:
  -SshPort, -BackendPort, -FrontendPort

Debe ejecutarse en una consola de PowerShell como Administrador.
#>

param(
    [int]$SshPort = 22,
    [int]$BackendPort = 8082,
    [int]$FrontendPort = 8081
)

$ErrorActionPreference = "Stop"

$currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Error "Este script debe ejecutarse como Administrador."
    exit 1
}

$rules = @(
    @{ DisplayName = "Calculadora - SSH";      Port = $SshPort }
    @{ DisplayName = "Calculadora - Backend";  Port = $BackendPort }
    @{ DisplayName = "Calculadora - Frontend"; Port = $FrontendPort }
)

foreach ($rule in $rules) {
    Remove-NetFirewallRule -DisplayName $rule.DisplayName -ErrorAction SilentlyContinue
    New-NetFirewallRule -DisplayName $rule.DisplayName -Direction Inbound -Protocol TCP -LocalPort $rule.Port -Action Allow | Out-Null
    Write-Host "Permitido entrante TCP/$($rule.Port) ($($rule.DisplayName))"
}

Write-Host "OK - Firewall configurado (SSH $SshPort, Backend $BackendPort, Frontend $FrontendPort)"
