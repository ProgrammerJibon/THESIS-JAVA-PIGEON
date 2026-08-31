@echo off
setlocal

set "BASE=main"
set "OUT=all.files.codes.txt"

echo Clearing old file and creating blank one for "%OUT%"

:: Using echo/ avoids parsing errors and creates a clean blank file
> "%OUT%" echo(

for /r "%BASE%" %%F in (*.java *.xml) do (
    echo(
    echo packing up %%F
    
    echo %%F:>>"%OUT%"
    echo ```>>"%OUT%"
    type "%%F">>"%OUT%"
    echo(>>"%OUT%"
    echo ```>>"%OUT%"
    echo(>>"%OUT%"
    
    echo packing ok %%F
)

for %%F in (package.json index.js app.json) do (
    if exist "%%F" (
        echo %%F:>>"%OUT%"
        echo ```>>"%OUT%"
        type "%%F">>"%OUT%"
        echo( >>"%OUT%"
        echo ```>>"%OUT%"
        echo( >>"%OUT%"
    )
)

echo(
echo Done: %OUT%
pause
start "" "%OUT%"
exit
