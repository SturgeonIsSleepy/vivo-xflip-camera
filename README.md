# vivo-xflip-camera

针对 vivo X Flip 外屏优化的 Android 相机适配项目，目标是让外屏预览和成片方向可控，研究外屏启动相机时的系统限制，并使用合法可用的计算摄影接口。

## 项目进度

已建立最小 Android CameraX 工程：前后摄切换、取景拍照、预览/照片镜像独立控制、窗口与摄像头诊断、成片 EXIF 检查。两个镜像开关默认关闭，照片保存到 Pictures/XFlipCamera。

独立 Java 方向核心已通过本地编译及 13,486 个断言检查；远程 Android APK 构建与 Lint 均已通过，已产出 debug APK。X Flip 真机验收仍待完成，不能据此宣称已解决 vivo 的外屏限制。

- [技术基线与实机验收清单](docs/technical-baseline.md)
- [构建、验证与当前待办](docs/build-and-verify.md)
- [开发日志与待办](DEVELOPMENT_LOG.md)
- [每天 00:00 开发（含周末和法定假期）](docs/work-task.md)
- [中国工作日 09:00 检查任务](docs/morning-review-task.md)

## 技术方向

优先运行时查询 vivo X Flip 上可用的 CameraX/Camera2 厂商扩展。Android 官方已知支持设备列表包含 Vivo X Flip，但具体镜头和模式仍需在设备上逐项查询。Google Camera 的专有 APK、库、素材或代码不会在未获授权时复制或分发。

外屏可用区域、窗口尺寸和旋转必须运行时检测；682×422 仅作设备背景参考，不作为硬编码参数。

## 验证状态

本地已验证 EXIF 1..8 的非对称图案、像素映射和反向变换，预览/成片镜像偏好独立性，以及旧拍照回调不能解锁新拍照的生命周期保护。可运行 bash scripts/check_core.sh 复查。

[成功构建与 APK 下载入口](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38191883873)已核验，通过页面 Artifacts 中的 xflip-camera-debug 下载。待验证：X Flip 外屏启动、真实预览和成片方向、折叠切屏、逐镜头厂商扩展能力及“翻开手机”提示来源。

