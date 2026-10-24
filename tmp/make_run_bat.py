# @anchor: mkrun_intro
# 生成脚本：以「纯 ASCII 文本 + CRLF 行尾」重写项目根目录的 run.bat，
# 规避 cmd 在含多字节字符 / LF 行尾的批处理上 goto 标签跳转偏移错乱（表现为 'mpile' 这类报错）。
# 用法：python tmp/make_run_bat.py（在项目根目录或 tmp 目录下执行均可）

# @anchor: mkrun_content
# run.bat 的正文：全部英文 + 单字节字符，结构与原脚本一致（run/compile/clean/help 四模式）
LINES = [
    "@echo off",
    "setlocal",
    "title maze-solver",
    "REM --------------------------------------------------------------",
    "REM Windows one-key build and run helper.",
    "REM Usage: run.bat [run|compile|clean|help]   (default: run)",
    "REM NOTE: this file MUST stay pure ASCII with CRLF line endings.",
    "REM       cmd.exe miscounts byte offsets for goto/call labels when the",
    "REM       file contains multi-byte characters or LF-only line endings,",
    "REM       which makes a label jump land in the middle of a line",
    "REM       (e.g. 'call mvn -B compile' turning into 'mpile').",
    "REM       Regenerate it with: python tmp/make_run_bat.py",
    "REM --------------------------------------------------------------",
    "",
    "REM ===== [setup] pin working dir to the script folder, print summary =====",
    'cd /d "%~dp0"',
    'set "MODE=%~1"',
    'if "%MODE%"=="" set "MODE=run"',
    "echo ==================================================",
    "echo  maze-solver  build and run helper",
    "echo  project dir : %CD%",
    "echo  mode        : %MODE%",
    "echo ==================================================",
    "",
    "call :check_env",
    "if errorlevel 1 goto :fail",
    "",
    'if /i "%MODE%"=="help" goto :usage',
    'if /i "%MODE%"=="-h" goto :usage',
    'if /i "%MODE%"=="/?" goto :usage',
    'if /i "%MODE%"=="clean" goto :do_clean',
    'if /i "%MODE%"=="compile" goto :do_compile',
    'if /i "%MODE%"=="run" goto :do_run',
    'echo [WARN] unknown mode "%MODE%", falling back to run',
    "goto :do_run",
    "",
    "REM ===== [usage] help text =====",
    ":usage",
    "echo Usage: run.bat [mode]",
    "echo   (no arg) / run : mvn compile then mvn javafx:run (needs a desktop)",
    "echo   compile        : compile main sources only, output tmp\\target\\classes",
    "echo   clean          : delete the build dir tmp\\target",
    "echo   help           : show this help",
    "goto :done",
    "",
    "REM ===== [compile] compile main sources only =====",
    "REM 'mvn' is a batch wrapper itself, so it must be invoked through CALL,",
    "REM otherwise this script would terminate at that line.",
    ":do_compile",
    "echo [1/1] compiling main sources: mvn compile",
    "call mvn -B compile",
    "if errorlevel 1 goto :fail",
    "echo [OK] compiled, output dir: tmp\\target\\classes",
    "goto :done",
    "",
    "REM ===== [launch] compile then start the JavaFX window =====",
    "REM javafx:run blocks until the window is closed; a non-zero exit code",
    "REM usually means there is no desktop environment available.",
    ":do_run",
    "echo [1/2] compiling main sources: mvn compile",
    "call mvn -B compile",
    "if errorlevel 1 goto :fail",
    "echo [2/2] starting UI: mvn javafx:run",
    "call mvn -B javafx:run",
    "if errorlevel 1 goto :launch_failed",
    "echo [OK] application exited normally.",
    "goto :done",
    "",
    ":launch_failed",
    "echo [ERROR] UI launch failed. javafx:run needs a desktop; without one use: run.bat compile",
    "goto :fail",
    "",
    "REM ===== [clean] remove the build dir only =====",
    "REM build.directory points to tmp\\target, so clean only touches that folder.",
    ":do_clean",
    "echo [1/1] cleaning build dir: mvn clean",
    "call mvn -B clean",
    "if errorlevel 1 goto :fail",
    "echo [OK] tmp\\target removed.",
    "goto :done",
    "",
    "REM ===== [check_env] prerequisites (detect only, never auto-install) =====",
    "REM java and mvn are hard requirements; a missing or old javac only warns",
    "REM and lets maven report the real error.",
    ":check_env",
    "where java >nul 2>&1",
    "if errorlevel 1 goto :env_no_java",
    "where mvn >nul 2>&1",
    "if errorlevel 1 goto :env_no_mvn",
    "where javac >nul 2>&1",
    "if errorlevel 1 goto :env_no_javac",
    'javac -version 2>&1 | findstr /R /C:"^javac 1[7-9]" /C:"^javac 2[0-9]" >nul',
    "if errorlevel 1 goto :env_old_jdk",
    "goto :env_ok",
    "",
    ":env_no_java",
    "echo [ERROR] java not found. Install JDK 17+ and add its bin folder to PATH.",
    "exit /b 1",
    "",
    ":env_no_mvn",
    "echo [ERROR] mvn not found. Install Maven 3.8+ and add it to PATH.",
    "exit /b 1",
    "",
    ":env_no_javac",
    "echo [WARN] javac not found (JRE only?). mvn compile needs JDK 17+.",
    "goto :env_ok",
    "",
    ":env_old_jdk",
    "echo [WARN] JDK looks older than 17 (pom.xml targets 17).",
    "goto :env_ok",
    "",
    ":env_ok",
    "echo [OK] environment check passed.",
    "exit /b 0",
    "",
    "REM ===== [fail] / [done] shared epilogue: keep the window open =====",
    ":fail",
    "echo.",
    "echo [FAILED] see the log above.",
    "echo Press any key to close...",
    "pause >nul",
    "endlocal",
    "exit /b 1",
    "",
    ":done",
    "echo.",
    "echo Done. Press any key to close...",
    "pause >nul",
    "endlocal",
    "exit /b 0",
    "",
]


