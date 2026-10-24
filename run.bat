@echo off
setlocal
title maze-solver
REM --------------------------------------------------------------
REM Windows one-key build and run helper.
REM Usage: run.bat [run|compile|clean|help]   (default: run)
REM NOTE: this file MUST stay pure ASCII with CRLF line endings.
REM       cmd.exe miscounts byte offsets for goto/call labels when the
REM       file contains multi-byte characters or LF-only line endings,
REM       which makes a label jump land in the middle of a line
REM       (e.g. 'call mvn -B compile' turning into 'mpile').
REM       Regenerate it with: python tmp/make_run_bat.py
REM --------------------------------------------------------------

REM ===== [setup] pin working dir to the script folder, print summary =====
cd /d "%~dp0"
set "MODE=%~1"
if "%MODE%"=="" set "MODE=run"
echo ==================================================
echo  maze-solver  build and run helper
echo  project dir : %CD%
echo  mode        : %MODE%
echo ==================================================

call :check_env
if errorlevel 1 goto :fail

if /i "%MODE%"=="help" goto :usage
if /i "%MODE%"=="-h" goto :usage
if /i "%MODE%"=="/?" goto :usage
if /i "%MODE%"=="clean" goto :do_clean
if /i "%MODE%"=="compile" goto :do_compile
if /i "%MODE%"=="run" goto :do_run
echo [WARN] unknown mode "%MODE%", falling back to run
goto :do_run

REM ===== [usage] help text =====
:usage
echo Usage: run.bat [mode]
echo   (no arg) / run : mvn compile then mvn javafx:run (needs a desktop)
echo   compile        : compile main sources only, output tmp\target\classes
echo   clean          : delete the build dir tmp\target
echo   help           : show this help
goto :done

REM ===== [compile] compile main sources only =====
REM 'mvn' is a batch wrapper itself, so it must be invoked through CALL,
REM otherwise this script would terminate at that line.
:do_compile
echo [1/1] compiling main sources: mvn compile
call mvn -B compile
if errorlevel 1 goto :fail
echo [OK] compiled, output dir: tmp\target\classes
goto :done

REM ===== [launch] compile then start the JavaFX window =====
REM javafx:run blocks until the window is closed; a non-zero exit code
REM usually means there is no desktop environment available.
:do_run
echo [1/2] compiling main sources: mvn compile
call mvn -B compile
if errorlevel 1 goto :fail
echo [2/2] starting UI: mvn javafx:run
call mvn -B javafx:run
if errorlevel 1 goto :launch_failed
echo [OK] application exited normally.
goto :done

:launch_failed
echo [ERROR] UI launch failed. javafx:run needs a desktop; without one use: run.bat compile
goto :fail

REM ===== [clean] remove the build dir only =====
REM build.directory points to tmp\target, so clean only touches that folder.
:do_clean
echo [1/1] cleaning build dir: mvn clean
call mvn -B clean
if errorlevel 1 goto :fail
echo [OK] tmp\target removed.
goto :done

REM ===== [check_env] prerequisites (detect only, never auto-install) =====
REM java and mvn are hard requirements; a missing or old javac only warns
REM and lets maven report the real error.
:check_env
where java >nul 2>&1
if errorlevel 1 goto :env_no_java
where mvn >nul 2>&1
if errorlevel 1 goto :env_no_mvn
where javac >nul 2>&1
if errorlevel 1 goto :env_no_javac
javac -version 2>&1 | findstr /R /C:"^javac 1[7-9]" /C:"^javac 2[0-9]" >nul
if errorlevel 1 goto :env_old_jdk
goto :env_ok

:env_no_java
echo [ERROR] java not found. Install JDK 17+ and add its bin folder to PATH.
exit /b 1

:env_no_mvn
echo [ERROR] mvn not found. Install Maven 3.8+ and add it to PATH.
exit /b 1

:env_no_javac
echo [WARN] javac not found (JRE only?). mvn compile needs JDK 17+.
goto :env_ok

:env_old_jdk
echo [WARN] JDK looks older than 17 (pom.xml targets 17).
goto :env_ok

:env_ok
echo [OK] environment check passed.
exit /b 0

REM ===== [fail] / [done] shared epilogue: keep the window open =====
:fail
echo.
echo [FAILED] see the log above.
echo Press any key to close...
pause >nul
endlocal
exit /b 1

:done
echo.
echo Done. Press any key to close...
pause >nul
endlocal
exit /b 0
