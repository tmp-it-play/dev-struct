# DevStruct

지정한 폴더 아래의 git 레포를 `소유자/레포명` 구조로 자동 정리해주는 macOS 메뉴바 앱입니다.

- 레포 이름이 바뀌거나 소유권이 이전되면 폴더와 git remote를 맞춰줍니다.
- fork는 `OpenSource` 폴더로 모읍니다.
- JetBrains IDE가 켜져 있으면 기다렸다가 IDE가 꺼지면 옮기고, IDE의 프로젝트 경로도 함께 바꿉니다.

## 실행

`gh` CLI에 로그인되어 있어야 합니다.

```sh
./kotlin run -m desktopApp
```
