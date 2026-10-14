# 实时预览性能收尾

用户反馈 0.2.9 卡顿、偶尔翻回镜像，要求按 vivo X Flip 特调。旧版实测 8.08 秒从 10 至 92 帧，约 10.15 fps。

## 已完成

- 取景画面直接用 HardwareBitmap 在 HardwareCanvas 渲染，去掉每帧整屏回读 CPU 和重新上传纹理。按钮判断改为约 45×105 小块、约 7.5 Hz 回读；仍每帧核对实际相机前台。
- 帧调度按约 30 fps 的完整周期计时，去掉“渲染完成后再固定睡 67 ms”。矩形及反射方法复用，空捕获暂保留上一正确帧。
- PreviewPhaseState 在识别漏检时保持方向，只有明确的确认页才撤掉翻转。返回预览要求连续识别，启动过渡使用黑帧。原生设置关闭图标单独识别，避免把设置文字当作取景镜像。
- 旧渲染线程清理时检查 generation，不能清掉新会话的画面。新增状态、帧率、捕获耗时与漏检计数；初始化脚本对“102 启动失败”立即退出，避免误把 Shell 成功退出当作相机启动成功。

## 验证边界

实机实验为客户端 0.2.9 + 首轮 0.3.0 Shell 工作者，真实合盖 display 1。8.07 秒从 18 至 254 帧，约 29.24 fps；3 次控制识别漏检后仍保持 phase=1，画面文字正向，capture_misses=0。此为工作者路径实测，不能等同最终完整 APK 已验收。

最终 0.3.0-dev / versionCode 18 的双 flavor assemble/Lint、13,817 项核心断言与原始 JPEG 全图/哈希通过，BUILD SUCCESSFUL in 37s。新增回归覆盖连续 20 次漏检、拍照/录像/确认、返回预览及原生设置状态。

完整包准备安装时手机已断开，未安装最终包，原生设置识别、拍照确认、录像及长时间连续稳定性仍待安装复验。用户要求睡前收尾，不再请求重新连接。APK 作为候选包上传，已有实测 0.2.9 Release 保留。

源码 [5ff5040](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/commit/5ff5040e305c9a9628cda0593f21aa29a6d84077) 已保存。[对应 CI](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38583427109) completed/success，artifact xflip-camera-debug / 11903658385 已含两个 CI APK 与 Lint 报告，ZIP 15,550,646 bytes。直接上传本地签名候选 APK 因网络传输超时失败，本地文件保留；空 Release 已转草稿，不能作为下载入口。CI 签名可能与手机现有安装不同。

## 后续任务

1. 打开直接进入拍照预览，右侧拍照/录像切换，左侧原生设置，遵循实际 display/window 尺寸。
2. 已实际查看外屏原生设置：只有倒计时、画幅、触屏/手掌/语音快门，没有发现蔡司水印或防抖开关；未验收深层/内屏设置。先确认系统水印是否能作用于 IMAGE_CAPTURE，不可用则自制文字水印、原图保留。
3. 用户认为平移防抖偏弱，优先核对原生防抖，再用成熟的光流与稳健运动估计处理旋转/平移并做真实抖动对照。OpenCV 官方提供 Android Maven AAR；本轮只确认依赖可取得，未接入，已移除未使用依赖和 ABI 限制，避免把库下载称为防抖升级。

性能证据仅包含统计，不公开真实画面。没有更改系统 DPI、电池策略或全机性能设置，也没有新增 Google Camera 算法。

参考：[OpenCV Android 支持](https://opencv.org/opencv4android-usage-models/)、[官方光流教程](https://docs.opencv.org/4.x/d4/dee/tutorial_optical_flow.html)。
