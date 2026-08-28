@echo off
setlocal
cd /d "%~dp0.."
if exist out-backend rmdir /s /q out-backend
mkdir out-backend
javac -d out-backend backend\src\main\java\com\fase1\calculadora\CalculatorServer.java backend\src\main\java\com\fase1\calculadora\CalculatorService.java backend\src\main\java\com\fase1\calculadora\HistoryRepository.java
if errorlevel 1 exit /b 1
set PORT=%~1
if "%PORT%"=="" set PORT=8082
java --add-modules jdk.httpserver -cp out-backend com.fase1.calculadora.CalculatorServer %PORT%