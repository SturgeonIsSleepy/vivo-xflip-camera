# 10月24日实机续接与CPU阶段验收

从main612fb64续接两位agent这几天的编辑器、Bitmap所有权、output-only恢复、媒体清理及视频交付工作。新版本0.4.5-lab/31同签名覆盖唯一org.codeaurora.snapcam.lab，不恢复基础版或并装诊断入口。相机权限保持；实测vivo在更新后将悬浮窗置为ignore，依用户此前明确授权恢复allow。未改全机DPI、性能或旋转锁设置。

## 外屏交互

合盖Android14/ARM64、display1。新增debug-only仪器使用3072×4096合成文件，通过反射打开真实生产编辑器及执行Activity.recreate，不替换处理器、控件或执行器，也不开启OEM相机。成功验证：

- output-only、photo为空且reading=true的真实重建恢复，读取完成后快门控件解锁
- 连续40次黑场变更及原图对照不能以旧预览开启另存
- 编辑中真实重建后黑白场参数恢复，新预览匹配后才能另存
- 开启NLM并完整另存，确认busy后通过Back关闭，等待实际worker结束，无新增媒体行，合成原图字节不变
- 取消后编辑器可再开，所有操作仍在display1

合成测试临时快照并恢复system_capture偏好，主机另行逐键核对未改用户偏好，不导出偏好内容。首次单独运行仪器等待外屏启动未返回；沿用原有设备脚本的后台instrumentation加延时shell启动方式后通过，最终包装器及最终APK又通过一次。复现：`scripts/test_editor_lifecycle_device.py --adb ADB --serial SERIAL nlm-full.jpg OUTPUT`。

另用最终包及重新初始化的同包桥接，合盖GPU预览phase1、47帧、capture_misses0。真实拍摄、原生确认、回传JPEG独立解码和旧照片字节保留通过。第一次自动确认点在页面动画期间未生效，查看实际确认页后再点才成功，不把漏点当代码修复。镜头所见很暗，本次不以实拍评价降噪质量或重新验收镜像方向矩阵。私人照片、文件名、摘要、EXIF和截图不上传。

## 实测发现的预览采样错误

权限页使外屏切为422×682后，重新启动取景时控件采样出现 `IllegalArgumentException: x must be < bitmap.width()`，worker报告mirror_error并停止翻转。源代码reviewControls按高度取半径，却未约束到窄采样图的左右边界；本轮仅把循环限制在实际Bitmap范围并要求非空计数。

新增native Bitmap回归覆盖1×1、1×170、7×170、26×170及横向样本的黑白判断，两种debug均通过。返回横向重开可恢复预览。**这处越界修复不等于完整竖屏预览翻转已支持**；旋转变化时的采集几何重建、portrait控件布局和自动恢复仍交给下一轮专项验证。未增加全局旋转设置、强制解锁或权限绕过。

## 最终同包CPU与像素验收

沿用同一生产save和固定合成图，先小图预热，再按诊断/普通/普通/诊断顺序保存，最后取消检查。每次instrumentation首尾校验独立base.apk身份，四次保存和取消均为最终APK。四份JPEG独立解码最大通道差均0，原图保留；取消诊断outcome为cancelled。普通入口不开启CPU时钟。本轮不是新旧算法提速基准，不把诊断样本并入普通速度中位。

| 顺序 | 类型 | 完整保存ms |
| --- | --- | ---: |
| 1 | CPU诊断 | 20635 |
| 2 | 普通 | 27419 |
| 3 | 普通 | 27239 |
| 4 | CPU诊断 | 27242 |

两次诊断总墙钟约20.635/27.242秒，NLM_FILTER分别占93.32%/94.51%。进程CPU分别56238/80113ms，覆盖整个应用进程及原生并行线程，可大于墙钟；不是OpenCV专属采样。调用线程CPU不能代表全部并行线程。阶段互斥，原始完整计数见JSON。

| 阶段 | 诊断1墙钟ms | 诊断2墙钟ms | 进程CPU ms：1 / 2 |
| --- | ---: | ---: | ---: |
| DECODE | 335.23 | 378.94 | 670 / 513 |
| NLM_SETUP | 12.46 | 13.41 | 25 / 14 |
| NLM_ROW_WINDOW | 15.99 | 17.36 | 24 / 18 |
| NLM_TILE_INPUT | 128.59 | 166.87 | 182 / 229 |
| NLM_FILTER | 19255.62 | 25746.47 | 54407 / 78378 |
| NLM_TILE_OUTPUT | 192.48 | 225.53 | 305 / 337 |
| NLM_RELEASE | 0.16 | 0.18 | 0 / 0 |
| INPUT_LEVELS | 0.58 | 0.64 | 1 / 1 |
| TONE_DETAIL | 1.99 | 2.01 | 2 / 2 |
| JPEG_TRANSACTION | 690.32 | 689.60 | 621 / 620 |
| BITMAP_RELEASE | 1.07 | 1.10 | 1 / 1 |

实际主要耗时在fastNlMeansDenoising本体，JPEG事务约0.69秒，行窗口约16–17ms。外围复制/解码不是这一输入的首要热点；先在固定质量约束下比较成熟滤波器或搜索参数取舍，不继续盲目扩大块/线程。尚未更换算法，没有本轮新增画质或提速承诺。

没有固定频率、温度、亮度、其他负载或做冷却控制；有线供电，所有thermal状态读数0不证明无降频。前一个未含采样边界修复的0.4.5候选普通保存曾为11129/12659ms，后续最终包27秒，说明连续运行漂移，不能选择性引用较快样本或与10月18日跨会话比速。该先导APK038440f2…的结果只留本地，公开CPU主表仅最终a0d0d1f8…，不混包。

复现使用scripts/verify_photo_cpu_device.py，指定--adb、--serial、既有合成fixture目录、独立输出目录。沿用生产768块/4线程、h5/7/21、halo16、滚动缓存、JPEG97，NLM仍拍后可选且默认关闭。Google Camera内部算法、真正包围曝光HDR、成熟视频防抖效果及视频完成临界点退出竞态没有在本轮新增实机验收。

## 构建与交付

本地核心66775111项断言、两个debug各65项图形回归、21项主机契约、lab构建/Lint通过。初次全量1分31秒；加入采样边界修复后相关测试/构建/Lint1分22秒通过。最终APK45617648bytes，SHA256 `a0d0d1f8b9a1b0100d5e2ef40a0e7f3de9bd582e0812b541c11a765228f7a9bc`，与手机及逐次回执一致。

[完整公开软件/合成证据](evidence/device-acceptance-2026-10-24.json)。[下载最终实测包和初始化脚本](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.4.5-stability-cpu-lab)，同包覆盖；桥接脚本指定 `-Package org.codeaurora.snapcam.lab`。手机仍只一个项目入口，历史Release/tag保留。
