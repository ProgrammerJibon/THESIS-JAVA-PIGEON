@echo off
setlocal

set "BASE=main"
set "OUT=checker.output.txt"

> "%OUT%" echo.

for /r "%BASE%" %%F in (*.java *.xml) do (
    echo %%F:>>"%OUT%"
    echo ```>>"%OUT%"
    type "%%F">>"%OUT%"
    echo.>>"%OUT%"
    echo ```>>"%OUT%"
    echo.>>"%OUT%"
)

for %%F in (package.json index.js app.json) do (
    if exist "%%F" (
        echo %%F:>>"%OUT%"
        echo ```>>"%OUT%"
        type "%%F">>"%OUT%"
        echo.>>"%OUT%"
        echo ```>>"%OUT%"
        echo.>>"%OUT%"
    )
)

echo Done: %OUT%
pause