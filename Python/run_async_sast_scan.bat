@echo off
setlocal EnableExtensions EnableDelayedExpansion

echo =====================================================
echo [INFO] Fortify Incremental Scan Started
echo =====================================================

:: =========================
:: PARAMETERS
:: =========================
set "project=%~1"
set "version=%~2"
set "buildID=%~3"
set "sastConfigFile=%~4"
set "webhook_token=%~5"
set "buildType=%~6"
set "incr_scan_path=%~7"
set "web_url=%~8"
::set "COMP_JAR=%~9"

:: Handle parameters beyond %9
shift
shift
shift
shift
shift
shift
shift
shift

set "INCR_SRC_FILE=%~1"
set "IMPACTED_MODULES_FILE=%~2"
set "JAR_PATHS_FILE=%~3"

echo [INFO] Project: %project%
echo [INFO] BuildID: %buildID%

:: =========================
:: VALIDATION
:: =========================
if not exist "%INCR_SRC_FILE%" (
    echo [ERROR] incr_source_code.txt not found
    exit /b 300
)

if not exist "%IMPACTED_MODULES_FILE%" (
    echo [ERROR] impacted_modules.txt not found
    exit /b 301
)

if not exist "%JAR_PATHS_FILE%" (
    echo [ERROR] built_jar_paths.txt not found
    exit /b 302
)

:: =========================
:: OUTPUT SETUP
:: =========================
set "RUN_DIR=%CD%"
if not exist "%RUN_DIR%\output" mkdir "%RUN_DIR%\output"

set "TRANS_LOG=%RUN_DIR%\output\translate_%buildID%.log"
set "SCAN_LOG=%RUN_DIR%\output\scan_%buildID%.log"

:: =========================
:: LOAD FORTIFY CONFIG
:: =========================
set "PS_PARSE_FORTIFY=R:\parse_yaml_V1.ps1"

set "sastConfigFile=%sastConfigFile:/shared_files=R:%"
set "sastConfigFile=%sastConfigFile:/=\%"

for /f "delims=" %%A in ('
  powershell -NoProfile -ExecutionPolicy Bypass -File "%PS_PARSE_FORTIFY%" "%sastConfigFile%"
') do (
  for /f "tokens=1,* delims==" %%K in ("%%A") do set "%%K=%%L"
)

set "SA=%fortifyhome%\bin\sourceanalyzer.exe"

if not exist "%SA%" (
    echo [ERROR] sourceanalyzer not found
    exit /b 211
)

:: =========================
:: WEBHOOK SETUP
:: =========================
set "web_url=%web_url: =%"
set "webhook_token=%webhook_token: =%"
set "WEBHOOK_URL=%web_url%%webhook_token%"

set "USERNAME=admin"
set "SCAN_TYPE=SAST"

:: =========================
:: PROCESS MODULES
:: =========================
for /f "usebackq delims=" %%M in ("%IMPACTED_MODULES_FILE%") do (

    set "MODULE=%%M"
    set "FILES_ARG="
    set "JAR_PATH="

    echo -----------------------------------------------------
    echo [INFO] Processing MODULE: !MODULE!
    echo -----------------------------------------------------

    :: Extract module name
    for %%A in ("!MODULE!") do set "MODULE_NAME=%%~nxA"
    echo [INFO] Module Name: !MODULE_NAME!

    :: =========================
    :: FIND JAVA FILES
    :: =========================
    for /f "usebackq delims=" %%F in ("%INCR_SRC_FILE%") do (
        echo %%F | findstr /I "!MODULE!" >nul
        if !errorlevel! EQU 0 (
            set FILES_ARG=!FILES_ARG! "%%F"
        )
    )

    if defined FILES_ARG (
        echo [INFO] Files found:
        echo !FILES_ARG!
    ) else (
        echo [INFO] No files for this module, skipping...
        goto :CONTINUE_LOOP
    )

    :: =========================
    :: FIND JAR
    :: =========================
    for /f "usebackq delims=" %%J in ("%JAR_PATHS_FILE%") do (
        echo %%J | findstr /I "!MODULE_NAME!" >nul
        if !errorlevel! EQU 0 (
            set "JAR_PATH=%%J"
        )
    )

    if defined JAR_PATH (
        echo [INFO] Jar Found: !JAR_PATH!
        set "CP_ARG=-cp ""!JAR_PATH!"""
    ) else (
        echo [WARN] No jar found for module
        set "CP_ARG="
    )

    :: =========================
    :: BUILD VARIABLES
    :: =========================
    set "BASE_NAME=!MODULE_NAME!_%buildID%"
    set "FPR_FILE=%project%_!MODULE_NAME!_%version%_%buildID%.fpr"
    set "FPR_OUT=%RUN_DIR%\output\!FPR_FILE!"

    :: =========================
    :: TRANSLATE
    :: =========================
    echo [INFO] TRANSLATE START

    "%SA%" -b "!BASE_NAME!" -Dcom.fortify.sca.Preserve=true !CP_ARG! !FILES_ARG! -logfile "!TRANS_LOG!"

    if errorlevel 1 (
        echo [ERROR] TRANSLATE failed
        exit /b 401
    )

    :: =========================
    :: SCAN
    :: =========================
    echo [INFO] SCAN START

    "%SA%" -Xmx20480m -b "!BASE_NAME!" -scan -f "!FPR_OUT!" !scan_options! -logfile "!SCAN_LOG!"

    if errorlevel 1 (
        echo [ERROR] SCAN failed
        exit /b 402
    )

    if exist "!FPR_OUT!" (
        echo [SUCCESS] FPR created: !FPR_OUT!

        set "STATUS=SUCCESS"
        set "RESULT_FPR=!FPR_OUT!"
        set "COMPONENT=!MODULE_NAME!"

       :: call :SEND_WEBHOOK
    )

    :CONTINUE_LOOP
)

echo -----------------------------------------------------
echo [INFO] ALL MODULES COMPLETED
echo -----------------------------------------------------
exit /b 0


:: =========================
:: WEBHOOK FUNCTION
:: =========================
:SEND_WEBHOOK
setlocal EnableDelayedExpansion

set "BUILD_ID=%buildID%"
set "BUILD_TYPE=%buildType%"

for /f "usebackq delims=" %%J in (`
  powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = [ordered]@{ SCAN_TYPE=$env:SCAN_TYPE; USERNAME=$env:USERNAME; status=$env:STATUS; FPR_FILE=$env:RESULT_FPR; COMPONENT=$env:COMPONENT; buildType=$env:BUILD_TYPE; buildID=$env:BUILD_ID }; ConvertTo-Json -Compress -InputObject $p"
`) do (
  set "PAYLOAD=%%J"
)

echo [INFO] Webhook Payload: !PAYLOAD!

for /f "tokens=*" %%H in ('curl -s -o nul -w "%%{http_code}" -X POST ^
     -H "Content-Type: application/json" ^
     -d "!PAYLOAD!" ^
     "!WEBHOOK_URL!"') do set "HTTP_STATUS=%%H"

echo [INFO] HTTP Status: !HTTP_STATUS!

endlocal & exit /b 0
