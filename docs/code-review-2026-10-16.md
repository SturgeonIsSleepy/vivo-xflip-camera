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

通用核心已经被现有 JPEG 保存路径调用，本修复无需等待性能核心接入。接下来补实际 JpegPhotoWriter 的编码/EXIF、取消时机、单行发布与清理故障回归，使用合成像素和可控提供者；不并发修改负责人保留的拍摄/成像集成文件。

真实 MediaStore、Android 文件描述符系统调用、低内存与开合屏生命周期仍待有线负责人。沿用现有 Robolectric FD 到同一临时文件的测试适配，不把 EXIF 字节检查当 dup/lseek 或真机验证。
