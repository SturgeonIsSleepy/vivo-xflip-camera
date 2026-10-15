# 2026-10-15 完整 GPU、原生控件与水印实机整合

起始提交 dfd7d89。0.3.1-dev / versionCode 19 为同一个完整 APK 的客户端与 Shell 工作者，不再依赖昨日混合版本实验。实际 V2256A、Android 14、CLOSED、display 1、682×422、无折叠 override，未更改全机 DPI 或性能设置。

## 已验证

- 安装后桥接就绪，冷启动直接进入系统拍照取景。仍需要现有外屏助手及电脑初始化 Shell 桥接；这是已初始化条件下的入口验证，尚未实现免电脑或重启自动恢复。
- 左设置是原生外屏对话框，可配置照片文字水印及默认关闭的实验录后平移防抖。打开设置暂停绘制，关闭恢复；设置窗口在实际外屏内。
- 右侧照片/录像按钮各是独立小窗口。通过关闭自己启动的 for-result 子相机，等待原预览服务销毁，再打开下一模式，避免并发相机及旧停止回调影响新渲染。录制、确认和原生设置阶段移除所有按钮窗口，原生快门和确认正常。
- 全包视频预览 1,494 帧时平均 28.6 fps、capture_misses=0；确认前 1,546 帧平均 28.4 fps。亮处纸面“正↑”和日历数字在照片及录像实时取景方向正确。unknown_controls 保留当前方向，不能据此承诺所有方向/长期场景均无跳变。
- 拍照有效原图 3,072×4,096 / 4,927,254 bytes，确认后文字副本另存 2,542,503 bytes，方向 1、显示尺寸不变，原图保留。实际文字为 vivo X Flip 和拍摄时间，目测完整。底部覆盖黑条，不扩展画布；主体像素独立重解码平均差异约 1.16/255（JPEG 重编码）。这是自制文字水印，未接通系统蔡司。
- 本机真实 FD 元数据写入成功，Make/Model 保留。原图只有空 GPS IFD 指针，不能声称真实坐标保留已验收；合成 GPS 的原生图形测试另行通过。公开收据只含哈希、维度和统计，不含真实场景、坐标或手机序列号。
- 实际视频确认回传后保存，独立完整解码 48 帧、720×1,280、24 fps、2.01 秒、H.264/AAC。录制 phase=2 / 确认 phase=3 均正确，未误把录像待机圆钮当录制方块。
- 控件就绪后连续六次照片→录像→照片交替成功，返回后服务退出，已保存媒体可恢复。自动化 900ms 连点曾在控件尚未出现时点到原生取消，改为等待窗口显示后可复验；不将这个短暂加载窗口称为已消除，也未加入固定延迟到产品流程。

## 构建与安装一致性

两个 flavor assemble、各自 Lint、核心 15,377 个断言、两套各 8 个 native graphics 水印回归全部通过，最终构建 BUILD SUCCESSFUL in 1m 9s。原始 JPEG 独立证据检查同时通过。

实测 coverCompat APK 19,982,560 bytes，SHA256 `184c3c582f261abf2900cc54b4d12ca1e1daf5112f20e56997cdf3a86a8714de`；手机已安装 base.apk 与本地完全相同。安装通过合盖外屏文件管理完成。CI 签名包与此本地签名实测包分开识别。

[无私有画面的机器收据](evidence/integration-2026-10-15.json)

初始化：`scripts/start_preview_bridge.ps1 -Serial <serial> -Adb <adb.exe> -Package org.codeaurora.snapcam -DisplayId 1`。使用安装 APK 中的工作者；换包后重启桥接，避免混版。

## 后续边界

原生拍摄仍需点“确定”；原生相机约两分钟闲置会自动退出。设置按钮下方仍可见部分原生齿轮，中央取景点击对焦被既有覆盖层阻挡，视频计时背景仍有局部拼接。未进行开合屏/不同画幅/全旋转矩阵及长时间录像压力验收。

自动防抖配置仅调用已有录后平移算法，不代表效果升级。成熟 OpenCV 光流/RANSAC 和旋转补偿仍待实现及真实运动对照；Google Camera 算法未接通。下一步代码 agent 完成独立 AffineStabilizationPlan 与合成旋转/平移测试，检查 agent 审查本轮结果归属、取消/恢复、设置暂停和窗口清理；所有协作只通过仓库。

## 远程成果收据

源码 [059f28e](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/commit/059f28e7eace0fb07a33e6e868fc279e4aa3c42c) 已推送并核对 main。对应 [CI 38720404954](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38720404954) completed / success；下载的两份 JUnit XML 各 tests=8、failures/errors/skipped=0，ZIP digest 11ae7f52c0141bbfcfa22349d821b7f371f87e5aec04f57a1dad8304d917c027 与 artifact 11965910260 一致。xflip-camera-debug / 11966055164 保存 CI APK 与 Lint，15,579,640 bytes；与本地签名实测包分开。

[下载 0.3.1 实测包](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.3.1-cover-controls-test)。Release 413678632 / asset 643497848，公开预发布；tag 精确指向源码 059f28e，远程 state=uploaded、19,982,560 bytes、digest 与本地及手机安装包三者一致。不是昨日未完成的 0.3.0 草稿。

同一已安装完整包追加照片预览观测：24.282 秒从 41 至 744 帧，区间 28.95 fps，phase 始终 1、capture_misses=0、unknown_controls 保持 3。窗口列表实见三个独立控件窗口，最终截图文字方向正确；返回后预览服务已退出，无本应用崩溃记录。这仍不是长期稳定性保证。
