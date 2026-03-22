@echo off
REM Runs the PowerShell helper without requiring a relaxed execution policy (default Restricted blocks .ps1).
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-single-node.ps1" %*
