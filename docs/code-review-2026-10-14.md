# 媒体发布失败处理与预览生命周期边界

起始源码 cbe80af67337d6bc553a8775f6315fe2a484368f。复用已实测的系统相机 JPEG/MP4 回传和既有证据；本轮没有连接或操作手机。

## 实际修复

PhotoPostProcessor 先写入独立 JPEG，再清除 MediaStore IS_PENDING。原实现没有检查 update 的返回行数，即使发布 0 行也返回 URI，让上层误报成功；清理时 delete 抛出的异常还可能掩盖原始保存失败。

新增纯 Java PendingMediaWrite，并实际接入该另存路径：

1. 只持有本次 insert 返回的新 pending URI；插入失败/空 URI 不进入写入或清理。
2. JPEG 与 EXIF 写入及资源关闭成功后才尝试发布，要求 update 恰好影响 1 行。
3. 写入或发布失败时只尝试删除本次新输出，源图及以前的照片不传给删除操作。
4. 清理失败保留原始异常，并记录为 suppressed 异常；同一个异常对象重复抛出也不产生自抑制错误。

此核心不是跨进程事务日志，不能保证进程被杀、Provider 失联或清理被拒绝时已经删除文件。失败不会被计为保存成功；Android Provider 故障、缓存 URI 授权和恢复场景仍须设备验证。当前没有修改系统相机回传流程，也没有宣称它的同类发布问题已修复。

本轮保持像素翻转与 JPEG/EXIF 策略，未增加成片算法。用户当前首要目标仍为实时取景纠正。

## 独立故障验证

```sh
bash scripts/check_core.sh
java -cp .build/core-checks com.sturgeon.xflipcamera.core.PendingMediaWriteChecks
java -Djava.awt.headless=true -cp .build/core-checks \
  com.sturgeon.xflipcamera.core.PhotoEvidenceChecks
java scripts/ParseJava.java
```

媒体模拟提供者包含源图、旧图、pending 集合、发布集合和部分写入；注入插入、写入、发布与删除阶段的 IO/运行时失败，以及异常更新行数。84 个新增断言全部通过，纳入现有 Gradle coreChecks，总计 13,595 个断言。原始黑 JPEG 的独立完整解码与哈希也已本地复核。

这些检查覆盖同一生产核心的失败语义，不模拟 Android Binder、Surface、实际文件授权或相机画面。完整双 flavor 构建与 Lint 使用既有工作流，实际结果记录在根目录 DEVELOPMENT_LOG.md。

## 交回实机负责人的静态发现

按照 [Issue 1 最新检查点](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/issues/1#issuecomment-6147112370) 暂不并发修改实时链路及系统入口，以下不是已执行的设备复现：

- 发送端已经 setShareIdentityEnabled(true)，接收端有 API 34 判断、DUMP 及实际 UID 0/2000 检查和 Binder 描述符校验。不能重复把“未共享发送身份”认定为现有超时根因，也不能以静态检查证明回传成功。
- PreviewMirrorWorker.stop 的 join 最多等待 1500ms，START 随后复用同一个 running 字段。如果旧渲染仍阻塞，重启可能与旧线程重叠，旧 finally 还可能清除新会话的 running。建议每次渲染拥有独立代号和结束信号，在实机故意延迟捕获/重复 START 下验收。
- PreviewMirrorService.surfaceDestroyed 当前没有显式发送 STOP；surfaceChanged 也未重新配置。需验收 Surface 销毁、后台/确认页面切换、Binder 死亡与快速重新开始时，旧 Surface/SurfaceControl 是否释放且不会停止新会话。
- SystemCaptureActivity 在 reading=true 的状态恢复时会再次 showPhoto，旧实例的 executor shutdown 并不取消已开始读取；同一进程的配置重建可能重复写同一目标。退出过程中 pending 的缓存与目标归属也需独立追踪。建议单一读取执行者与持久化阶段，先区分配置重建和进程重建。
- SystemCaptureActivity 的 IS_PENDING update 仍未检查返回行数；解除独占后可复用 PendingMediaWrite，但需先保留已验证的系统输出 URI、图片/视频 MIME、取消与原图归属。

## 优先级与验收

- P0：直接 Binder 通道与真实动态预览由有线负责人继续验证，使用非对称文字确认左右；要求快门/确认可用、退出无覆盖残留，并对 STOP/旧线程及 Surface 生命周期回归。
- P1：系统输出发布与读取恢复接续修复，验收正常拍照/录像、取消、写入/权限失败、后台退出及重建，不删除已发布照片。
- P2：合法成像与厂商扩展待办继续复用。没有接入 Google Camera 专有算法。

官方 API 依据：[ContentResolver.update](https://developer.android.com/reference/android/content/ContentResolver) 返回影响行数；[MediaStore.IS_PENDING](https://developer.android.com/reference/android/provider/MediaStore.MediaColumns) 的发布状态；[BroadcastOptions](https://developer.android.com/reference/android/app/BroadcastOptions) 的身份共享设置。此轮模型/推理强度与剩余额度接口均未提供可读取值。
