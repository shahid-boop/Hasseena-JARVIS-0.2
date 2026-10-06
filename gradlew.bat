@echo off
setlocal
set "GRADLE_VERSION=8.7"
set "GRADLE_HOME=%USERPROFILE%\.gradle\hasseena-gradle\%GRADLE_VERSION%"
set "GRADLE_BIN=%GRADLE_HOME%\gradle-%GRADLE_VERSION%\bin\gradle.bat"
if exist "%GRADLE_BIN%" goto run
where gradle >nul 2>&1
if %ERRORLEVEL% EQU 0 (
  for /f "tokens=2" %%V in ('gradle --version ^| findstr /B /C:"Gradle"') do set "INSTALLED=%%V"
  if "%INSTALLED%"=="%GRADLE_VERSION%" goto local
)
set "ZIP=%TEMP%\hasseena-gradle-%GRADLE_VERSION%.zip"
where curl.exe >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
  echo Error: curl.exe is required to download Gradle %GRADLE_VERSION%.
  exit /b 1
)
mkdir "%USERPROFILE%\.gradle\hasseena-gradle" >nul 2>&1
curl.exe -fL --retry 3 -o "%ZIP%" "https://services.gradle.org/distributions/gradle-%GRADLE_VERSION%-bin.zip"
if %ERRORLEVEL% NEQ 0 exit /b 1
powershell -NoProfile -Command "Expand-Archive -Force '%ZIP%' '%USERPROFILE%\.gradle\hasseena-gradle'"
del /q "%ZIP%" >nul 2>&1
:run
call "%GRADLE_BIN%" %*
exit /b %ERRORLEVEL%
:local
gradle %*
exit /b %ERRORLEVEL%
