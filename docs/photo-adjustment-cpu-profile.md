# 拍后保存的可选 CPU 分阶段诊断

2026-10-20，从 `088e48685aef356ea3c003f41e1040c4a6ca86eb` 续接。

已有0.4.4同包ABBA证明结果一致，但耗时漂移很大，不能据此定位CPU热点。本轮实现此前仅规划的诊断工具，复用 `PhotoAdjustmentProcessor.save` 和同一个NLM循环，不复制算法。尚未连接手机，没有本轮CPU数值、热点结论或提速/画质声明。

## 范围与计数含义

普通编辑器、现有调度基准和内存采样脚本默认不创建计时器、不读取这些CPU时钟。只有debug/lab instrumentation经 `--cpu-profile` 显式开启。生产仍为768块、4线程，h5/7/21、halo16、滚动原始行缓存、JPEG97及NLM默认关闭均不变。未改变版本、Manifest、权限、桥接或现有Release，未安装候选。

每个阶段取三个累计计数的相邻差，阶段互斥，可重复的tile阶段只累计固定数量的字段，不保存逐tile记录或像素。结束收据不可变，同一工作线程持有，重复finish不再读时钟。

| 字段 | 时钟与范围 | 解释限制 |
| --- | --- | --- |
| `wall_ns` | `SystemClock.elapsedRealtimeNanos()` | 单调墙钟，包含休眠、等待、I/O；纳秒单位不保证纳秒准确度 |
| `process_cpu_ms` | `Process.getElapsedCpuTime()` | 整个应用进程CPU，含UI、GC和其他线程；不是OpenCV专属，也不包含独立系统相机/桥接进程 |
| `calling_thread_cpu_ns` | `Debug.threadCpuTimeNanos()` | 仅调用处理器的工作线程；不能代表全部原生并行线程，官方也指出可能计入部分I/O等待 |

多核进程CPU可以大于墙钟时间，不截断为100%。进程CPU为毫秒粒度，短阶段读数0不表示免费；三个时钟是顺序读取，非原子快照。线程接口不支持时的-1、回退或累计溢出标为JSON `null`，相应总计也为null，不改成0。时钟读取和累计自身有开销，计入相邻阶段，未测量或扣除。

| 阶段 | 包含的操作 |
| --- | --- |
| `DECODE` | 入口取消检查、完整尺寸JPEG解码和格式/gainmap检查 |
| `NLM_SETUP` | OpenCV初始化、线程设置、滚动行窗口与Mat创建 |
| `NLM_ROW_WINDOW` | 行带推进与原始重叠行保留，含循环/取消检查 |
| `NLM_TILE_INPUT` | 进度回调、tile准备、像素复制、Bitmap到Mat、RGBA到BGR |
| `NLM_FILTER` | 同一 `Photo.fastNlMeansDenoising` 调用，不更换滤波器 |
| `NLM_TILE_OUTPUT` | BGR到RGBA、Mat到Bitmap、核心块回写和tile回收 |
| `NLM_RELEASE` | Mat释放及其后取消检查 |
| `INPUT_LEVELS` | 原有输入黑白场行处理；未开启时保留少量检查开销 |
| `TONE_DETAIL` | 现有PhotoEnhancer整体调用，不细分其内部算法 |
| `JPEG_TRANSACTION` | 进度回调、JPEG97、EXIF、MediaStore发布及失败回滚整体，不单称JPEG编码耗时 |
| `BITMAP_RELEASE` | 工作Bitmap的finally回收 |

计时仅围绕一次生产save及其清理，不含准备用基线JPEG、独立整图NLM参考、APK摘要、输出拉取、质量统计或后续清理测试文件。`save_ms`沿用原有nanoTime定义，记录于JSON导出前；墙钟与nanoTime的休眠语义不完全相同，不能强求二者逐纳秒相等。失败/取消可导出已完成部分，但不能把它当成完整保存样本；脚本只接受测试成功且outcome一致的收据。

