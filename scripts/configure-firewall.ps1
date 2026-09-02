<#
Configura únicamente los puertos necesarios para el Taller 3.
Ejecutar en PowerShell como Administrador.
#>
param(
    [int]$SshPort = 22,
    [int]$BackendPort = 8082,
    [int]$FrontendPort = 8081,
    [int]$PeerBackendPort = 8084,
    [int]$PeerFrontendPort = 8083
)

$ErrorActionPreference = "Stop"

$currentPrincipal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $currentPrincipal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    Write-Error "Este script debe ejecutarse como Administrador."
    exit 1
}

$rules = @(
    @{ DisplayName = "Calculadora - SSH"; Port = $SshPort },
    @{ DisplayName = "Calculadora - Backend propio"; Port = $BackendPort },
    @{ DisplayName = "Calculadora - Frontend propio"; Port = $FrontendPort },
    @{ DisplayName = "Calculadora - Backend recibido"; Port = $PeerBackendPort },
    @{ DisplayName = "Calculadora - Frontend recibido"; Port = $PeerFrontendPort }
)

foreach ($rule in $rules) {
    Remove-NetFirewallRule -DisplayName $rule.DisplayName -ErrorAction SilentlyContinue
    New-NetFirewallRule `
        -DisplayName $rule.DisplayName `
        -Direction Inbound `
        -Protocol TCP `
        -LocalPort $rule.Port `
        -Action Allow | Out-Null
    Write-Host "Permitido TCP/$($rule.Port) - $($rule.DisplayName)"
}

Write-Host "OK - Firewall configurado."
