# vivo X Flip 外屏相机技术基线

核查日期：2026-10-12

## 当前状态与本轮目标

仓库此前只有 README 和初始提交，没有 Android 源码、测试或开发日志。本轮优先确认可合法调用的成像能力、预览与成片的镜像边界、折叠屏运行时状态接口，并建立后续实机验收标准。

## Google Camera 与合法成像路径

Android 官方文档提供 Camera2 与 CameraX Extensions，用于调用设备厂商通过 OEM vendor library 暴露的 Auto、Bokeh、人像修饰、HDR、Night 等模式。它们覆盖预览和静态照片，不覆盖视频；设备与摄像头是否支持某一模式必须在运行时查询。

Android 官方已知支持设备列表包含 Vivo X Flip，但列表不穷尽，且没有列出该机每个摄像头的具体模式。实现时要针对前、后摄像头分别查询可用模式，不能仅凭设备名称推定能力。

本轮查阅的 Android 官方开发文档没有给出把 Google Camera 应用内部计算摄影流程嵌入第三方应用的公开 SDK。这个结论仅限本次查阅范围，不代表对所有 Google 合作接口的否定。当前安全路线是先验证 X Flip 可用的 CameraX/Camera2 厂商扩展；未授权前不复制或分发 Google Camera APK、专有库、素材或代码。扩展不可用时，使用标准 CameraX/Camera2，并在产品说明中明确它与 Google Camera 成像结果不同。

CameraX Extensions 官方文档注明：从 2026-11-01 起，部分设备会移除 CameraX 1.5 及更早版本的扩展支持，1.6 及更新版本继续支持。开始实现时应选用兼容的稳定版本，并确认 camera-core、camera-camera2、camera-lifecycle、camera-extensions 版本一致。

## 预览、成片与旋转必须分开处理

CameraX Preview.Builder 的镜像模式有 OFF、ON、ON_FRONT_ONLY；默认是 ON_FRONT_ONLY。此设置从 CameraX 1.4.0 提供，在 Android 13/API 33 及以上生效，在 API 32 及以下无操作。不能假设系统默认行为在 X Flip 外屏、所有 Android 版本或自定义 Surface 上都一致。

ImageCapture.Metadata 提供左右反转标记，OutputFileOptions.Builder 可把元数据保存到 JPEG EXIF。EXIF 标记不等同于已经验证 JPEG 像素本身的镜像结果。验收时同时检查文件的像素方向与读取 EXIF 后的显示方向，避免图库正确、其他查看器错误或二次翻转。

相机旋转按每个 use case 的 target rotation 更新。CameraX 的 Preview 转换信息包含传感器旋转、目标旋转和 ViewPort；后续实现应由同一套状态计算预览方向、裁切和镜像，避免预览层和保存层重复应用变换。外屏窗口旋转、设备物理方向和相机传感器方向要分别记录。

建议的默认镜像策略：

| 摄像头 | 外屏实时预览 | 保存照片 |
| --- | --- | --- |
| 后摄 | 不左右翻转 | 不左右翻转 |
| 前摄 | 默认按自拍习惯镜像，可提供关闭选项 | 默认不镜像；用户显式开启“镜像自拍”后才镜像 |

这是待实机确认的产品默认值。最终行为需要与用户期望和图库显示结果一起核对。

## 外屏与“翻开手机”提示

DisplayManager 可以枚举系统当前公开的逻辑显示屏；应用窗口自己的实时尺寸和方向也必须在运行时读取。折叠屏状态可通过 Jetpack WindowInfoTracker 获取与当前窗口关联的 WindowLayoutInfo/DisplayFeature，DisplayFeature.bounds 是当前应用窗口坐标系中的区域。该接口用于折痕与窗口布局，不保证每家厂商都会公开外屏，也不能单独证明外屏相机启动权限。

Android 有后台启动 Activity 的安全限制，Android 14 对 PendingIntent 与跨应用 Service 的相关授权要求更严格。“翻开手机”提示可能来自 vivo 相机的产品策略、外屏 Activity 限制或系统启动路径；目前没有设备日志，不能认定根因。安全的首选验证路线是用户从外屏前台启动本项目自己的相机 Activity。若系统仍阻止第三方应用在外屏显示，需要 OEM 公开接口或系统授权；不尝试隐藏 API、伪造系统权限或绕过签名校验。

## 验收与复现清单

1. 在前摄、后摄分别显示一张不对称测试卡：左侧写“LEFT”，右侧画箭头，并包含偏离中心的标记。
2. 在内屏与外屏分别查看实时预览，再拍照；在图库和按 EXIF 旋转后的独立解码结果中核对左右方向。
3. 每个摄像头测试设备方向 0°、90°、180°、270°，并在折叠/展开及屏幕切换后重复。记录预览镜像策略、目标旋转、EXIF Orientation、文件像素方向。
4. 在 X Flip 上逐摄像头运行 CameraX Extensions 查询，记录 Auto、HDR、Night、Bokeh、人像修饰是否可用。不能以公开设备列表代替运行时检测。
5. 复现“翻开手机”：分别从系统外屏相机入口、外屏启动本项目、内屏启动本项目，再尝试前后摄像头。记录 ROM/Android 版本、用户操作路径、提示截图和提示出现时的 logcat 时间窗；分享日志前删除账号、序列号等个人信息。

## 本轮已知阻塞与待办

- 尚无 vivo X Flip 实机测试结果，无法确认外屏显示权限、具体扩展模式、前后摄像头输出方向或系统提示根因。
- P0：建立最小 CameraX 相机预览/拍照工程，并运行时读取当前窗口尺寸、显示方向和可用摄像头。
- P1：为预览镜像与保存照片镜像建立分离的状态和可重复测试，覆盖前摄/后摄及 EXIF。
- P2：在 X Flip 上查询 CameraX Extensions 的每摄像头支持情况，比较标准与扩展模式样张。
- P3：按复现清单定位“翻开手机”提示来源；若是系统策略，整理 OEM 授权接口需求。

## 官方参考

- [Android Camera extensions](https://developer.android.com/media/camera/camera-extensions)
- [CameraX Extensions API（含 2026-11-01 版本提示）](https://developer.android.com/media/camera/camerax/extensions-api)
- [CameraX 扩展已知支持设备](https://developer.android.com/media/camera/supported-devices)
- [Preview.Builder 镜像模式](https://developer.android.com/reference/androidx/camera/core/Preview.Builder)
- [SurfaceRequest.TransformationInfo](https://developer.android.com/reference/androidx/camera/core/SurfaceRequest.TransformationInfo)
- [ImageCapture.Metadata](https://developer.android.com/reference/androidx/camera/core/ImageCapture.Metadata)
- [ImageCapture.OutputFileOptions.Builder](https://developer.android.com/reference/androidx/camera/core/ImageCapture.OutputFileOptions.Builder)
- [CameraX 旋转处理](https://developer.android.com/media/camera/camerax/orientation-rotation)
- [DisplayManager](https://developer.android.com/reference/android/hardware/display/DisplayManager)
- [WindowInfoTracker](https://developer.android.com/reference/androidx/window/layout/WindowInfoTracker)
- [DisplayFeature.bounds](https://developer.android.com/reference/androidx/window/layout/DisplayFeature)
- [Android 后台 Activity 启动安全规则](https://developer.android.com/guide/components/activities/secure-bal)
- [Android 14 后台启动行为变更](https://developer.android.com/about/versions/14/behavior-changes-14)
- [AOSP CameraX 厂商扩展架构](https://source.android.com/docs/core/camera/camerax-vendor-extensions)
