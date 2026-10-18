; Установщик w0y для Windows (Inno Setup 6). Ставит для текущего пользователя, права администратора не нужны.
; Сборка: iscc /DAppVersion=0.0.4-beta-1 /DSourceDir=<папка w0y после createDistributable> installer\w0y.iss
#ifndef AppVersion
  #define AppVersion "0.0.0"
#endif
#ifndef SourceDir
  #define SourceDir "..\build\compose\binaries\main\app\w0y"
#endif

[Setup]
AppId={{7B1D0C52-4A67-4D2B-9E8C-5A3F0E1D6B77}
AppName=w0y
AppVersion={#AppVersion}
AppPublisher=TexFi
AppPublisherURL=https://github.com/texfi-w0y/texfi-w0y
DefaultDirName={localappdata}\Programs\w0y
DefaultGroupName=w0y
PrivilegesRequired=lowest
OutputDir=..\build\installer
OutputBaseFilename=w0y-windows-x64-setup
Compression=lzma2/max
SolidCompression=yes
ArchitecturesAllowed=x64compatible
ArchitecturesInstallIn64BitMode=x64compatible
SetupIconFile=..\src\main\resources\icon.ico
UninstallDisplayIcon={app}\w0y.exe
WizardStyle=modern
DisableProgramGroupPage=yes
CloseApplications=yes

[Languages]
Name: "russian"; MessagesFile: "compiler:Languages\Russian.isl"
Name: "english"; MessagesFile: "compiler:Default.isl"
Name: "ukrainian"; MessagesFile: "compiler:Languages\Ukrainian.isl"
Name: "polish"; MessagesFile: "compiler:Languages\Polish.isl"

[Tasks]
Name: "desktopicon"; Description: "{cm:CreateDesktopIcon}"; Flags: unchecked

[Files]
Source: "{#SourceDir}\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{group}\w0y"; Filename: "{app}\w0y.exe"
Name: "{userdesktop}\w0y"; Filename: "{app}\w0y.exe"; Tasks: desktopicon

[Run]
Filename: "{app}\w0y.exe"; Description: "{cm:LaunchProgram,w0y}"; Flags: nowait postinstall skipifsilent

; Данные пользователя (%APPDATA%\w0y) при удалении не трогаются: это его библиотека и настройки.
