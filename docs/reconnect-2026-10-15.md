# 2026-10-15 用户路径失败后的启动恢复修复

用户反馈 0.3.1 打开就是原始相机、经常翻转失败且没有功能键。此前实测只覆盖初始化后同一进程，未覆盖应用被系统清理后的正常入口，不能把该结果当作日常稳定性验收。起始 fc02852。

## 根因与修复

1. 实机翻转工作进程仍在，应用进程却已经重启，PreviewMirrorService 为 not_connected，三控件窗口均不存在。旧 Receiver 只在 static 保存一次性 Binder，应用重建无法重连；Shizuku.pingBinder 为真也不代表用户服务已经连接，却允许进入原生相机。现在在应用专属外部目录写短请求，已有 Shell 工作者只向固定 DUMP 权限接收器重新共享 Binder，仍校验真实 UID 2000/0。保留经授权 Shizuku 用户服务重连，但不再凭管理器存活就启动原生取景。
2. 强制停止应用后，原生子相机仍占据旧任务栈；再次打开入口可能只把原生页面带到前台，应用进程甚至没有启动。入口改 singleTask，重开清除上层原生页面并通过 onNewIntent 恢复；只保留一个正常启动图标，旧 MainActivity 外屏快捷入口转入同一个拍摄页，诊断仍可显式指定 diagnostics / camera_id。
3. 中断图形工作进程可能触发厂商 HWUI 的 native abort，Java catch 无法捕获。初始化脚本增加独立 Shell 监护进程，工作进程退出后自动重启；更新时先核对 /proc 中的监护标识再停止旧实例。新 Binder 接入时重建覆盖 Surface，避免把已弃用的缓冲队列交给替代工作进程而重复 native abort。监护不是免重启授权方案。

服务未连接时显示恢复/初始化提示，等待期间不进入未经纠正的原生取景；用户回到后台时不自动弹相机。原有公共系统拍摄、确认和原片保存逻辑保持。

## 最终包实机复验

0.3.2-dev / versionCode 21，真实 CLOSED、display 1、未设折叠 override。安装包与手机 base.apk SHA256 都是 `ed7eddc8b67c7ae3fdf4e2c4ca7fef0946fd1f7ace10769ca5677cf59fb02925`，19,850,408 bytes。使用同一个 APK 内的工作者和最新版初始化脚本。

- 脚本 scripts/test_preview_reconnect.ps1 对同一监护会话连续三次 am force-stop 后正常打开入口；三个新的应用 PID 均重新得到实时 phase=1、至少 10 个纠正帧和三个真实原生控件窗口，无重新初始化。最终包三次诊断耗时约 9.87 / 7.22 / 10.64 秒，含 ADB 启动及 dumpsys 采样，不能写成纯界面延迟；旧中间包较快的统计不替代最终包结果。第三次截图纸面文字方向正确，退出后服务停止。
- 保持同一应用与原生拍照会话，主动 kill 确认过的工作进程。监护自动创建新 PID，约 7.96 秒观察到实时纠正恢复；新 Surface 避免重复崩溃，随后左侧设置对话框实际可打开。未重跑初始化或改手机权限。
- 去掉工作者时曾实见本应用的“预览服务未连接，请初始化桥接后重试”，没有静默进入裸原生取景。旧中间包首次 force-stop 仍失败：分别定位了旧任务栈和 HWUI 崩溃，不把失败记录删掉或当成通过。
- 两 flavor assemble/Lint、15,377 项核心断言、两套各 8 项原生图形水印回归及原始 JPEG 证据通过，最终构建 BUILD SUCCESSFUL in 39s。此次只改启动/连接/Surface 恢复，未宣称增加防抖或 Google 成像算法。

[去除私有画面的机器收据](evidence/reconnect-2026-10-15.json)。真实截图仅保存在负责人本地。

## 使用与限制

手机已安装并初始化最终包。新版脚本 scripts/start_preview_bridge.ps1 随源码提供；更新 APK 后按脚本启动同版工作者，之后退出重开和本轮故障注入可自动恢复。手机重启、撤销授权或监护进程也被杀死仍需要重新初始化；没有实测物理 USB 拔线或整机重启后的自动授权，不能声称免电脑启动已经实现。

原生确认/闲置退出、取景触摸对焦遮挡、计时背景拼接、各种画幅/旋转及长期压力仍为既有待办。GPU 原生崩溃此次通过隔离和重建实现恢复，后续可把绘制迁回应用进程以消除远端 Surface 风险。代码 agent 的成熟防抖开发继续待办，检查 agent 优先审查新连接和任务栈生命周期。

## 远程收据与后续回归

源码 [3ed7e6e](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/commit/3ed7e6ea5005d911ce7f44cffc3639c1f01c5b2f) 与 [CI 38724970989](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38724970989) head 一致，CI success。下载的两套水印 JUnit XML 各 8 项通过，artifact 11967746158 ZIP SHA256 与远程一致；CI APK/Lint artifact 11968430618，15,583,784 bytes。

[下载 0.3.2 修复版及初始化脚本](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.3.2-reconnect-fix)。Release 413709949 / APK asset 643594007、脚本 asset 643600283 均 uploaded；APK digest 与手机/本地三者一致，脚本 SHA256 8f64831415d4ab46fa8502d24b7024017d268eba697c8436d675a9d23c414bc2 与本地一致，tag 指向源码 3ed7e6e。0.3.1 Release 已补充已知失联问题与替代版本说明。

补充旧 MainActivity 外屏入口：应用强制停止后从该入口启动，实际转入 SystemCaptureActivity、正确实时取景和右侧录像模式。模式初开约 1.8 秒时第一次原生录制点击未开始，待窗口就绪再次点击正常，记录为系统相机初开就绪边界。最终录制/确认/保存完成，独立完整解码 551 帧、720×1280、约 22.96 秒、H.264/AAC，录像原生平均帧率约 19.95；不把前版 29fps 覆盖渲染当成本轮录像帧率。最终版本没有恢复旧入口的黑屏 CameraX 页面。
