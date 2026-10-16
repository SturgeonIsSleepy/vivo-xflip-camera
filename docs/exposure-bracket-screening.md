# 只读多曝光元数据初筛

基线 `0a9e62f0dd92c30449bac794cf96d025ab8ced3c`。沿用已有 JPEG 色调与三张同曝光降噪，不重新实现它们，也不将它们改称多曝光 HDR。

## 可用接口与边界

`JpegExposureInspector.inspect(ContentResolver, List<Uri>, BooleanSupplier)` 在工作线程读取三个用户已授权的原始 JPEG content URI，返回 `ExposureBracketAssessment.Result`。不申请额外权限、不操纵相机/外屏、不解码整图 RGB、不写媒体、原图或日志，不自动上传。它是独立可调用组件，**尚未接入界面/拍摄流水线，不会捕获包围曝光或生成 HDR 成片**。

只有三份元数据齐全、文件摘要不同、归一后尺寸相同且13MP内、Make/Model一致、光圈/焦距逐对差异不超过1%、闪光标记已知且未触发，才继续检查曝光跨度。缺字段/无效数据失败关闭。没有URI、文件名、摘要、相机标识或GPS返回到结果；结果只含状态、输入索引与相对档位。不要公开真实照片的元数据或派生指纹，示例和回归均为合成。

设 `t` 为秒、`N` 为光圈值，使用 `C = log2(t) - 2*log2(N)` 作曝光量代理，`G = C + log2(ISO)` 作含增益代理。按 C 从短到长排序，中间一张仅作为未来参考索引；C和G的两侧相邻差都至少1档，返回 `METADATA_CANDIDATE_ONLY`。这里的正档位表示曝光量增加，**不是绝对 EV100 测光值**。1档门限、同镜头/光圈约束为本项目保守首阶段策略，不是HDR通用标准，也不覆盖双原生ISO/多增益HDR路线。

| 合成输入t（毫秒） | ISO | 初筛 |
| --- | --- | --- |
| 1 / 4 / 16 | 100 / 100 / 100 | C、G各侧2档，仅元数据候选 |
| 4 / 4 / 4 | 100 / 400 / 1600 | C无跨度，拒绝ISO-only |
| 1 / 4 / 16 | 1600 / 400 / 100 | G抵消为同级，拒绝 |
| 任一关键字段缺失 | 不猜测 | 拒绝，不能当候选 |

候选不证明同场景、独立传感器测量、实际曝光控制、镜头物理ID或可用像素。EXIF可改写，整个文件的不同哈希也不证明像素不同。专门的回归把相同像素复制三份后改写EXIF，仍只返回“元数据候选”，用于明确这个限制。后续必须再核验授权采集路径、实际帧结果、内容/方向/对齐、运动与剪切，不能直接据此启用HDR按钮。

## 读取与内存预算

`BoundedDigestInputStream` 单流32MiB预算，边界最多读1个额外字节判断是否超额；串行处理三个输入。摘要覆盖解析器没读的JPEG尾部，skip也计入，mark/reset只发生在上层缓冲，不重复哈希。读/available异常、取消或超预算会保持失败状态，即使元数据解析器内部捕获过异常，完成摘要仍抛出。输入/权限/关闭异常不返回部分肯定结果。

自有初始流缓冲为16KiB摘要/skip缓冲加16KiB解析器前缓冲，不保留三张全分辨率RGB。Exif库可能另分配或扩展元数据缓冲，未测手机PSS/峰值；不把上述数组尺寸当总内存。取消检查在读边界执行，不能承诺强制中断阻塞提供者。只检查JPEG头及元数据，不证明压缩像素流完整可解码，不读日期/定位用于真实性判断。

摘要只描述本次观察到的字节流，不证明提供者读取期间保持不变。宿主负责保留调用时相同的输入快照、URI授权、请求归属及取消；不能把旧结果应用于后来替换的新照片。

## 验证

```sh
bash scripts/check_core.sh
java scripts/ParseJava.java
gradle --no-daemon :camera-core:check :app:testStandardDebugUnitTest :app:testCoverCompatDebugUnitTest :app:assembleStandardDebug :app:assembleCoverCompatDebug :app:lintStandardDebug :app:lintCoverCompatDebug
```

本地主机JDK17核心942,894项通过，比基线增加192项；48文件语法及差异空白检查通过，不等同Android类型或设备验收。新增10项Robolectric API34/NATIVE graphics JPEG回归已实际执行并通过；没有手机操作或本地Android构建。

[源码dba482b](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/commit/dba482baeb05a9f57601d916e2f871060d3e8887)的[CI 38946658536](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38946658536)已completed/success，实际日志确认942,894核心断言、两张历史JPEG完整解码/哈希、双APK/Lint、两flavor各27项回归（旧水印8、共享JPEG9、新曝光10），BUILD SUCCESSFUL in 57s。

测试artifact `12066362235`，5,160 bytes，已下载核对ZIP SHA256 `a3394f707a6dbf97d88dc7fb16db54a5f5104e03decc642d854e67b3d84c0782`；实际六份JUnit XML分别10/9/8项各两份，失败/错误/跳过全0。artifact沿用历史名字watermark-test-results，不误记为新增27项水印。APK/Lint artifact `12066177289`，36,725,085 bytes，digest `68efb0dc645dded40b0d62715f13c16254cda98ff50d20f969153bd4c0c54d08`，到期2026-10-23T18:22:27Z，测试ZIP到期18:22:26Z。CI包未安装，签名不与既有手机实测包混同；不发布新的实测Release。

## 技术依据与后续

Android官方[CaptureResult](https://developer.android.com/reference/android/hardware/camera2/CaptureResult)区分曝光时长、ISO增益和光圈；[CaptureRequest](https://developer.android.com/reference/android/hardware/camera2/CaptureRequest)说明AE可覆盖手动参数，最终值需看结果。外部系统相机回传的EXIF只能作初筛，不能把请求值或补偿标记视为已验证实际曝光。上述C/G为项目代理，不计透射率、传感器校准、其他增益、JPEG响应或局部色调映射，不能解释为恢复被剪白信息。

本轮核对[AndroidX官方ExifInterface输入流源码](https://github.com/androidx/androidx/blob/androidx-main/exifinterface/exifinterface/src/main/java/androidx/exifinterface/media/ExifInterface.java)的只读、调用方关闭及兼容性I/O处理；项目依赖仍为既有1.4.2，没有升级或复制其实现。保留 Google Camera 合法边界，没有封装或分发其私有算法/二进制。

下一项由负责人先核验原系统路径是否能获取真实不同曝光和实际帧证据，再接只读检查并设计融合。逐行性能核心仍按原文件协调等待接入；不以缺设备重复调查黑屏或创建空提交。
