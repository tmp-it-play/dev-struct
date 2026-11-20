#!/bin/sh
# DevStruct.app(런타임 포함)을 만들고 dmg로 묶는다. 사용: packaging/package-macos.sh [버전]
set -eu
cd "$(dirname "$0")/.."

version="${1:-$(sed -n 's/^const val APP_VERSION = "\(.*\)"/\1/p' shared/src@jvm/Updater.kt)}"
jpackage="${JAVA_HOME:+$JAVA_HOME/bin/}jpackage"

KOTLIN_CLI_NO_WELCOME_BANNER=1 ./kotlin package -m desktopApp -f executable-jar

rm -rf dist && mkdir -p dist/input
cp build/tasks/_desktopApp_executableJarJvm/desktopApp-jvm-executable.jar dist/input/devstruct.jar
"$jpackage" --type app-image \
    --input dist/input --main-jar devstruct.jar \
    --name DevStruct --app-version "$version" \
    --icon packaging/AppIcon.icns \
    --mac-package-identifier team.themoment.devstruct \
    --java-options --enable-native-access=ALL-UNNAMED \
    --dest dist
# 메뉴바 앱이라 Dock 아이콘을 숨긴다
plutil -insert LSUIElement -bool true dist/DevStruct.app/Contents/Info.plist
codesign --force --deep --sign - dist/DevStruct.app

# 응용 프로그램 폴더 바로가기를 넣어 끌어다 놓기로 설치하게 한다
mkdir dist/dmg
ditto dist/DevStruct.app dist/dmg/DevStruct.app
ln -s /Applications dist/dmg/Applications
dmg="dist/DevStruct-$version-macos-arm64.dmg"
hdiutil create -volname DevStruct -srcfolder dist/dmg -ov -format UDZO "$dmg"
echo "$dmg"
