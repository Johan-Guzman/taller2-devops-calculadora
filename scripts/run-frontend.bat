@echo off
setlocal

rem Compila y ejecuta el Frontend apuntando al Backend indicado.
cd /d "%~dp0.."
if "%~1"=="" (
    echo Uso: scripts\run-frontend.bat http://IP_BACKEND:PUERTO_BACKEND [PUERTO_FRONTEND]
    exit /b 1
)

set "BACKEND_URL=%~1"
set "PORT=%~2"
if "%PORT%"=="" set "PORT=8081"

if exist out-frontend rmdir /s /q out-frontend
mkdir out-frontend

javac -d out-frontend backend\src\main\java\com\fase1\calculadora\FrontendServer.java
if errorlevel 1 exit /b 1

java --add-modules jdk.httpserver -cp out-frontend com.fase1.calculadora.FrontendServer "%BACKEND_URL%" %PORT%
