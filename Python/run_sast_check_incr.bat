@echo off
setlocal EnableExtensions EnableDelayedExpansion

echo =====================================================
echo [INFO] Starting Fortify SAST Scan
echo [INFO] Directory : %CD%
echo [INFO] Date/Time : %DATE% %TIME%
echo =====================================================

::----------Reading the parameters---------------
set "project=%~1"
set "version=%~2"
set "buildID=%~3"
set "sastConfigFile=%~4"
set "webhook_token=%~5"
set "buildType=%~6"
set "incr_scan_path=%~7"
set "web_url=%~8"
set "COMP_JAR=%~9"

echo [DEBUG] Raw args : %*
echo [DEBUG] 1=%~1
echo [DEBUG] 2=%~2
echo [DEBUG] 3=%~3
echo [DEBUG] 4=%~4
echo [DEBUG] 5=%~5
echo [DEBUG] 6=%~6


echo [INFO] build is : %buildID%
echo [PARAM] project=%project% , version=%version% , buildID=%buildID% , buildType=%buildType% , incr_scan_path=%incr_scan_path%

set "buildwise_incr_scanpath=%incr_scan_path%\%buildID%"

echo [INFO] buildwise_incr_scanpath = %buildwise_incr_scanpath%

REM ---- Skip if folder not created ----
if not exist "%buildwise_incr_scanpath%\" (
  echo [INFO] Incremental folder not found for build %buildID%: "%buildwise_incr_scanpath%"
  exit /b 0
)

REM ---- Skip if folder exists but has no files ----
dir /a-d /s /b "%buildwise_incr_scanpath%" >nul 2>&1
if errorlevel 1 (
  echo [INFO] No incremental files detected for build %buildID%; skipping translate/scan.
  exit /b 0
)

:: -------- Config files --------
set "PS_PARSE_FORTIFY=R:\parse_yaml_V1.ps1"
::set "FORTIFY_YAML=R:\fortify_sast.yaml"

REM Convert: /shared_files/...  ->  R:\...
set "sastConfigFile=%sastConfigFile:/shared_files=R:%"

REM Convert forward slashes to backslashes
set "sastConfigFile=%sastConfigFile:/=\%"

echo [DEBUG] sastConfigFile resolved to: "%sastConfigFile%"


set "FORTIFY_YAML=%sastConfigFile%"

:: -------- Validation --------
if not exist "%PS_PARSE_FORTIFY%"  exit /b 100
if not exist "%FORTIFY_YAML%"      exit /b 101

:: =====================================================
:: Load Fortify globals
:: =====================================================
for /f "delims=" %%A in ('
  powershell -NoProfile -ExecutionPolicy Bypass -File "%PS_PARSE_FORTIFY%" "%FORTIFY_YAML%"
') do (
  for /f "tokens=1,* delims==" %%K in ("%%A") do (
    set "%%K=%%L"
    echo [CONFIG] %%K=%%L
  )
)

set "SA=%fortifyhome%\bin\sourceanalyzer.exe"
if not exist "%SA%" (
  echo [ERROR] sourceanalyzer not found: "%SA%"
  exit /b 211
)


:: =========================
:: WEBHOOK (constants)
:: =========================

REM --- remove ANY spaces from both (handles leading/trailing/middle) ---
set "web_url=%web_url: =%"
set "webhook_token=%webhook_token: =%"

REM --- build webhook URL ---
set "WEBHOOK_URL=%web_url%%webhook_token%"
set "USERNAME=admin"
set "SCAN_TYPE=SAST"
echo WEBHOOK_URL="%WEBHOOK_URL%"


echo =====================================================
echo [INFO] buildwise_incr_scanpath = %buildwise_incr_scanpath%
echo =====================================================

:: Step-1: Go inside the path
if not exist "%buildwise_incr_scanpath%" (
  echo [ERROR] Path not found: "%buildwise_incr_scanpath%"
  exit /b 20
)

pushd "%buildwise_incr_scanpath%" >nul 2>&1
if errorlevel 1 (
  echo [ERROR] Cannot enter: "%buildwise_incr_scanpath%"
  exit /b 21
)

