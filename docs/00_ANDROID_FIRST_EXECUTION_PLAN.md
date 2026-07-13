# Android First Execution Plan

G0 后只执行 AH0，然后 AA0。Android 工程放在 `android/`；不得创建 `ios/`。

- AH0：环境、CameraX Preview/ImageCapture/ImageAnalysis、权限、旋转、两台设备；
- AA0：MediaPipe vs ML Kit Pose；必要时 MoveNet；
- AA1：Bundle Importer、参考图、骨架 Overlay、单条指导、拍照；
- Android Gate 通过后才可申请 iOS0。