## 设备复现及验收

由已有授权设备负责人准备同一候选 `.lab` APK并确认签名可同包升级，先复测编辑器快速滑动/原图对照/Back/重建。本工具不安装、不改全局性能/DPI设置、不请求root，也不增加入口。当前手机实测包仍是0.4.4 Release，不能对旧包加flag后声称有新诊断。

复用既有 `generate_photo_adjustment_fixture.py` 的 `nlm-full.jpg` 和设备脚本。示例中的SERIAL替换为已授权设备，各次目录内须有相同合成输入；不要改成私人照片或公开私人EXIF/文件名/摘要。

```sh
python scripts/test_photo_adjustment_device.py --serial SERIAL --scenario full --cpu-profile --nlm-tile 512 --nlm-threads 2 /path/to/profile-a1
python scripts/test_photo_adjustment_device.py --serial SERIAL --scenario full --cpu-profile /path/to/profile-b1
python scripts/test_photo_adjustment_device.py --serial SERIAL --scenario cancel --cpu-profile /path/to/profile-cancel
```

结果保存在各目录原有的 `full-edit-report.json` / `cancel-edit-report.json`，新增 `cpu_profile` 及已安装 `profile_base_apk_sha256`。核心计时不含基线/摘要，报告保留原有 `original_preserved`、实际OpenCV线程、请求调度、JPEG方向和取消检查。拒绝旧包漏回收据、坏JSON、未知scope、负数、重复stage及阶段和总计不一致的数据。摘要只标识base.apk，本工程当前非split交付，不冒充签名或额外split校验。

受控采样先做不纳入统计的预热，固定输入/APK/功能组合，按ABBA和逆序BAAB重复，记录每次顺序、ROM、热状态、供电/亮度/其他负载，样本不足或漂移则保留原始值而不下固定性能结论。thermal状态0不能证明频率稳定。独立解码所有profile/unprofile输出，验收最大通道差0，原图完整字节不变、取消无新增媒体行。诊断样本及带dumpsys内存采样的样本均不得混入无诊断的速度中位；既有 `benchmark_nlm_save_device.py` 保持未加诊断的原路径。

这些阶段只能给出候选耗时区间；需要细到原生函数/核调度时，再在用户授权的设备上选择可用的采样工具。没有证据前不继续增大tile/线程，不承诺可避开系统相机策略。

## 本轮验证与后续

- 本地JDK17 `bash scripts/check_core.sh`：66,775,080项，其中新增24项计时记账断言。假时钟验证重复阶段、无双计、单位、并行CPU、未知/回退、finish、固定容量、跨线程拒绝和不可变收据；不是手机CPU读数。
- `python -m unittest discover -s scripts -p 'test_*_host.py' -v`：10项通过，纯JSON，无ADB。`--help`入口、三份Python的py_compile、60份Java语法及 `git diff --check` 均通过；语法不等于Android类型解析。
- 新增Android定义：真实native Bitmap下诊断开/关的手动色阶/像素/取消顺序、debug JSON单位/null；共享保存回归新增完整JPEG/EXIF相同、发布Error原实例回滚、写入取消。NLM/真正MediaStore和设备CPU仍待真机。Android执行结果待本轮CI，不能以定义替代运行。
- 下一步先取得同一APK的合成图profile、未profile像素一致和取消证据，再按最高实际占比提出一个最小改动；跨场景质量与前后摄/方向/切屏矩阵继续复用既有待办。

官方依据（2026-10-20核对）：[elapsedRealtimeNanos](https://developer.android.com/reference/android/os/SystemClock#elapsedRealtimeNanos())、[进程CPU](https://developer.android.com/reference/android/os/Process#getElapsedCpuTime())、[调用线程CPU](https://developer.android.com/reference/android/os/Debug#threadCpuTimeNanos())。未复制专有Google Camera代码或算法，不将本工具称为Google成像集成。
