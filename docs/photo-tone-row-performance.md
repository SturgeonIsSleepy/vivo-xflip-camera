# 逐行色调核心与主机对照

基线 `44c200644cb6ab1fc2abecb92be3fc4d8d34cde2`，沿用 0.4.1 的色阶、细节与 JPEG HDR 色调，不增加成像功能。

## 范围与接入状态

新增 `camera-core` 的 `PhotoToneRowProcessor`。每图缓存 HDR 的逐亮度对数、羽化强度及细节门限，不近似指数、不改灰度系数、不改 RGB 增益/限幅/舍入顺序。行循环只写调用者的 `int[] colors`，其余行只读；构造后没有逐行新建数组。

按 [Issue 1 的集成文件协调](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/issues/1#issuecomment-6198997547)，本轮不改 `PhotoEnhancer`、`PhotoToneCurve`、拍摄流程、共享 JPEG 保存或版本号。**核心尚未接入应用实际处理路径，手机端尚未获得本轮提速。** Bitmap、RGBA/灰度、CLAHE 和全尺寸 HDR 基础层的现有 OpenCV 分配没有减少。

当前新表仅含 256 项 double：两张共享表共 4,096 bytes 数据，启用 HDR 时每图强度表再增加 2,048 bytes 数据，不含 Java 对象开销。关闭 HDR 不创建每图强度表。这是有限小表缓存，不能把它说成整条照片流水线峰值内存优化。

## 已执行验证

```sh
bash scripts/check_core.sh
java scripts/ParseJava.java
bash scripts/benchmark_photo_tone.sh 5
git diff --check
```

JDK 17 / `--release 17 -Xlint:all -Werror`：942,626 项核心断言通过，较基线新增 912,189 项。测试里的 `LegacyPhotoToneRow` 固定保留基线旧循环及三个旧公式，不调用新 HDR 缓存作为预期值。涵盖亮度 0..255、HDR 0.999 边界、六种噪声、饱和 RGB/中性灰/alpha、随机非对称邻域、1/2 像素窄行、顶部/底部邻行别名、缺失及短缓冲失败前不写入。语法解析通过 41 个文件，不代表独立 Android 类型验证。

[源码 fee44f8](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/commit/fee44f8b02ac7581aae9a9e660ca0d0eadb94c7f)的 [CI 38784336760](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38784336760) 已成功，实际日志确认上述核心断言、两张既有 JPEG 的完整像素/哈希、双 flavor APK/Lint、两套各 8 项水印图形回归，`BUILD SUCCESSFUL in 1m 3s`。测试 ZIP artifact `11997850600` 已下载并核对 SHA256 `2394e748fd956880aaabf70214c629366e577acfa4a7211f60248189f13523b6`，两份 JUnit XML 各为 tests=8、failures/errors/skipped=0。APK/Lint artifact `11997715799`，36,710,191 bytes，digest `eb7dfe3a415763a86961eb3fa892982069258f04a462308dc6eda006df6bb2d2`，到期 2026-10-22T18:18:39Z；测试 ZIP 到期 18:18:37Z。未安装这些 CI 包，不与此前本地签名实测文件混同，也不把水印回归称为新核心的 Android 实测。

另在计时外分别对两种模式各执行 12,582,912 像素的逐行 ARGB 对照，差异均为 0。计时为 amd64、Java 17.0.20、128 MiB JVM 堆，16 条固定合成行循环成 3072×4096 的流；四轮短预热、五组交替先后顺序，统计中位数：

| 主机像素循环 | 基线 | 新核心 | 结论 |
| --- | --- | --- | --- |
| 细节开，HDR 关 | 491.496 ms | 483.627 ms | 1.0163 倍，落在波动范围，不能声称明确提速 |
| 细节开，HDR 开，compression=0.65/noise=3.5 | 867.605 ms | 604.612 ms | 1.4350 倍，仅该主机合成像素循环 |

时间包含行输入复制、色调/细节/RGB 运算及输出校验和，**不含 OpenCV 分析、图像解码、JPEG 编码、MediaStore 和水印**。线程分配计数器在预热后的新行循环测得 0 bytes，不含初始化/夹具/行缓冲，不代表手机总体内存。该脚本不进常规 CI 计时，也没有速度通过阈值。[原始五组结果与边界](evidence/tone-row-host-2026-10-16.json)。

## 后续最小接入

负责人确认文件协调后，在 `PhotoEnhancer.apply` 的 compression/noise 确定后构造一次：

```java
PhotoToneRowProcessor rows = new PhotoToneRowProcessor(options.detail(), compression, noise);
// 仍在现有逐行循环中执行 check(cancelled)、Bitmap.getPixels 和 Mat.get。
rows.process(colors, before, center, after, local, smooth, baseRow);
// 保留原有 Bitmap.setPixels；不改 ImageDecoder、EXIF 或 PendingMediaWrite。
```

由宿主继续处理取消、进度、图像首尾行夹紧、方向、颜色空间、Bitmap 与 Mat 释放。disabled detail/HDR 对应缓冲可为 null；center 必需，colors.length 决定有效行宽。不能对同一行重复应用，也不能给它原始未做色阶映射的 gray 而期望等效。

下一步先按此最小替换验证真实 OpenCV 生产路径的处理前像素/编码后照片、单张与三帧、EXIF/取消回滚，再在同一 X Flip 上记录 13MP 端到端耗时与 Java/native 峰值。共享 JPEG 输出、直方图噪声/尾部与光晕仍按原审查待办，不借本轮缓存改变其成像语义；真正包围曝光及成熟视频防抖继续原计划。没有设备时不把主机结果外推到手机。
