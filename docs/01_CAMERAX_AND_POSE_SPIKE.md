# CameraX and Pose Spike

参考 `android/camera-samples`、MediaPipe Android Pose Landmarker 和 `googlesamples/mlkit`。使用 PreviewView + Compose Canvas；ImageAnalysis 使用 KEEP_ONLY_LATEST；推理不阻塞快门。所有 SDK 结果先映射成自有 Pose Domain。记录两台设备的 P50/P95、FPS、温度、包体和失败样本，ADR 选择唯一生产引擎。
