# 基础拍照版实测

从 `d15fbb188f42e1e0d2d938c3fef00755f57b5d0f` 续接前两天的逐行核心、保存回滚修复与曝光初筛，未覆盖这些成果。用户要求先解决按键重叠并交付基础版，再做直方图和算法。

`coverCompatBasic` 为同一源码的独立构建，版本 `0.4.2-basic / 28`，包名仍为 `org.codeaurora.snapcam`，可直接更新此前手机安装。只保留系统照片采集、实时左右纠正、原始JPEG保存与查看。拍摄时不创建任何自有设置/拍照/录像悬浮按钮，系统快门、确认与重拍保持可用；返回页仅拍照和返回。基础版在编译配置中禁用增强、水印、三张合成和录像，不受旧偏好影响；增强版源码与依赖仍保留，未谎称已从APK裁除所有算法代码。

已在真实合盖外屏完成两次照片保存、重拍回到取景、三次强制停止后正常入口重开及取消退出。三次均恢复phase=1，附加控件窗口0个；包括ADB查询的诊断耗时1.959 / 2.267 / 2.284秒，不是精确用户启动耗时。最终包安装后SHA256与本地一致。可读文字和数字的预览与成片方向一致；系统回传JPEG和图库保存JPEG逐字节相等，独立完整解码成功，未发生二次增强或水印编码。实拍与截图只留本地，不公开其哈希、文件名或EXIF。

本地构建、基础版Lint、942,894核心断言与coverCompat的27项图形/保存/EXIF回归通过，BUILD SUCCESSFUL in 49s。APK为45,261,516 bytes，SHA256 `e7b226989a6948f4675eff65b7b5c090f73b2f0dedfc9515f3102c9cdb343332`。

复现：

```powershell
gradle :camera-core:check :app:assembleCoverCompatBasic :app:lintCoverCompatBasic :app:testCoverCompatDebugUnitTest
.\scripts\start_preview_bridge.ps1 -Serial <serial> -Adb <adb>
.\scripts\test_preview_reconnect.ps1 -Serial <serial> -Adb <adb> -EvidenceDirectory <local-private-directory> -BasicCapture
```

基础版仍需要外屏助手及每次手机重启后的桥接初始化，系统每张需确认。未做整机重启自动授权、内屏/展开/前摄和全旋转矩阵或长时间耐久验收，不能把短时回归说成永久稳定。未改变设备全局DPI或偏好；本次服务读取外屏运行时density=200，与早期160不同。

后续增强版需求为可选显示直方图和PS式黑白场滑块。先与稳定APK隔离，接入前两天已验证的逐行核心，用成熟开源算法与独立对照衡量收益，不单纯调大锐化；真正多曝光输入仍未取得。
