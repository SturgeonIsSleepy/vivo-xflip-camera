# 新增依赖与显示控制适配

- Shizuku API / provider 13.1.5 来自 dev.rikka.shizuku，使用其 Apache-2.0 开源接口，把预览渲染运行在用户授权的真实 Shell 用户服务中。手机需安装、启动 Shizuku 并授权本应用；本工程没有伪造系统签名或相机权限。
- PreviewMirrorWorker.displayToken 的 Android 14 系统服务类加载方式参考并适配 Genymobile scrcpy v4.1 的 DisplayControl.java，适用 Apache-2.0。完整许可见 [scrcpy-APACHE-2.0.txt](licenses/scrcpy-APACHE-2.0.txt)，原实现见 [官方源码](https://github.com/Genymobile/scrcpy/blob/v4.1/server/src/main/java/com/genymobile/scrcpy/wrappers/DisplayControl.java)。本应用没有打包 scrcpy server、vivo 系统 APK 或反编译代码。
- Android 的显示捕获和排除图层是本机系统接口。它们只在真实 UID 2000 / 0 的用户服务内使用；普通应用进程不能借这个类取得系统权限。Lint 对该 Shell 适配方法的 SDK 访问限制采用局部注解，并有实际 UID 检查，其他 Lint 检查保持启用。

Google Camera 专有算法没有包含在上述依赖中，成片翻转是本应用的 JPEG 后处理，原生采集仍由 vivo 系统相机完成。
