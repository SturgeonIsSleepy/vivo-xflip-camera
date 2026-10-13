# 黑屏证据复核与独立像素检查

检查起点：c3f50ce4acbfb87c088490ecb9539e3b51f66e84。真实设备记录沿用 [首版实机报告](device-test-2026-10-12.md)，本轮没有连接或操作手机。

## 审查发现

- 实机报告、原始照片量化 JSON 与系统摘录一致：两个 JPEG 记录为 4096×2534、RGB 全零、EXIF=6；系统相机提示来自 vivo daemonService。CameraService 有连接和帧计数不能证明像素有效，元数据不含反射也不能证明镜像验收通过。
- 默认 selector 只按 front/back 选择。设备公开 ID 0、1 均报告 facing=1，前摄 selector 不可用与证据一致；这不能证明所有后摄候选都试过。原生相机使用 ID 4 是调查线索，不能直接硬编码成产品默认。
- bindCamera 的缓存比较 width、height、rotation，没有比较 displayId。如果窗口迁移到尺寸和旋转相同的显示屏，代码会直接返回，可能遗漏重绑定。此为静态路径发现，尚无对应实机复现，也不认定为本次全黑原因。
- bindToLifecycle 的返回 Camera 未保存，诊断只显示请求的前后摄与 CameraManager 枚举；没有实际选中 ID、CameraState 和 PreviewView stream state。后续需按绑定生命周期记录这些状态，STREAMING 也不能代替像素验收。
- CaptureGate 的停后台取消、旧/重复回调隔离及拍照偏好快照继续通过已有核心检查，没有证据需要改动该实现。

[Issue 1 的后续交接](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/issues/1)要求避免同时修改 MainActivity，实机负责人先进行 ID 与合盖策略探测。本轮保留 MainActivity 原 blob 1d8908144390b084c16e9ec9e306d982c7d9894d，只在独立文件实现下述工具；相机绑定修复保持待办。

## 新增可用工具

新增 PhotoInspectionActivity，图库把 JPEG 分享给“照片像素检查”即可读取，或通过该页面的“选择 JPEG”调用系统文档选择器。无需广泛图库权限，不开相机、不修改原图，也不自动上传照片。这个独立入口没有新增桌面图标。

JpegPixelInspector 在后台先读取尺寸，拒绝无效、非 JPEG 或超过 1 亿像素的输入，再按 2 的幂降采样，使解码长边不超过 512。报告包含原始/采样尺寸、RGB 每通道范围、非黑采样数、EXIF 与元数据反射信息。复制操作由用户点击触发。

- all_sampled_rgb_zero=true 仅说明被解码样本全零，不能确定系统策略、镜头遮挡或场景照度。
- false 仅说明有非零样本，不能证明真实场景、完整原图或镜像方向验收通过。
- 解码/权限失败、空输入与超出尺寸预算分别拒绝，不能伪装成全黑或检查通过。
- 手机页面使用采样降低内存开销，完整原始照片的量化仍交给独立离线检查。

应用版本为 0.1.1-diagnostics / versionCode 2，便于与首版区分。相机主页面的拍摄行为保持首版实现，新版本仍不代表解决了黑屏。

## 验证与复现

```sh
bash scripts/check_core.sh
java scripts/ParseJava.java
# 在完整仓库中独立解码两张原始 JPEG，需 JDK 17：
java -Djava.awt.headless=true -cp .build/core-checks \
  com.sturgeon.xflipcamera.core.PhotoEvidenceChecks
# 需要 Pillow：
python docs/evidence/2026-10-12-v1/inspect-photos.py
# 完整 Android 工具链：
gradle --no-daemon :camera-core:check :app:assembleDebug :app:lintDebug
```

coreChecks 新增全黑、各通道微弱像素、空输入、尺寸边界和乘法溢出检查。photoEvidenceChecks 在 Gradle check 中检查两个原始 JPEG 的 SHA-256、尺寸，并用 JVM ImageIO 完整解码后调用同一 RGB 统计核心，不能在缺少证据时跳过成功。

旧 inspect-photos.py 在目录无 JPEG 时输出 [] 并退出 0；本轮已复现并修复，现退出非零并说明未解码任何文件。

当前连接器不能读取仓库 JPEG 二进制，本地未重新解码真实照片；远程 CI 能 checkout 原始文件，已读回新版 CI 日志，确认两个原始 SHA-256 匹配，20,758,528 个完整解码像素均为 RGB=0，且 Android 构建与 Lint 通过。这不代表新页面已在设备执行。本地已执行的结果和后续远程核验统一记录在 DEVELOPMENT_LOG.md。

真机验收：安装新版本后，从图库分享原来的黑 JPEG，预期显示采样全零；再选一张已知明亮且有非对称文字的 JPEG，核对非零统计与 EXIF。验证无权限、损坏 JPEG、页面退出及连续选择时没有旧结果覆盖新结果。这些 Android 运行时检查均待有线测试负责人完成。

## 下一步

1. P0：实机负责人完成实际 Camera2 ID/权限、选中 ID、相机与预览流状态探测，并接续修复 displayId 缓存；验收为合盖明亮场景下预览与 JPEG 都有真实内容。
2. P1：有效图像路径恢复后，验收前后摄、内外屏、折叠切屏、四方向与独立镜像开关。当前像素检查可辅助取证，不代替该矩阵。
3. P2：继续合法成像接口与厂商扩展查询；未集成 Google Camera 专有算法。

官方依据：[BitmapFactory.Options](https://developer.android.com/reference/android/graphics/BitmapFactory.Options) 的尺寸预读与采样说明；[PreviewView](https://developer.android.com/reference/androidx/camera/view/PreviewView) 的流状态观察接口。没有更改现有两项任务的日程、提示或启用状态。
