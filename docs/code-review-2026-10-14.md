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

## 远程结果已核验

- 验证源码：1603d6e48b924862f5e21c2a879497456ced5171。
- [CI 与本轮 APK](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38446311535)：completed / success；13,595 个断言、原始照片全图/哈希、standard / coverCompat 的 assemble 与 Lint 均通过。
- Artifacts 的 xflip-camera-debug 含两个 APK 与两个 Lint HTML；ZIP 11,958,242 bytes，digest sha256:5a026e47b999dd2dc8a0d307eed311cf831d5635e9697a74faf74d261f228cc5，当前保留至 2026-10-20T18:24:21Z。
- app 版本号沿用实机负责人接管的 0.2.2-dev / 10，通过源码 SHA 区分本次修复。没有修改协作中的版本/Manifest/预览链路，也没有重复发布实测 APK；CI 签名与手机上的实测包可能不同。
- 下一步仍按上述 P0/P1 交回唯一有线负责人。编译、模拟提供者测试与证据复核均不代替本轮手机验证。

## 晨间补充：独立照片检查的选择恢复

起始 main 为 1ea6ef629b9ffedc95f49f5fadfac923503f35a8。凌晨源码 1603d6e 的双 flavor CI 已成功，未发现新的失败检查；Issue 1 的实时预览协作交接没有变化。本轮只修改独立照片检查入口及其状态核心，不接管实时预览或系统回传。

静态可达的问题：通过 ACTION_SEND 分享 A，随后在页面的 OpenDocument 选择 B，原实现没有保存当前 URI/报告；系统重建页面时 onCreate 重新执行原始 ACTION_SEND，读取 A。选择器单独启动时，重建则丢失 B。此结论来自已有代码的状态流，没有宣称在手机上复现。

PhotoInspectionActivity 现在把当前选择和小型文字报告写入 saved instance Bundle，优先恢复它们。已完成报告直接恢复，待完成读取只对最新选择重新发起；从分享或选择器换图时立即清空旧报告和复制状态。新增实际接入的 PhotoInspectionState，拒绝旧、重复及已销毁页面的回调。Bitmap 与像素数组不进入 Bundle，不取得新的持久 URI 授权。

验证：bash scripts/check_core.sh 在 JDK 17、--release 17、-Xlint:all、-Werror 下通过 13,630 个断言；新增的 35 个断言验证 A → B → 重建、待完成恢复、重复/旧回调、退出及明确失败报告的归属。可单独运行 java -cp .build/core-checks com.sturgeon.xflipcamera.core.PhotoInspectionStateChecks。java scripts/ParseJava.java 通过 20 个 Java 文件语法解析，git diff --check 通过。此处是 JVM 状态场景验证，未执行 Android Activity 重建或真实权限测试；远程构建结果见下文。

### 待实机验收

1. 从图库分享已知全黑 A，使用选择器改选已知明亮 B，等待报告后重建页面：仍显示 B 的报告，复制内容对应 B；不能回到 A 的全黑结论。
2. 在 B 读取中触发页面重建：只恢复 B 的选择；新实例可重新读取，旧实例不得覆盖新报告。当前 executor 的中断不保证底层提供者立即停止，未承诺跨重建保留同一读取任务。
3. 先改选 B 再取消选择器：保留 B；连续分享/选择 A、B 后，只允许最后一次读取的报告显示和复制。
4. 使用无权限、损坏或已删除 JPEG：明确显示“检查未完成”，不能计为全黑或有效图像。Bundle 中的 URI 字符串不代表权限仍有效；已有报告是此前的检查记录，重新检查需当前有效授权。

状态恢复依据：[Android 官方 Save UI states](https://developer.android.com/topic/libraries/architecture/views/saving-states-views)，仅保存小型选择标识与报告。P0 动态预览及 SystemCaptureActivity 的恢复风险继续沿用上文计划，由有线负责人验收后解除相应协作限制；没有将此次独立入口修复当作实时镜像成功。

### 晨间源码与构建已核验

- 源码提交：[6e811e6ded0b4fd2bc832b0c22c0c2e0774c797f](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/commit/6e811e6ded0b4fd2bc832b0c22c0c2e0774c797f)，7 个变更文件的远程 Git blob 与本地一致，其余远程文件及权限位均保留；9 个预览/系统入口协作文件保持原值。
- [CI 38493995473](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38493995473) completed / success，head 精确对应上述源码；日志确认 13,630 个核心断言、2 张原始 JPEG 共 20,758,528 个完整像素及 SHA-256、双 flavor assemble/Lint 全部通过，构建 BUILD SUCCESSFUL in 44s。原始 JPEG 复核不属于新版本实机测试。
- artifact xflip-camera-debug / 11863057147，11,959,725 bytes，digest sha256:d4795df7b6f0859c3d88d3b44942c59119da6b6283ef3a4eaee4e2160dac256c；保留至 2026-10-21T01:08:44Z。包内为两个 APK 与各自 Lint 报告；版本沿用协作检查点 0.2.2-dev / 10，按源码 SHA 区分，CI 签名未与已安装包核对，也未在手机安装或复测。
