@echo off

SET Location="%CD%"
if not exist "%Location%\bin" (
    echo You need to run this file from within the same directory.
    exit /B 1
)
SETX /M PATH "%PATH%;%Location%\bin"

REG ADD "HKCR\*\shell\SkateKotlinRun" /ve /d "Run with Skate" /f
REG ADD "HKCR\*\shell\SkateKotlinRun" /v "AppliesTo" /d ".kt" /f
REG ADD "HKCR\*\shell\SkateKotlinRun\command" /ve /d "%Location%\bin\skate.bat """%%1"""" /f

REG ADD "HKCR\*\shell\SkateKotlinEdit" /ve /d "Edit with Skate" /f
REG ADD "HKCR\*\shell\SkateKotlinEdit" /v "AppliesTo" /d ".kt" /f
REG ADD "HKCR\*\shell\SkateKotlinEdit\command" /ve /d "%Location%\bin\skate.bat -e """%%1"""" /f

REG ADD "HKCR\*\shell\SkateKotlinInteractive" /ve /d "Run Interactive with Skate" /f
REG ADD "HKCR\*\shell\SkateKotlinInteractive" /v "AppliesTo" /d ".kt" /f
REG ADD "HKCR\*\shell\SkateKotlinInteractive\command" /ve /d "%Location%\bin\skate.bat -i """%%1"""" /f
