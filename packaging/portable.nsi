; SoundMesh Single-EXE Portable Launcher (NSIS, Unicode, Silent Self-Extractor)
Unicode True

!ifndef APP_VERSION
  !define APP_VERSION "1.2.0"
!endif

!define APP_NAME "SoundMesh"
!define APP_EXE "SoundMesh.exe"
!define APP_SOURCE_DIR "..\build\compose\binaries\main\app\SoundMesh"
!define OUTPUT_FILE "..\build\SoundMesh-${APP_VERSION}-Portable.exe"

Name "${APP_NAME} ${APP_VERSION} Portable"
OutFile "${OUTPUT_FILE}"
Icon "icon.ico"
SilentInstall silent
AutoCloseWindow true
RequestExecutionLevel user

SetCompressor /SOLID lzma
SetCompressorDictSize 32

VIProductVersion "${APP_VERSION}.0"
VIAddVersionKey "ProductName" "${APP_NAME} Portable"
VIAddVersionKey "ProductVersion" "${APP_VERSION}"
VIAddVersionKey "FileDescription" "${APP_NAME} Single-EXE Portable"
VIAddVersionKey "FileVersion" "${APP_VERSION}"
VIAddVersionKey "CompanyName" "${APP_NAME}"
VIAddVersionKey "LegalCopyright" "MIT License"

Section ""
  StrCpy $0 "$LOCALAPPDATA\Temp\SoundMesh-Portable-${APP_VERSION}"
  IfFileExists "$0\${APP_EXE}" launch extract

  extract:
    SetOutPath "$0"
    File /r "${APP_SOURCE_DIR}\*.*"

  launch:
    SetOutPath "$0"
    Exec '"$0\${APP_EXE}"'
SectionEnd
