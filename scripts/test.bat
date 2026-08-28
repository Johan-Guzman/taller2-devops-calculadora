@echo off
setlocal
cd /d "%~dp0.."
if exist out-test rmdir /s /q out-test
mkdir out-test
javac -d out-test backend\src\main\java\com\fase1\calculadora\*.java backend\src\test\java\com\fase1\calculadora\AppTest.java
if errorlevel 1 exit /b 1
java --add-modules jdk.httpserver -cp out-test com.fase1.calculadora.AppTest