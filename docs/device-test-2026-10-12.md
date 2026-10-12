# 第一版 vivo X Flip 有线实机验收

日期：2026-10-12，北京时间

结论：第一版未通过外屏相机验收。合盖外屏能够运行 Activity、操作设置和保存 JPEG，但预览及两张成片均为全黑，仍出现系统“翻开手机以使用摄像头”提示。有效画面恢复前，不能宣称镜像问题已解决。

## 测试基线

- 仓库检查起点：776a9b867271ac434be2f30cb9993a092b56af5f
- 实际 APK 对应源码：efe0150d275bc5b84a19e65228c52e93e0e531f3
- GitHub Actions：run 38191883873，artifact 11722060632
- artifact ZIP SHA256：c25389d165b5dd59db3fdbf2bf5a4c8b5a680a8460a18dff71451d57ad3bd00b
- APK SHA256：8329d69e6a0b460e18c87748595c4168e44c04cf72ca21d43ee45674b7436659
- 从手机拉取已安装 base.apk，哈希与上面的原始 APK 完全一致
- 应用：com.sturgeon.xflipcamera，versionName 0.1.0-dev，versionCode 1
- 手机：V2256A，Android 14 / API 34，ROM PD2256_A_14.1.16.1.W10.V000L1
- device_state：mBaseState 与 mCommittedState 都为 CLOSED，mOverrideState=Optional.empty，没有软件模拟折叠状态
- 外屏：displayId 1，682×422，display_rotation=0，物理 installOrientation=ROTATION_270
- 初始外屏 override density=160，本轮触控受其他前台页面切换干扰后曾变为 200，已用 wm density 160 -d 1 恢复，并以 dumpsys display 核验

## 已执行结果

| 项目 | 结果 | 证据与限制 |
| --- | --- | --- |
| APK 安装 | 成功，依赖一次用户手动安装 | ADB install 与 pm install 未完成，解锁后仍等待；用户通过文件管理安装后，已拉取 base.apk 核对哈希 |
| 合盖外屏冷启动 | Activity 启动成功 | am start 返回 COLD / Status: ok，外屏窗口和预览均为 682×422，display_id=1；使用 ADB 指定显示屏，不代表已具备独立外屏入口 |
| 后摄预览 | 失败 | 黑屏，并出现“翻开手机以使用摄像头”系统提示，确认后仍黑屏 |
| 系统提示定位 | 来源已确认 | com.vivo.daemonService 的 com.vivo.camera.action 窗口，UID 1000，displayId=1，type=2009；与 APK 安装入口的 FlipToContinue 是两个不同现象 |
| 相机权限与连接 | 有权限、有连接 | CAMERA granted=true；CameraService 记录应用连接 Camera ID 0，预览流曾产生 948 帧，但这不证明像素有效 |
| 外屏拍照 | 文件保存成功，图像验收失败 | 两次快门各产生 62,824 字节 JPEG，解码后每个 RGB 通道最小值与最大值均为 0 |
| 无遮挡对照 | 第一版仍全黑 | 用户确认镜头朝向明亮物体且无遮挡后，第二张依然全黑 |
| vivo 原生相机对照 | 真实预览成功 | 同一合盖状态、同一场景，原生相机可看到物体与文字；CameraService 显示它连接 ID 4。场景和图库缩略图仅本地保留，不公开上传 |
| 前后摄切换 | 前摄路径不可用 | 切换按钮显示“另一摄像头不可用”；应用诊断仅枚举 ID 0、1，均 facing=1、sensor_rotation=90 |
| 默认镜像与 EXIF | 只确认元数据 | preview_mirror=false、photo_mirror=false；成片 EXIF=6，reflection=false。黑图没有非对称参照，不能验收像素方向 |
| 预览镜像开关 | 偏好写入已确认 | 实际 shared_prefs 为 back.preview=true、back.photo=false；切预览引发重绑定后系统提示再次出现。仅证明这次偏好不互相污染 |
| 照片镜像开关、四方向、前摄镜像 | 未验收 | 前台切到其他应用后的点击被排除；没有将未触发的拍照或设置操作计为成功 |
| Google Camera 算法 | 第一版未实现 | 源码是普通 CameraX，既没有 Google Camera 成像集成，也没有厂商 Extensions 调用 |

