# 构建、验证与当前待办

## 实现范围

- app：仅在前台启动自己的 CameraX Activity，支持前后摄切换、拍照、分别控制预览镜像和照片镜像。
- Android 13/API 33 起运行，窗口尺寸和旋转由运行时获取，不硬编码外屏分辨率。
- 前后摄的两项镜像偏好分别保存，默认关闭。预览使用 CameraX MirrorMode；照片显式使用 ImageCapture.Metadata，包含 false 值，不用预览 View 的 scaleX 再翻转成片。
- Preview 和 ImageCapture 共享 ViewPort，窗口/屏幕旋转变化后更新绑定。
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
gradle --no-daemon :camera-core:check :app:assembleDebug :app:lintDebug
```

成功后 APK 路径是 app/build/outputs/apk/debug/app-debug.apk。GitHub Actions 的 Android build 工作流使用相同检查，并在成功后上传 APK 和 Lint 报告。

## 本轮已执行的验证

- Java 核心通过 JDK 17 编译，启用 -Xlint:all 与 -Werror。
- 13,486 个断言通过，包含 EXIF 1..8 非对称图案固定样例、35 种矩形尺寸的像素一一映射/边界/反向变换、镜像分类、两项镜像偏好互不污染，以及旧/重复拍照回调保护。
- Manifest 和资源 XML 已解析；工作流 YAML 已解析，内容权限为只读，设置 20 分钟超时。
- Android 应用源码进行语法检查，类型和依赖解析仍由真正的 Android 构建确认。
- 当前本地环境没有 Android SDK 与 Gradle；本地没有执行 APK 构建、Android Lint 或真机测试。新增 CI 定义不等于 CI 已成功，远程结果需单独核验。

## 下一步

1. P0 已完成：远程 Android 构建/Lint 与方向核心检查通过，debug APK 已上传。后续代码变更继续使用同一工作流验证。
2. P1：在 X Flip 外屏与内屏分别测试前后摄、四个方向、两项镜像开关。使用 technical-baseline.md 的非对称卡片，核对图库和独立 EXIF 解码显示，不能仅凭 EXIF 标记证明 JPEG 像素方向正确。
3. P2：取得 ROM/Android 版本和折叠/切屏复现日志，确认“翻开手机”提示来自系统、启动器还是其他相机应用。
4. P3：增加并在设备上验证 CameraX Extensions 能力查询及可用模式，保持 Google Camera 授权边界。

缺少设备只阻塞第 2 至 4 项的真机部分。剩余额度、实际模型和推理强度当前无法从运行接口读取；按用户已授权的规则说明并继续独立工作，不编造余额或型号。

## 远程构建已核验

- 源码提交：efe0150d275bc5b84a19e65228c52e93e0e531f3。
- [Android build 实际结果](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38191883873)：成功。
- 实际日志包含核心检查 PASS 13486 assertions、:app:assembleDebug、:app:lintDebug 和 BUILD SUCCESSFUL。Android 编译与 Lint 已执行通过。
- artifact：xflip-camera-debug，5922640 bytes；上传日志确认包含 2 个文件，即配置中指定的 debug APK 和 Lint HTML 报告。APK 及报告从上方构建页面的 Artifacts 下载。
- archive digest：sha256:c25389d165b5dd59db3fdbf2bf5a4c8b5a680a8460a18dff71451d57ad3bd00b。
- 当前 artifact 保留至 2026-10-19T01:28:25Z；源代码与构建脚本保留在仓库，可以重新构建。
- 成功构建仍不代表 X Flip 外屏、折叠状态或镜像实拍验收通过。