:: ---- Bind logs & output directory under the current run directory (AFTER pushd) ----
set "RUN_DIR=%CD%"

:: Ensure output is a directory (fixes: '"output" is not a directory')
if exist "%RUN_DIR%\output\" (
  rem output exists and is a directory
) else (
  if exist "%RUN_DIR%\output" (
    rem output exists as file; unlock and delete then create folder
    attrib -R "%RUN_DIR%\output" >nul 2>&1
    del /f /q "%RUN_DIR%\output" >nul 2>&1
  )
   mkdir "%RUN_DIR%\output" >nul 2>&1
)

:: Logs will always be in build/output
set "TRANS_LOG=%RUN_DIR%\output\fortify_translate_%project%_%buildID%.log"
set "SCAN_LOG=%RUN_DIR%\output\fortify_scan_%project%_%buildID%.log"

echo [INFO] Current dir: %CD%
echo -----------------------------------------------------

set "prefix=%cd%\"
set /a srcCount=0

for /d %%D in (*) do (
  for /f "delims=" %%S in ('dir /ad /s /b "%%D\src" 2^>nul') do (
    set /a srcCount+=1

    rem -- Relative path under the working root
    set "rel=%%S"
    set "rel=!rel:%prefix%=!"

    rem -- Strip trailing "\src" if present
    set "component_dir_full=!rel!"
    set "component_dir=!rel!"
    if /i "!component_dir:~-4!"=="\src" set "component_dir=!component_dir:~0,-4!"

    rem -- COMPONENT (forward slashes) and COMPONENT_NAME (underscores)
    set "COMPONENT=!component_dir_full:\=/!"
    set "COMPONENT_NAME=!component_dir:\=_!"

    rem -- FPR file name per component
    set "FPR_FILE=!project!_!COMPONENT_NAME!_!version!_!buildID!.fpr"

    rem -- BASE_NAME = folder name immediately before \src
    for %%Z in ("!component_dir!") do set "BASE_NAME=%%~nZ"

    rem --- Add this line ---
    set "BASE_NAME=!BASE_NAME!_!buildID!"


    rem -- Build name per component (unique per build/component)
    set "BUILD_NAME=!project!_!COMPONENT_NAME!_!buildID!"
    set "SOURCE_DIR=%%S"
    set "excludeList=**\Test*.java;**\*Test*.java"
    rem Guard: SOURCE_DIR must exist
    if not exist "!SOURCE_DIR!" (
      echo [ERROR] Source directory not found: "!SOURCE_DIR!"
      popd
      exit /b 220
    )

    rem -- Print variables for visibility
    echo [PATH]           !rel!
    echo COMPONENT:       !COMPONENT!
    echo COMPONENT_NAME:  !COMPONENT_NAME!
    echo FPR_FILE:        !FPR_FILE!
    echo BASE_NAME:       !BASE_NAME!
    echo BUILD_NAME:      !BUILD_NAME!
    echo [INFO] SCAN: !COMPONENT_NAME!
    echo [INFO] SOURCE_DIR: !SOURCE_DIR!

    rem =====================================================
    rem ================   SCAN PER COMPONENT   =============
    rem =====================================================

    rem --- ensure we are inside SOURCE_DIR before translate/scan ---
        pushd "!SOURCE_DIR!" >nul 2>&1
        if errorlevel 1 (
          echo [ERROR] Cannot cd to SOURCE_DIR: "!SOURCE_DIR!"
          exit /b 221
        )

    :: Optional clean when YAML says so
    if /i "!clean_build!"=="true" "%SA%" -b "!BASE_NAME!" -clean

    :: TRANSLATE (use -logfile to avoid redirection issues)
    echo [INFO] TRANSLATE
   rem===============commited line for check==========================
   REM  "%SA%" -b "!BASE_NAME!" -verbose !translation_options! -exclude "!excludeList!" -logfile "!TRANS_LOG!" "!SOURCE_DIR!"



    set "CP_ARG="

    if exist "!COMP_JAR!" (
      set "CP_ARG= -cp ^"!COMP_JAR!^""
    ) else (
     echo [WARN] COMP_JAR not found
     echo [WARN] Continuing translate WITHOUT classpath.
   )


   "%SA%" -b "!BASE_NAME!" -verbose !translation_options! -exclude "!excludeList!" -logfile "!TRANS_LOG!" !CP_ARG!  "!SOURCE_DIR!"


    if errorlevel 1 (
      echo [ERROR] TRANSLATE failed. See "!TRANS_LOG!"
      popd & exit /b 202
    )

    :: SCAN (write FPR into output directory; unlock any stale file first)
    echo [INFO] SCAN
    set "FPR_OUT=%RUN_DIR%\output\!FPR_FILE!"

    if exist "!FPR_OUT!" (
      attrib -R "!FPR_OUT!" >nul 2>&1
      del /f /q "!FPR_OUT!" >nul 2>&1
    )

    "%SA%" -Xmx20480m -b "!BASE_NAME!" -scan -f "!FPR_OUT!" !scan_options! -logfile "!SCAN_LOG!"
    if errorlevel 1 (
      echo [ERROR] SCAN failed for build "!BASE_NAME!". Showing last 100 lines of scan log:

       set "STATUS=FAILED"
       set "RESULT_FPR=!FPR_OUT!"

      echo -----------------------------------------------------
      powershell -NoProfile -ExecutionPolicy Bypass -Command ^
        "if (Test-Path -LiteralPath '""!SCAN_LOG!""') { Get-Content -Tail 100 -LiteralPath '""!SCAN_LOG!""' } else { Write-Host 'SCAN_LOG not found: !SCAN_LOG!' }"
      echo -----------------------------------------------------
      popd
      exit /b 203
    )

    if exist "!FPR_OUT!" (
      echo [DONE] FPR created: "!FPR_OUT!"

      set "STATUS=SUCCESS"
      set "RESULT_FPR=!FPR_OUT!"
      call :SEND_WEBHOOK

    ) else (
      echo [ERROR] FPR not found after SCAN. See "!SCAN_LOG!"

      set "STATUS=FAILURE"
      set "RESULT_FPR=!FPR_OUT!"

       popd
      exit /b 204
    )
    echo(
  )
)

echo -----------------------------------------------------
echo [INFO] Total components discovered: !srcCount!

popd >nul 2>&1
exit /b 0


:: =========================
:: WEBHOOK (per component)
:: =========================
:SEND_WEBHOOK
setlocal EnableDelayedExpansion

REM === Build JSON payload safely using PowerShell ===
 set "BUILD_ID=%buildID%"
 set "BUILD_TYPE=%buildType%"

for /f "usebackq delims=" %%J in (`
  powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -Command ^
  "$payload = [ordered]@{ SCAN_TYPE=$env:SCAN_TYPE; USERNAME=$env:USERNAME; status=$env:STATUS; FPR_FILE=$env:RESULT_FPR; COMPONENT=$env:COMPONENT ; BASENAME=$env:BASE_NAME ; buildType=$env:BUILD_TYPE ; buildID=$env:BUILD_ID }; ConvertTo-Json -Compress -InputObject $payload"
`) do (
  set "PAYLOAD=%%J"
)


if not defined PAYLOAD (
  echo [ERROR] Payload is empty. PowerShell JSON build failed.
  endlocal & exit /b 302
)



echo [INFO] Webhook payload: !PAYLOAD!

REM === POST the payload ===
for /f "tokens=*" %%H in ('curl -s -o nul -w "%%{http_code}" -X POST ^
     -H "Content-Type: application/json" ^
     -d "!PAYLOAD!" ^
     "!WEBHOOK_URL!"') do set "HTTP_STATUS=%%H"

echo [INFO] Webhook HTTP status: !HTTP_STATUS!

set "STATUS_FIRST_DIGIT=!HTTP_STATUS:~0,1!"
if /I "!STATUS_FIRST_DIGIT!"=="2" (
  echo [OK] Webhook call completed successfully.
  endlocal & exit /b 0
)

echo [ERROR] Webhook call failed (HTTP !HTTP_STATUS!).
endlocal & exit /b 301
