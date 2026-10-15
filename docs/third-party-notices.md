# 新增依赖与显示控制适配

- Shizuku API / provider 13.1.5 来自 dev.rikka.shizuku，使用其 Apache-2.0 开源接口，把预览渲染运行在用户授权的真实 Shell 用户服务中。手机需安装、启动 Shizuku 并授权本应用；本工程没有伪造系统签名或相机权限。
- PreviewMirrorWorker.displayToken 的 Android 14 系统服务类加载方式参考并适配 Genymobile scrcpy v4.1 的 DisplayControl.java，适用 Apache-2.0。完整许可见 [scrcpy-APACHE-2.0.txt](licenses/scrcpy-APACHE-2.0.txt)，原实现见 [官方源码](https://github.com/Genymobile/scrcpy/blob/v4.1/server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayControl.java)。本应用没有打包 scrcpy server、vivo 系统 APK 或反编译代码。
- Android 的显示捕获和排除图层是本机系统接口。它们只在真实 UID 2000 / 0 的用户服务内使用；普通应用进程不能借这个类取得系统权限。Lint 对该 Shell 适配方法的 SDK 访问限制采用局部注解，并有实际 UID 检查，其他 Lint 检查保持启用。

Google Camera 专有算法没有包含在上述依赖中，成片翻转是本应用的 JPEG 后处理，原生采集仍由 vivo 系统相机完成。


- OpenCV 4.12.0 来自官方 Maven `org.opencv:opencv`，用于照片 ORB/Hamming/RANSAC/ECC 对齐和双线性重采样。OpenCV 4.5 及以后采用 Apache-2.0，官方说明 https://opencv.org/license/ 。APK 的 assets/licenses 保存 OpenCV 主许可、第三方目录说明、实际 ARM64 AAR 报告的相关组件许可和 LLVM libc++ 许可；来源与版本见该目录 README.txt。ITT 使用其 BSD 替代许可，没有接入非自由模块或 GCam 权重。
- 当前只打包实机确认支持的 arm64-v8a 原生库，标准与兼容 flavor 同样限制；不声称此包支持 x86 模拟器或 32 位手机。核心 JVM 与水印 Robolectric 测试不依赖 Android OpenCV native 库，注册/融合另用真实 ARM64 手机 Instrumentation 验证。
