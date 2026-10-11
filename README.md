# vivo-xflip-camera

针对 vivo X Flip 外屏优化的 Android 相机适配项目，目标是让外屏预览和成片方向可控，研究外屏启动相机时的系统限制，并使用合法可用的计算摄影接口。

## 项目进度

当前处于技术基线阶段，仓库还没有 Android 应用源码。已完成 CameraX/Camera2 能力和授权边界的初步核查，并建立前摄、后摄、预览、成片、旋转与外屏状态的验收清单。

- [技术基线与实机验收清单](docs/technical-baseline.md)
- [开发日志与待办](DEVELOPMENT_LOG.md)

## 技术方向

优先运行时查询 vivo X Flip 上可用的 CameraX/Camera2 厂商扩展。Android 官方已知支持设备列表包含 Vivo X Flip，但具体镜头和模式仍需在设备上逐项查询。Google Camera 的专有 APK、库、素材或代码不会在未获授权时复制或分发。

外屏可用区域、窗口尺寸和旋转必须运行时检测；682×422 仅作设备背景参考，不作为硬编码参数。

## 验证状态

目前没有 vivo X Flip 外屏实机验证，也没有应用构建结果。接下来会先搭建最小 CameraX 预览/拍照工程，再分别核对前摄和后摄的预览镜像与保存照片镜像。
