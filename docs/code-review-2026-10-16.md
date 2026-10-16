# 共享照片保存事务与故障回归

起始 `d5be7e5e14a328beca1e5e40eda8f13880cbb610`。复用 0.4.1 成像及已完成的逐行性能核心，不重新实现算法。最新五轮 CI 均成功，没有将未知真机现象当作已确认错误。

## 已复现并修复

`PendingMediaWrite` 原先只捕获 IOException 和 RuntimeException。写入/发布抛出 Error 时不会回滚已知身份的新 pending 项；清理抛出 Error 时会覆盖原始失败。新增故障测试在旧生产核心失败：

```text
bash scripts/check_core.sh
AssertionError: New partial output is removed once

java -cp .build/core-checks com.sturgeon.xflipcamera.core.PendingMediaWriteChecks cleanup-errors
AssertionError: Cleanup Error cannot mask the primary failure
```

修复扩大了异常清理范围，原 Error 实例继续抛出，不返回成功、不转换为普通 IOException；清理错误在可用时附加为 suppressed，防止同实例自我抑制。诊断分配失败不覆盖原错误。只清理本次新输出，成功项、源图和旧输出保持；插入抛错且没有输出身份时不清理未知数据。

测试使用预先构造的 OutOfMemoryError/LinkageError/AssertionError，**没有真的耗尽堆，也不保证真实 OOM 时提供者还能删除或进程还能继续工作**。删除仍为尽力操作，失败需保留真实诊断。没有错误清理能力的断电/进程被杀不在此测试范围。

```sh
bash scripts/check_core.sh
java -cp .build/core-checks com.sturgeon.xflipcamera.core.PendingMediaWriteChecks write-errors
java -cp .build/core-checks com.sturgeon.xflipcamera.core.PendingMediaWriteChecks cleanup-errors
```

实际通过 942,702 项核心断言，新增 76 项；两个独立选择分别通过 54/22 项。保留此前字节/元数据关闭后恰好发布一行和已有故障测试。

## 有界后续验证

通用核心已经被现有 JPEG 保存路径调用，本修复无需等待性能核心接入。[修复 02f1567](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/commit/02f1567ae5bc644fc2e46ba145faf9959a0713b1) 的 [CI 38833455721](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38833455721) 已成功，日志确认 942,702 个核心断言、历史 JPEG 完整解码、双 flavor 各 8 项既有水印回归、双 APK/Lint，BUILD SUCCESSFUL in 1m 6s。

额外新增 `JpegPhotoWriterTest` 的 9 个测试方法，使用真实 Bitmap/ImageDecoder/JPEG/EXIF 与故障可控的提供者：

- 已归一的工作图从 EXIF 1..8 源生成，保存后方向1、非对称角点/宽高正确，所选拍摄字段/合成GPS/处理描述保留；原 Bitmap 不变。归一发生在 ImageDecoder，不能据此声称 JpegPhotoWriter 会修复任意未归一 Bitmap。
- 写入前取消、编码/EXIF后发布前取消均回滚新项；发布之后的取消保留已完成结果。
- 发布0/2行、元数据读取失权、输出写入失权均不能报告成功；原图和既存输出逐字节保持。
- 发布阶段注入预构造 OutOfMemoryError，检查同一实例传播与新项清理；清理注入 AssertionError，检查原发布 IOException 与 suppressed，以及未清理项仍存在的真实状态。
- 提供者模型检查输出描述符在发布前已关闭，实际 FD 的 dup/lseek/内核所有权仍不在主机覆盖范围。

共享既有 EXIF FD 测试适配的登记表，未改生产输出/成像/拍摄集成或依赖。本地语法检查42文件与差异空白检查通过；新增测试已由下述真实 CI 执行核验。

## 完整验证与后续

[回归源码 cc97e21](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/commit/cc97e21986c456d83f21344e8532c96a28967414) 的 [CI 38833842432](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38833842432) 已 completed/success，日志为 `BUILD SUCCESSFUL in 44s`。实际执行942,702核心断言、两张历史黑JPEG完整解码/哈希、双APK/Lint，standard和coverCompat各17项图形回归，即水印8项加共享保存9项。

已下载 `watermark-test-results` / `12018303388`，3,316 bytes，ZIP SHA256 `5e5567b93658dfddaf75a85c0202ae9a224b80fea7396e7a409817711b6d500d` 匹配。实际4份JUnit XML，各flavor对应两个测试类，分别9/8项，失败/错误/跳过全为0。Artifact名和日志标签沿用历史水印名称，但已包含JPEG保存，不误记为水印新增17项。

APK/Lint artifact `12018413140`，36,710,133 bytes，digest `cb1b12ff582b495f4094f9cd3bd50de082885a7d975321a5d6261ed2d3d78a99`，到期2026-10-23T01:13:33Z；测试ZIP到期01:13:32Z。未安装CI包或制作新实测Release，签名不与此前手机实测包混同。两次代码提交已使用预期main SHA且非强制推送，读回6个变更blob并保留其余156个原文件/权限位。

下一项由负责人协调逐行核心接入，验收真实OpenCV路径像素、单张/三帧、取消与13MP端到端耗时/峰值。共享输出还需在真实MediaStore上做正常保存及权限/生命周期检查；不在无人值守运行中制造真实堆耗尽。其余直方图/噪声/光晕和多曝光/视频防抖沿用现有待办。

真实 MediaStore、Android 文件描述符系统调用、低内存与开合屏生命周期仍待有线负责人。沿用现有 Robolectric FD 到同一临时文件的测试适配，不把 EXIF 字节检查当 dup/lseek 或真机验证。
