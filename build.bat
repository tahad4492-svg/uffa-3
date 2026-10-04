@echo off
echo Building UnstableFFA (needs JDK 21 and Maven installed)...
call mvn -q clean package
if exist target\UnstableFFA-1.0.0.jar (
  echo.
  echo DONE. Put target\UnstableFFA-1.0.0.jar in your server's plugins folder.
) else (
  echo Build failed - read the errors above.
)
pause
