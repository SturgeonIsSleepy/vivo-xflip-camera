# vivo-xflip-camera

针对 vivo X Flip 外屏优化的 Android 相机适配项目，目标是让外屏预览和成片方向可控，研究外屏启动相机时的系统限制，并使用合法可用的计算摄影接口。

## 项目进度

已建立最小 Android CameraX 工程：前后摄切换、取景拍照、预览/照片镜像独立控制、窗口与摄像头诊断、成片 EXIF 检查。两个镜像开关默认关闭，照片保存到 Pictures/XFlipCamera。0.1.1-diagnostics 新增独立“照片像素检查”分享入口，后台采样 JPEG 的 RGB 与 EXIF；无需开启相机，结果仅辅助取证，不代表黑屏或镜像已修复。

0.1.6-dev 新增“设置 → 系统相机”回传路径：已在实际合盖外屏连续拍摄并返回两张有效 JPEG，取景和成像由 vivo 原生相机完成。本应用的 CameraX 预览仍黑，原生预览仍有镜像现象，Google 算法尚未接通。独立外屏入口、镜像与折叠验收仍待完成，见[本轮实机报告](docs/device-test-2026-10-13.md)。

[下载本轮实测 APK](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.1.6-device-test)。预发布附件就是已在手机安装并验证的 coverCompat 包，SHA256 已与远程资产及手机 base.apk 核对；CI 构建包与本地 debug 签名可能不同。

独立 Java 方向核心已通过本地编译及 13,486 个断言检查；0.1.1-diagnostics 的远程 Android APK 构建、Lint、13,511 个核心断言及原始 JPEG 独立重解码均已通过，已产出 debug APK。[第一版有线实机测试](docs/device-test-2026-10-12.md)未通过外屏验收：合盖预览及两张成片全黑，系统仍提示翻开手机；原生相机对照可正常预览。镜像方向与外屏独立使用仍待修复后验收。

- [技术基线与实机验收清单](docs/technical-baseline.md)
- [构建、验证与当前待办](docs/build-and-verify.md)
- [第一版实机结果、原始证据与两个 agent 的下一轮任务](docs/device-test-2026-10-12.md)
- [独立 JPEG 像素检查与本轮代码审查](docs/code-review-2026-10-13.md)
- [开发日志与待办](DEVELOPMENT_LOG.md)
- [每天 02:00 开发（含周末和法定假期）](docs/work-task.md)
- [中国工作日 09:00 检查任务](docs/morning-review-task.md)

当前源码 0.2.2-dev 的实时预览仍处于待实机验证的开发检查点。本轮独立修复成片另存的发布失败误报与清理异常，保留原图；进展和静态审查边界见 [保存与生命周期审查](docs/code-review-2026-10-14.md)。

## 技术方向

优先运行时查询 vivo X Flip 上可用的 CameraX/Camera2 厂商扩展。Android 官方已知支持设备列表包含 Vivo X Flip，但具体镜头和模式仍需在设备上逐项查询。Google Camera 的专有 APK、库、素材或代码不会在未获授权时复制或分发。

外屏可用区域、窗口尺寸和旋转必须运行时检测；682×422 仅作设备背景参考，不作为硬编码参数。

## 验证状态

本地已验证 EXIF 1..8 的非对称图案、像素映射和反向变换，预览/成片镜像偏好独立性，以及旧拍照回调不能解锁新拍照的生命周期保护。可运行 bash scripts/check_core.sh 复查。

[当前源码双 APK 构建、Lint 与 13,595 个核心断言](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38446311535)已核验成功，通过 Artifacts 中的 xflip-camera-debug 可下载两个 flavor 的 CI APK 及各自 Lint 报告。CameraX 外屏真实预览仍失败，公开系统相机回传路径已有有效成片。待完成：独立外屏入口、预览及成片方向、折叠切屏、Google 成像及逐镜头厂商扩展能力。

