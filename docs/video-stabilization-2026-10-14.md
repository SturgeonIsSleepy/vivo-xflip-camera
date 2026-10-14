# 外屏录像录后防抖

续接 1ecf2b8，按用户的实时左右纠正 → 视频防抖 → 影像算法顺序推进。使用现有系统相机回传 MP4，原片只读，处理结果另存 Movies/XFlipCamera。

## 实现与限制

每秒采样 10 帧，最长边 160 像素，估计相邻帧的全局平移。亚像素局部最小值拟合避免将相邻匹配位置误判为不同匹配；低纹理、不同场景、重复纹理与超出搜索范围的运动不参与轨迹。局部线性拟合平滑轨迹，保留恒速平移；变化时间按实际时间戳插值。

Media3 Transformer / effect 1.9.0 对每一输出帧应用矩阵补偿，H.264 重编码、保留音频，放大 1.10 倍约损失 9.1% 的可见宽/高，平移限制防止露出黑边。用 PendingMediaWrite 验证单条发布并回滚新文件，不删除原片。退出页面取消处理，处理期间保持应用前台。

这是录后平移防抖，第一版支持 0.3 至 60 秒 SDR 视频；没有新增实时 EIS，不补偿旋转、透视、滚动快门或大幅运动，不含 Google 专有算法。重编码会产生画质与体积变化，不能把成片较小当作画质提升。用户无法展开，内屏/展开验收继续待办。

## 实测

实际设备 V2256A / Android 14 / CLOSED / 无 override / display 1。0.2.8-dev / 16 的本地与手机 APK SHA256 均为 37090646c34bd3cde61f952f4f8a06dacf5ef32779fa67d9d448a29f53442e44。

- 合成测试通过同一应用 UID 的实际解码、GL 矩阵、编码和 MediaStore 发布，41 个样本、40 个可靠位移。输出 709,677 bytes，H.264 + AAC，120 帧全部保留，显示尺寸 360×640，时长约 4.02 秒。
- 独立 FFmpeg 全帧解码、跟踪绿色标记并扣除放大倍率：横向残留抖动 14.4%、纵向 34.3%，黑边比例 0。数据只代表该已知运动合成片段，不代表所有手持场景或 OIS 效果。
- 原生录像实时预览仍正向，录制中计数 27，00:00:01 正常；停止确认返回，实际 MP4 1,961,072 bytes。外屏点击“视频防抖”后成功另存 873,704 bytes，显示“防抖视频已另存，原片保留”。源与输出均为 720×1280 显示尺寸、H.264、AAC、约 3.43 秒。真实素材只保留本地，不上传视频、画面或 GPS。
- 播放验收发现原预览图和播放 Surface 同时显示，后续 0.2.9 改为居中视频并隐藏底图，完成时恢复预览图；其完整包复验另行记录。

## 复现

调试 APK 内的 StabilizationDeviceTest 只由 ADB instrumentation 显式启动。测试会将输入放进本应用 files 并在相册生成一条测试输出，不自动处理其他文件。APK 不包含 FFmpeg；它仅用于电脑端独立验证。

```powershell
python -m pip install numpy Pillow imageio-ffmpeg
python scripts/generate_video_fixture.py OUTPUT_DIR
python scripts/test_stabilization_device.py --serial SERIAL --adb PATH_TO_ADB OUTPUT_DIR/synthetic-shake.mp4 OUTPUT_DIR/synthetic-stabilized.mp4
python scripts/verify_stabilized_video.py OUTPUT_DIR/synthetic-shake.mp4 OUTPUT_DIR/synthetic-stabilized.mp4 OUTPUT_DIR/report.json
```

保持合盖、外屏解锁和既有外屏助手可用。该 ROM 的无前台 instrumentation 会暂停运行并产生超时，脚本从 Shell 立即将自有入口带到外屏；不修改系统电池设置。public evidence 中的两个 MP4 是生成的纹理/标记/正弦音，均不是相机拍摄的私人场景。

## 参考

- [Media3 Transformer](https://developer.android.com/media/media3/transformer/getting-started)
- [MatrixTransformation 的时间戳及归一化坐标](https://developer.android.com/reference/androidx/media3/effect/MatrixTransformation)
- [Media3 1.9.0 源码与许可证](https://github.com/androidx/media/tree/1.9.0)
- [MediaMetadataRetriever 帧采样](https://developer.android.com/reference/android/media/MediaMetadataRetriever)
- [独立测试用 imageio-ffmpeg](https://github.com/imageio/imageio-ffmpeg)
