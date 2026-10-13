# 构建、验证与当前待办

## 实现范围

- app：在前台启动自己的 CameraX Activity；另有独立 JPEG 分享检查页面，后台只读采样，不开相机、不改原图。
- Android 13/API 33 起运行，窗口尺寸和旋转由运行时获取，不硬编码外屏分辨率。
- 前后摄的两项镜像偏好分别保存，默认关闭。预览使用 CameraX MirrorMode；照片显式使用 ImageCapture.Metadata，包含 false 值，不用预览 View 的 scaleX 再翻转成片。
- Preview 和 ImageCapture 共享 ViewPort，尺寸或旋转变化后更新绑定；同尺寸/旋转的显示屏迁移可能遗漏重绑定，displayId 缓存修复仍待接续。
- 退出前台释放相机；拍照使用不可变偏好快照与 CaptureGate token，旧回调不会解锁恢复前台后的新拍照。
- 照片保存到 MediaStore 的 Pictures/XFlipCamera。诊断显示窗口、显示屏 ID、旋转、公开摄像头信息、最近成片 EXIF 与错误类型。未读取到有效 EXIF 时显示 unknown。
- 当前不集成 Google Camera 专有算法，也未实现厂商 HDR/Night 扩展；沿用已有合法集成研究，不把标准 CameraX 结果称为 GCam 成像。

## 工具版本

| 工具/依赖 | 固定版本 |
| --- | --- |
| JDK | 17 |
| Gradle | 8.11.1 |
| Android Gradle Plugin | 8.10.0 |
| compileSdk / targetSdk | 36 |
| SDK Build Tools | 35.0.0 |
| CameraX core/camera2/lifecycle/view | 1.6.2 |
| Activity | 1.10.1 |
| ExifInterface | 1.4.2 |

[AGP 官方兼容表](https://developer.android.com/build/releases/agp-8-10-0-release-notes)确认该工具链支持 API 36、Gradle 8.11.1 和 JDK 17。[CameraX 官方发布说明](https://developer.android.com/jetpack/androidx/releases/camera)确认 1.6.2 稳定版。预览 MirrorMode 在固定版本中仍按实验接口显式采用；需要真机确认厂商行为。

## 可复现命令

方向核心不依赖 Android SDK、网络或第三方测试库：

```sh
bash scripts/check_core.sh
java scripts/ParseJava.java
```

完整 Android 构建需要安装以上 JDK、Gradle 与 Android SDK，能访问 Google Maven/Maven Central，并配置 ANDROID_HOME 或本地 SDK 路径：

```sh
gradle --no-daemon :camera-core:check :app:assembleStandardDebug :app:assembleCoverCompatDebug :app:lintStandardDebug :app:lintCoverCompatDebug
```

0.1.6-dev 的 standard APK 路径是 app/build/outputs/apk/standard/debug/app-standard-debug.apk，包名 com.sturgeon.xflipcamera；coverCompat 路径是 app/build/outputs/apk/coverCompat/debug/app-coverCompat-debug.apk，包名 org.codeaurora.snapcam。后者用于复现兼容假设，改包名未赋予系统相机权限。GitHub Actions 使用同一组明确的 flavor 任务并上传两个 APK 和 Lint 报告；lintDebug 在该配置中会有歧义。

本机 Windows 使用 JDK 21 编译 Java 17 目标、共享 SDK 和 Gradle 8.11.1，双 APK、双 Lint、核心及原始 JPEG 检查均已执行通过。0.1.6 coverCompat 已安装并通过合盖系统回传实测，standard 只完成构建；CI 与本地 debug 签名可能不同，不能仅以包名相同直接更新已装版本。新结果与限制见 device-test-2026-10-13.md，以下各节保留前几轮历史记录。

## 本轮已执行的验证

- Java 核心通过 JDK 17 编译，启用 -Xlint:all 与 -Werror。
- 13,511 个断言通过，包含 EXIF 1..8 非对称图案固定样例、35 种矩形尺寸的像素一一映射/边界/反向变换、镜像分类、两项镜像偏好互不污染，旧/重复拍照回调保护、RGB 全零/微弱像素区分与 JPEG 解码预算。
- Manifest 和资源 XML 已解析；工作流 YAML 已解析，内容权限为只读，设置 20 分钟超时。
- Android 应用源码进行语法检查，类型和依赖解析仍由真正的 Android 构建确认。
- 当前本地环境没有 Android SDK 与 Gradle；本地没有执行 APK 构建、Android Lint 或真机测试。新增 CI 定义不等于 CI 已成功，远程结果需单独核验。

## 当前验证与下一步

新增 PhotoEvidenceChecks 在 Gradle check 中用原始 SHA-256 与 JVM ImageIO 独立解码两张设备 JPEG，不将连接或保存成功等同于图像验收。此轮本地不能读取仓库二进制，原始 JPEG 重解码及新 APK/Lint 已在远程 CI 执行通过，见下方新版记录；本地合成像素、尺寸预算与 Java 语法已检查。

1. P0：实机负责人继续 ID/合盖策略探测，接续 displayId 重绑定与实际相机/流状态诊断；验收为明亮场景的真实预览和有效 JPEG。
2. P1：验收新 JPEG 分享检查入口、黑图与明亮图对照；有效图像恢复后执行完整镜像与折叠矩阵。
3. P2：继续厂商扩展查询和合法成像接入，保持 Google Camera 授权边界。

剩余额度、实际模型和推理强度无法从当前接口读取；按用户已授权规则继续独立工作。新工具使用方法、代码审查发现和设备待验收项见 code-review-2026-10-13.md。

## 首版远程构建已核验

- 源码提交：efe0150d275bc5b84a19e65228c52e93e0e531f3。
- [Android build 实际结果](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38191883873)：成功。
- 实际日志包含核心检查 PASS 13486 assertions、:app:assembleDebug、:app:lintDebug 和 BUILD SUCCESSFUL。Android 编译与 Lint 已执行通过。
- artifact：xflip-camera-debug，5922640 bytes；上传日志确认包含 2 个文件，即配置中指定的 debug APK 和 Lint HTML 报告。APK 及报告从上方构建页面的 Artifacts 下载。
- archive digest：sha256:c25389d165b5dd59db3fdbf2bf5a4c8b5a680a8460a18dff71451d57ad3bd00b。
- 当前 artifact 保留至 2026-10-19T01:28:25Z；源代码与构建脚本保留在仓库，可以重新构建。
- 成功构建仍不代表 X Flip 外屏、折叠状态或镜像实拍验收通过。

## 0.1.1-diagnostics 远程验证

- 验证源码：800935ea24ad1638749dd4ed0cae8985a7a868e9。
- [实际 CI 结果与 APK 下载](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38331262454)：completed / success，assembleDebug、lintDebug、13,511 个核心断言和 photoEvidenceChecks 全部通过。
- JVM ImageIO 重解码两张原始 JPEG，20,758,528 个完整 RGB 像素均为零，两个 SHA-256 与实机报告匹配。这是原始证据复核，不能写成新版本实机测试。
- artifact xflip-camera-debug，ID 11782463018，ZIP 5928273 bytes；上传日志确认包含 APK 与 Lint 报告。archive digest sha256:f8285f504436ea7b4ebd03f639d121a52cecb02fb1954e545ac3e35b18973f33，当前保留至 2026-10-20T01:16:11Z。
- 新分享入口、Android 解码采样、临时 URI 权限、损坏文件、连续选择和外屏布局仍待有线实机负责人验证；未声称相机黑屏已修复。