相机诊断显示保存后 last_error=none、last_capture=back, requested_mirror=false，并报告 EXIF 6，但文件像素全部为黑。后续版本必须明确区分“文件写入成功”和“有效画面通过验收”。

## 可获取的原始证据

[证据目录](evidence/2026-10-12-v1/)

- camera-policy-dialog.png：仅包含相机界面及系统提示
- camera-black-preview.png：关闭提示后的黑色预览
- camera-switch-unavailable.png：切镜头提示
- camera-saved-black.png：文件保存成功的 UI
- diagnostics.txt：从应用诊断弹窗的 UI XML 提取的完整文本
- system-excerpts.txt：系统窗口、显示信息、权限、CameraService 的必要摘录，省略无关应用和设备标识
- photo-default-1.jpg、photo-default-2.jpg：手机原始 JPEG，经检查为全黑、无 GPS 标签
- photo-analysis.json：独立 Pillow 解码的像素范围、尺寸、EXIF、哈希
- inspect-photos.py：可复现上述照片检查

不上传完整全机日志、其他应用页面、真实场景和图库缩略图。原始 APK 已在仓库 Actions artifact 中，本次不重复把 15 MB APK 提交到 Git。

## 复现命令

以下命令用 -d 选择唯一 USB 实机；多台 USB 设备时改用 -s，设备序列号无需上传。

```powershell
adb -d shell dumpsys device_state
adb -d shell am force-stop com.sturgeon.xflipcamera
adb -d shell am start -W --display 1 -n com.sturgeon.xflipcamera/.MainActivity
adb -d shell dumpsys window windows
adb -d shell dumpsys media.camera
adb -d shell dumpsys SurfaceFlinger --display-id
```

外屏逻辑 ID 与 screencap 使用的 SurfaceFlinger 物理 ID 不同，先查询再截图，不硬编码到产品。

等待相机绑定后在外屏确认系统提示。设置中打开“诊断”，可用 uiautomator dump 保存 UI XML，只提取 package=com.sturgeon.xflipcamera 的 text。点击快门后检查 Pictures/XFlipCamera，并拉取新拍的 JPEG。

```powershell
python docs/evidence/2026-10-12-v1/inspect-photos.py
```

本轮全局 logcat 中没有可用的当前应用 TAG 或 PID 日志，历史 AndroidRuntime 错误未归因于本应用。定位依赖实际窗口、CameraService、UI 诊断与独立 JPEG 解码。

## 给两个 agent 的下一轮任务

代码撰写 agent 优先解决有效图像路径：

1. 做小规模、可单独构建的 Camera2 诊断，记录运行时公开 ID、facing、sensor orientation、可用流与当前显示屏。调查为什么本应用仅见 0、1，而原生外屏相机使用 4。可以把查询 ID 4 作为受控实验，须报告是否拒绝、不可见或能打开，不能把 ID 4 直接写成产品默认或保证第三方可访问。
2. 为 CameraX 记录相机状态、PreviewView stream state、实际选中 ID 和 Surface 生命周期，补齐无法从普通 logcat 取得时的外屏可读诊断。判断系统策略、黑帧来源与渲染状态，不能仅移除提示或调整 scaleX。
3. 按同一基线构建下一版 APK，先证明合盖下有真实预览与真实成片，再推进独立外屏入口、无内屏权限流程与非对称卡片镜像验收。

基础错误检查 agent 负责独立复核：

1. 运行随证据上传的照片检查，复核 APK 基线、黑像素和 EXIF，保持镜像验收未通过状态。
2. 检查 MainActivity 的 bindCamera、默认 selector、摄像头可用性、显示变更、Surface 绑定及 CaptureGate 回调；重点复核“另一摄像头不可用”的静态配置和设备证据，区分合盖时公开摄像头枚举与常规前后摄假设。
3. 下一版执行核心检查、assembleDebug 与 lintDebug，上传明确源码 SHA 对应的 APK，并在本仓库报告结果；设备验收交回有线测试负责人。

Google Camera 需求单独保持待办。可以核查用户已有相机应用的外屏兼容性或合法开放成像路径，但调用第三方 App、厂商 HDR 和 Google 内部算法是不同方案，必须写清实际实现及验证边界。

协作入口：[Issue 1](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/issues/1)

本轮结束已停止测试相机、清除本轮新建镜像偏好恢复默认，并确认没有活动 CameraService 客户端。未修改相机源码，未声称已修复系统相机策略。
