@echo off
timeout /t 3 >nul
del "D:\TCP\TCP\target\classes"
move /Y "C:\Users\16563\AppData\Local\Temp\TCP-update1196438347695800423\TCP-new.jar" "D:\TCP\TCP\target\classes"
start "" "D:\TCP\TCP\target\classes"
rmdir /s /q "C:\Users\16563\AppData\Local\Temp\TCP-update1196438347695800423"
del "%~f0"
