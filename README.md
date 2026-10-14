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

0.2.9-dev 已在合盖外屏验证拍照、录像的实时左右纠正及录后视频防抖，确认回传、原片保留、另存和播放正常。防抖为平移补偿，0.3 至 60 秒 SDR 视频，约 9.1% 可见边缘裁切；还没有实时 EIS 和 Google Camera 成像算法。需电脑初始化 Shell 桥接，覆盖层会挡住取景点击对焦，录像计时背景有局部拼接。见 [防抖与最终包报告](docs/video-stabilization-2026-10-14.md)和 [实时翻转报告](docs/live-preview-2026-10-14.md)。

[下载实时翻转实测 APK](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.2.6-preview-test)，手机、本地与远程附件 SHA256 三者一致，初始化脚本及限制见上述报告。

[下载最新防抖实测 APK](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.2.9-stabilization-test)。打开“外屏系统相机”，运行初始化脚本后直接拍照/录像；录像确认返回后点“视频防抖”，结果另存、原片保留。真实录像源/输出均 82 帧、720×1280、约 2.74 秒并保留音频；合成输入独立测量仅代表该测试场景。

0.3.0-dev 为预览性能候选版：GPU 路径在混合调试会话实测从约 10 fps 提高到约 29 fps，识别漏检保持方向。双版本构建/Lint、13,817 项断言通过，完整候选包因手机断线尚未安装复验。界面重排、水印和更成熟防抖仍待开发，见 [性能收尾与边界](docs/preview-performance-2026-10-14.md)。

[0.3.0 CI 构建包](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38583427109)已上传到 Artifacts。它的签名可能不同于手机现有包；本地签名候选 APK 直传因网络超时未完成，空 Release 已转草稿。

独立“照片像素检查”现已保存最近一次分享/选择器选中的照片及文字报告，避免页面重建后回读最初分享的照片；已完成报告直接恢复。状态核心通过 35 个新增场景断言，页面恢复和临时权限仍待实机验收，见同一审查文档。

## 技术方向

优先运行时查询 vivo X Flip 上可用的 CameraX/Camera2 厂商扩展。Android 官方已知支持设备列表包含 Vivo X Flip，但具体镜头和模式仍需在设备上逐项查询。Google Camera 的专有 APK、库、素材或代码不会在未获授权时复制或分发。

外屏可用区域、窗口尺寸和旋转必须运行时检测；682×422 仅作设备背景参考，不作为硬编码参数。

## 验证状态

本地已验证 EXIF 1..8 的非对称图案、像素映射和反向变换，预览/成片镜像偏好独立性，以及旧拍照回调不能解锁新拍照的生命周期保护。可运行 bash scripts/check_core.sh 复查。

[当前源码双 APK 构建、Lint 与 13,783 个核心断言](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38564399301)已核验成功，Artifacts 中的 xflip-camera-debug 包含两个 flavor 的 CI APK 及各自 Lint 报告。CameraX 外屏仍黑，系统回传与 Shell 预览纠正路径已在合盖实机验证。待完成：初始化独立性、取景点击对焦、更多运动/方向与折叠切屏、Google 成像及逐镜头厂商扩展能力。