# @anchor: mkrun_write
# 写出 run.bat（CRLF）并自检：无 CR 缺失行、无 BOM、无非 ASCII 字节
def write_bat(path):
    data = "\r\n".join(LINES).encode("ascii")
    with open(path, "wb") as f:
        f.write(data)
    return data


# @anchor: mkrun_verify
# 逐项校验文件字节：ASCII / 无 BOM / 行行以 CRLF 结尾 / 无孤立 LF / 标签与跳转一一对应
def verify(path):
    with open(path, "rb") as f:
        raw = f.read()
    problems = []
    if raw.startswith(b"\xef\xbb\xbf"):
        problems.append("found UTF-8 BOM")
    bad_ascii = [i for i, b in enumerate(raw) if b > 127]
    if bad_ascii:
        problems.append("non-ASCII bytes at %r" % bad_ascii[:5])
    lone_lf = raw.replace(b"\r\n", b"").count(b"\n")
    if lone_lf:
        problems.append("%d LF without CR" % lone_lf)
    if not raw.endswith(b"\r\n"):
        problems.append("file does not end with CRLF")

    lines = [ln.strip() for ln in raw.decode("ascii").splitlines()]
    labels = set(ln[1:].strip().lower() for ln in lines
                 if ln.startswith(":") and not ln.startswith("::"))
    for need in ("usage", "do_compile", "do_run", "do_clean", "check_env",
                 "fail", "done", "env_ok", "launch_failed"):
        if need not in labels:
            problems.append("missing label :%s" % need)
    for ln in lines:
        low = ln.lower()
        for kw in ("goto :", "call :"):
            if low.startswith(kw):
                target = low[len(kw):].split()[0]
                if target not in labels:
                    problems.append("jump to undefined label :%s" % target)
    return raw, problems



# @anchor: mkrun_main
# 入口：生成 -> 自检 -> 打印结论与文件头若干行
def main():
    import os
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(here)
    target = os.path.join(root, "run.bat")
    raw = write_bat(target)
    raw, problems = verify(target)
    print("written:", target)
    print("bytes:", len(raw), "lines:", raw.count(b"\r\n"))
    if problems:
        print("PROBLEMS:")
        for p in problems:
            print("  -", p)
        raise SystemExit(1)
    print("all checks passed: ASCII / no BOM / CRLF only")
    for line in raw.split(b"\r\n")[:6]:
        print(repr(line))


if __name__ == "__main__":
    main()
