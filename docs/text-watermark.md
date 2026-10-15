# 自制文字水印处理器

WatermarkProcessor.mark(ContentResolver, Uri original, String caption) 按 Issue 1 的检查任务交接实现，返回新发布的 MediaStore JPEG URI。原图以只读方式打开，失败只尝试删除本次新输出。组件尚未接入拍摄页面或设置，不代表当前界面已有水印开关；没有原生蔡司水印、厂商标志或 Google Camera 专有算法。

## 输入与图像代价

- 只接受授权可读的 content URI、JPEG、双边至少 128 像素且总像素不超过 20MP。软件可变 Bitmap 经 ImageDecoder 处理 EXIF，输出 Orientation=1；EXIF 5..8 的显示宽高交换。图像不缩放、不裁切、不拓边。
- 输出为 sRGB / JPEG 质量 95 的有损重编码。检测到 Bitmap gain map 时拒绝处理；不保证保留 HDR、广色域、XMP、MakerNote 或所有厂商元数据。
- 文字去首尾空白后为 1～80 个单行字符，拒绝控制、不可见格式和无效代理字符；按图宽调整字号，低于 12 像素或覆盖区超过图高 1/4 则拒绝，不静默截断。普通系统字体、黑底白字覆盖另存图底部部分像素，原图不变。
- 拍摄 EXIF 按 CAPTURE_TAGS 白名单复制：Make/Model、原拍摄日期及细分/时区、曝光、光圈、ISO、焦距、闪光灯、原有 GPS。原宽高/缩略图/Orientation 不复制，避免残留旧方向；Software 标记本应用文字编辑副本。
- 原有 GPS 会保留在本机副本，当前接口不提供去 GPS 选项，分享前需注意元数据隐私。仓库不上传真实照片/坐标/场景，回归只在临时目录生成色块 JPEG，GPS 为合成零值。

## 保存与接入

调用放在后台执行器；宿主负责 UI、任务归属、退出/取消及重复点击。本方法不打开相机、请求权限或改变 Shell。新图保存到 Pictures/XFlipCamera、image/jpeg、IS_PENDING=1，压缩资源关闭和 EXIF 完成后，复用 PendingMediaWrite 要求恰好发布一行。发布 0 行不返回成功；清理拒绝保留原错误及 suppressed，原图和旧副本不是回滚目标。

不新增 Manifest 权限，不改变既有拍摄链路。JUnit 4.13.2 / Robolectric 4.17 仅为 testImplementation，不进入运行时依赖；按官方 Java 17 配置开放测试 JVM 所需包。CI 增加两个 flavor 的 unit test 和 JUnit XML artifact，保留 APK、Lint、核心及原始 JPEG 检查。

## 合成图验证与边界

WatermarkProcessorTest 使用 Robolectric API 34 / NATIVE graphics，实际执行 Bitmap/Canvas/ImageDecoder/JPEG/ExifInterface；MediaStore 是文件支持的假提供者，用于控制发布和故障，不是 vivo/真实 MediaStore 验收。8 个方法覆盖：

1. EXIF 1..8 不对称色块、独立角点表、归一宽高、真实白字/黑底像素、拍摄 EXIF、合成 GPS、pending 及原图逐字节保留。
2. 两种 caption 的底部像素不同，原图和旧副本保留。
3. 空/超长/控制字符及 file URI 不创建输出。
4. 发布 0 行回滚、不报成功。
5. 清理拒绝保留发布错误及 suppressed。
6. 无权读取不插入输出。
7. 损坏 JPEG 不计有效，原字节不变。
8. 写入失败只清理新 item。

```sh
bash scripts/check_core.sh
java scripts/ParseJava.java
gradle --no-daemon :camera-core:check :app:testStandardDebugUnitTest :app:testCoverCompatDebugUnitTest :app:assembleStandardDebug :app:assembleCoverCompatDebug :app:lintStandardDebug :app:lintCoverCompatDebug
```

本地已执行核心 15,375 个断言、语法和差异空白检查；本地没有 Gradle/ADB，native graphics、双 APK/Lint 须读回实际远程结果。此轮未连接 X Flip，字体/中文、HDR 拒绝、真实权限变化和完整流程仍待手机复验。测试定义本身不构成通过证明。

首轮 CI 38664028710 的 8 项测试有 4 项失败，JUnit XML 显示 ExifInterface(FileDescriptor) 在 Os.dup 返回 null 后抛 NPE，不是角点或文字像素断言失败。对照 AndroidX 与 Robolectric 源码后，仅在测试中把已登记的临时 FileDescriptor 映射到相同 File，调用真实 ExifInterface(File) 解析/写入字节；未知描述符直接使测试失败。图形实现和元数据解析没有改为空操作，生产 FD 路径没有修改。测试仍不覆盖真实 Android dup/lseek/描述符所有权，须由设备验收补齐。修正适配后的结果需重新读回 CI。

## 下一步

先由有线负责人复验完整 GPU 候选，再把 CoverControls 和本方法接入设置/照片另存流程，验收快门、原图及退出/错误归属。成熟防抖与合法成像复用已有路线，不因文字副本扩展算法声明。

依据：[ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder)、[Robolectric 设置](https://robolectric.org/getting-started/)、[原生图形模式](https://robolectric.org/javadoc/4.17/org/robolectric/annotation/GraphicsMode.html)。Robolectric 4.17 于 2026-09-10 正式发布，测试环境不代替设备。
