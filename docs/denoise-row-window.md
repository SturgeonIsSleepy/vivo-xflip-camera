# NLM 原始行窗口

## 范围

2026-10-18 从 main `6a05954704f905191dd628e89bb1529628a5c2ff` 续接。此前实验版已集成逐行色调、直方图、同窗口编辑和 RGB NLM，本轮不重复实现。只替换 `PhotoAdjustmentProcessor.denoise` 的原始像素缓存，不改变 OpenCV h=5/template=7/search=21、512 分块/16 重叠、色阶/色调顺序、JPEG 写入和 UI。冻结的 basic/lab Release/tag/资产保持原样；本轮源码尚待设备复测，不替换已验收下载。

## 原始行保护

原实现保留整图不可变 Bitmap，每块从它读取，避免已降噪像素污染下一块。新 `UnmodifiedRowWindow` 在写当前输出带之前读取全宽原始行，向下一带保留所有重叠行，其余只从尚未覆盖的图像区域读取。512/16 配置中，相邻带共享32行，不能只留16行或重新从已修改 Bitmap 读取上沿。一个输出带内的横向 tile 均读取同一个快照。

缓存容量为 `width × min(height, bandSize + 2 × halo)` 个 int。每个源行只读一次；输出带必须按顺序推进，调用方只能写当前带。缓存不暴露内部数组，复制检查目标 offset/stride/矩形边界并使用 long 防溢出。Reader 的 IOException、取消、RuntimeException 或 Error 原样传播，部分读取后不允许重用窗口。该对象只用于单线程工作任务。

| 合成尺寸 | 旧整图副本像素 | 新行缓存像素 | 缓存像素差额 |
| --- | ---: | ---: | ---: |
| 3072×4096 | 50,331,648 bytes（48 MiB） | 6,684,672 bytes（6.375 MiB） | 43,646,976 bytes |
| 4096×3072 | 50,331,648 bytes（48 MiB） | 8,912,896 bytes（8.5 MiB） | 41,418,752 bytes |

这些是精确可计算的像素 payload，排除对象/数组头、行对齐、源/工作 Bitmap、tile、Mat、编码器和瞬时分配。旧 Bitmap 与新 int[] 可能分别记入 native/Java 账户，不能把差额当成真实进程峰值降低量或速度收益。小图/极宽矮图未必减少缓存，容量始终不超过整图像素数。仍需完整工作 Bitmap，未实现低内存解码或真正多曝光融合。

增加输出带读前、进度回调后及 tile 写回前的取消检查。当前 OpenCV native tile 调用仍不可在内部强行中断，不能承诺立即取消。源 JPEG 只读/另存发布事务沿用既有实现，未新增媒体权限或相机入口。

## 已执行与待执行

- 基线 `bash scripts/check_core.sh`：944,181 项通过。
- 新版同命令：66,775,056 项通过，新增65,830,875个断言；JDK17、`--release 17 -Xlint:all -Werror`。固定13MP横/竖图、非512整数倍、单像素/窄长、48组固定种子形状及多种 halo，逐 tile/halo 像素与原始公式 oracle 对照；每块后实际改写源数组，验证不会回读污染，覆盖目标 padding、读次数、溢出/失效和失败终止。
- 本地故障注入：将 retained 行数临时置0，真实运行被 `Never read an already modified source row` 拒绝；恢复后完整检查通过。故障版本未提交。
- `java scripts/ParseJava.java`：56个文件语法通过，仅语法检查；`git diff --check` 通过。本地无 PATH 中 Gradle/ADB，没有本地 Android 构建或手机操作。
- 新 `BitmapRowWindowTest` 定义3项 Robolectric API34 NATIVE 图形回归，用真实可变 Bitmap 逐块覆盖，与旧的不可变 Bitmap 副本逐 tile/halo 比较。它验证 native Bitmap 桥接，不执行 ARM64 OpenCV。双 flavor Android 单测/四 APK/Lint 待提交后的实际 CI，不能把测试定义说成已经通过。

## 设备验收与下一步

1. 有授权设备后，用相同合成 fixture 与同构建配置，对旧实验 tag `5ca9357` 和本轮候选分别运行 `python scripts/test_photo_adjustment_device.py --help` 中的检查流程。保留现有七组检查，优先 denoise 的独立整图 OpenCV 对照、full 的13MP完整保存及 cancel，核对原始输入保留。主机逐输入像素等价不替代设备实际 NLM 输出与 JPEG 验收。
2. 测量完整 decode→NLM→色阶/色调→JPEG另存的耗时及 Java/native/进程峰值，记录测量方法、采样间隔、冷热状态、多次运行和实际构建 SHA。旧版单次21,807ms只属于此前源码；本轮没有新设备测量，不沿用成提速结果。
3. 同窗口编辑的旧任务取消/Back/重建、实时直方图采样/worker恢复、basic 编译开关、前摄/内屏/全方向和长期耐久沿用共享待办，不因本缓存完成而标为验收。

真实私有照片和标识不公开。Google Camera 合法集成边界不变，NLM 仍是现有 OpenCV 成熟算法，不是 Google Camera/HDR+。
