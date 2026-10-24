# vivo-xflip-camera

针对 vivo X Flip 外屏优化的 Android 相机适配项目，目标是让外屏预览和成片方向可控，研究外屏启动相机时的系统限制，并使用合法可用的计算摄影接口。

## 项目进度

0.4.5-lab 已同签名覆盖唯一实验版，整合此前编辑器旧预览另存、Bitmap所有权、output-only重建及媒体清理修复。合盖真实Activity快速调节/原图对照/重建/返回取消通过；CPU诊断与普通保存结果像素一致，NLM滤波是主要耗时，未更换算法或承诺固定提速。实测另发现窄竖向控件采样越界，已加边界限制；完整竖屏翻转及视频防抖退出竞态仍待专项实机验收。见 [10月24日实机报告](docs/device-acceptance-2026-10-24.md)，[下载同版实测包](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.4.5-stability-cpu-lab)。

以下为各次代码审查当时的状态，候选的最新实机验收范围以上方报告为准。

修复系统相机回传预览的Activity重建源选择：已发布或编辑后的输出不再依赖私有捕获缓存仍然存在；只有OEM拍摄结果处理期间才读取私有缓存。恢复状态缺少所选来源时会退出读取状态，并清理无法继续的待发布占位，避免控件永久锁定。新增6项纯Java状态回归；当前66,775,107项核心断言、双debug共116项、四APK/Lint及21项主机契约通过。候选未安装，真实Activity重建仍待同签名.lab在X Flip验收。

晨间审查新增系统相机回传预览的显式Bitmap所有权：新预览替换旧预览时回收旧对象，过期结果、处理异常与Activity销毁也会释放未交付或已显示对象。四项native Bitmap回归在两个debug flavor均通过；当前合计116项回归、四APK/Lint及21项主机契约成功，下载XML已核验。不改拍摄、保存、算法参数或手机安装版本。

新增[可选计时回调异常隔离](docs/photo-adjustment-cpu-profile.md#2026-10-21-计时回调异常隔离)：采样RuntimeException不再从计时标记覆盖原始处理错误，失败诊断显式拒绝。故障已在原核心复现，修复后双debug共108项回归、21项主机契约、四APK/Lint通过，下载XML已核验；算法参数和手机安装版本不变。

补齐[基准逐次APK证据与BAAB对照](docs/photo-adjustment-cpu-profile.md#2026-10-20-审查逐次-apk-证据及-baab)：缺失/不同包不能再生成“同包”结论，四次计时与独立内存报告全部核验；新增BAAB起始配置互换，ABBA仍为默认。20项主机契约、双debug共100项回归、四APK/Lint已通过，下载XML已核验；不改生产算法或手机安装版本。

新增[可选CPU分阶段诊断](docs/photo-adjustment-cpu-profile.md)，显式flag复用同一拍后save/NLM路径，分列墙钟、进程CPU及调用线程CPU，普通入口不开启；保留调度、滤波和保存参数。最新候选四APK/Lint、双flavor共108项及21项主机契约测试通过，下载XML已核验。本轮没有手机CPU样本或提速结论，候选未安装。

本轮新增[编辑器预览/保存审查与修复](docs/code-review-2026-10-19.md)：新参数预览未完成或失败时，原图对照不能再用旧预览开启另存；过期预览在工作线程分配位图前退出，关闭时清理尚未交付的位图。基线缺陷已由实际CI确认；修复后10项UI回归在两个flavor各通过，全量80项及四APK/Lint成功，产物已核验。候选尚未安装手机，不改变下列实测Release。

手机只安装最新测试版，目前为0.4.5-lab（org.codeaurora.snapcam.lab）。按用户最新要求已卸载基础版及旧诊断版，照片保留；以后优先同包覆盖升级，并将桥接指向.lab，不默认并装或恢复基础版。基础版和历史实验APK仍保留在Release供明确需要时取用。

0.4.2-basic 只保留基础拍照、实时左右纠正和原始JPEG保存。已移除拍摄页自有悬浮按钮，避免与系统控件重叠；旧增强/水印/录像/多帧偏好在此构建均不生效。实机完成两次保存、重拍、取消和三次强制停止后重开，见 [基础版实测及初始化限制](docs/basic-camera-2026-10-17.md)。构建任务为 `:app:assembleCoverCompatBasic`。前两天的核心加速、保存修复及曝光初筛已续接；增强版的直方图/算法使用独立构建。

[下载基础版实测APK压缩包](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.4.2-basic)，解压后安装。同版APK与手机一致，tag固定d8e4bb8，CI38992698267成功。

0.4.4-lab 在同一APK内对照NLM调度，生产块改为768、OpenCV线程改为4，降噪强度不变。13MP ABBA保存样本中位约37→21秒，但连续运行波动很大，不能承诺固定提速；本组观测PSS增加约9MiB。六份保存结果像素一致，实拍处理中取消与原图保留通过，NLM仍默认关闭。见 [调度对照及测量边界](docs/nlm-scheduling-2026-10-18.md)，[下载同版实测APK与初始化脚本](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.4.4-nlm-scheduling-lab)。保持同包升级，只保留这一个测试入口。

0.4.3-lab 完成滚动行缓存的同机验收：六份13MP输出像素一致，本组观测进程PSS峰值约172→132MiB（下降约23%），Java/native占用发生转移。候选保存16–18秒，耗时仍需优化，不把昨日22秒当作提速基线。实际处理中Back取消及原图保留通过，基础版继续冻结。见 [设备对照与测量边界](docs/nlm-device-2026-10-18.md)。 [下载0.4.3实测实验包与脚本](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.4.3-row-cache-lab)，解压安装；切换到实验版拍摄前按报告初始化桥接。

历史0.4.2 `coverCompatLab` 实验版提供可选实时直方图、同窗口拍后黑白场滑块/原图对照/另存和可选OpenCV RGB NLM降噪，并接入前两天逐行核心。包名带`.lab`，不覆盖基础版；该版首次13MP降噪约22秒，默认关闭，不是实时降噪。见 [编辑与降噪验证及限制](docs/photo-editor-2026-10-17.md)。 [下载独立实验版实测APK与脚本](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.4.2-editor-lab)，解压后安装；切换桥接目标需按报告指定包名重新初始化。

0.4.1-dev 新增自动色阶、文字纹理增强和可选JPEG HDR色调，单张/三帧均支持，原图保留；已完成合盖实机功能检查与八组手机合成图算法检查。HDR是已有JPEG信息的色调映射，目前不是多曝光HDR采集。见 [新功能报告](docs/photo-tone-2026-10-15.md)。

[0.4.1 交付说明](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.4.1-photo-tone-test)与 [CI APK 下载](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38744983176/artifacts/11978385837)已发布。手机已安装本地实测版；该签名 APK 直传及压缩/分卷上传因网络中断失败，Release 无安装包附件。CI 包来自同一源码，签名可能不同，不代表与手机安装文件逐字节一致。全尺寸处理耗时仍需优化。

0.4.0-dev 新增“连续三张合成”：真实系统 JPEG 经 OpenCV 对齐、局部运动保护、降噪平均和轻度锐化，保留三张原图，另存全尺寸结果并可叠加已有文字水印。已完成 3 张 3072×4096 合盖实拍合成和六组手机算法检查。当前每张仍需系统确认，尚未实现单快门自动连拍、RAW HDR+ 或超分。见 [多帧实测与公平对照](docs/multiframe-2026-10-15.md)。

0.3.1 用户实际重开暴露翻转与控件失联，现已在 0.3.2-dev 修复连接恢复及旧任务栈，并增加工作进程监护与 Surface 重建。最终包完成三次强制停止后正常重开，以及一次工作进程中断后的自动恢复。手机重启仍需初始化，不能把同进程演示当长期稳定验收。见 [启动恢复修复报告](docs/reconnect-2026-10-15.md)。

[下载历史 0.3.2 修复 APK 与初始化脚本](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.3.2-reconnect-fix)。APK 已安装并与手机/远程附件核对哈希；使用新版脚本可恢复应用重开及工作进程中断，整机重启仍需初始化。

0.3.1-dev 已整合并在真实合盖外屏验证：桥接就绪后打开即拍照，右侧切换照片/录像，左侧原生设置，机型与时间文字水印另存且保留原图。完整同版 GPU 预览实测约 28 fps，拍照及带音频录像正常。成熟防抖仍待升级，系统蔡司及 Google Camera 算法未接通。见 [本轮实机报告](docs/integration-2026-10-15.md)。

[下载历史 0.3.1 实测 APK（存在重开失联问题）](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.3.1-cover-controls-test)。手机、本地与远程附件 SHA256 三者一致；更新后用同版 APK 重启初始化桥接。

已建立最小 Android CameraX 工程：前后摄切换、取景拍照、预览/照片镜像独立控制、窗口与摄像头诊断、成片 EXIF 检查。两个镜像开关默认关闭，照片保存到 Pictures/XFlipCamera。0.1.1-diagnostics 新增独立“照片像素检查”分享入口，后台采样 JPEG 的 RGB 与 EXIF；无需开启相机，结果仅辅助取证，不代表黑屏或镜像已修复。

0.1.6-dev 新增“设置 → 系统相机”回传路径：已在实际合盖外屏连续拍摄并返回两张有效 JPEG，取景和成像由 vivo 原生相机完成。本应用的 CameraX 预览仍黑，原生预览仍有镜像现象，Google 算法尚未接通。独立外屏入口、镜像与折叠验收仍待完成，见[本轮实机报告](docs/device-test-2026-10-13.md)。

[下载本轮实测 APK](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.1.6-device-test)。预发布附件就是已在手机安装并验证的 coverCompat 包，SHA256 已与远程资产及手机 base.apk 核对；CI 构建包与本地 debug 签名可能不同。

独立 Java 方向核心已通过本地编译及 13,486 个断言检查；0.1.1-diagnostics 的远程 Android APK 构建、Lint、13,511 个核心断言及原始 JPEG 独立重解码均已通过，已产出 debug APK。[第一版有线实机测试](docs/device-test-2026-10-12.md)未通过外屏验收：合盖预览及两张成片全黑，系统仍提示翻开手机；原生相机对照可正常预览。镜像方向与外屏独立使用仍待修复后验收。

- [技术基线与实机验收清单](docs/technical-baseline.md)
- [构建、验证与当前待办](docs/build-and-verify.md)
- [第一版实机结果、原始证据与两个 agent 的下一轮任务](docs/device-test-2026-10-12.md)
- [独立 JPEG 像素检查与本轮代码审查](docs/code-review-2026-10-13.md)
- [开发日志与待办](DEVELOPMENT_LOG.md)
- [每天 02:00 开发（含周末和法定假期）](docs/work-task.md)
- [中国工作日 09:00 检查任务](docs/morning-review-task.md)

0.2.9-dev 已在合盖外屏验证拍照、录像的实时左右纠正及录后视频防抖，确认回传、原片保留、另存和播放正常。防抖为平移补偿，0.3 至 60 秒 SDR 视频，约 9.1% 可见边缘裁切；还没有实时 EIS 和 Google Camera 成像算法。需电脑初始化 Shell 桥接，覆盖层会挡住取景点击对焦，录像计时背景有局部拼接。见 [防抖与最终包报告](docs/video-stabilization-2026-10-14.md)和 [实时翻转报告](docs/live-preview-2026-10-14.md)。

[下载实时翻转实测 APK](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.2.6-preview-test)，手机、本地与远程附件 SHA256 三者一致，初始化脚本及限制见上述报告。

[下载历史 0.2.9 防抖实测 APK](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.2.9-stabilization-test)。打开“外屏系统相机”，运行初始化脚本后直接拍照/录像；录像确认返回后点“视频防抖”，结果另存、原片保留。真实录像源/输出均 82 帧、720×1280、约 2.74 秒并保留音频；合成输入独立测量仅代表该测试场景。

0.3.0-dev 为预览性能候选版：GPU 路径在混合调试会话实测从约 10 fps 提高到约 29 fps，识别漏检保持方向。双版本构建/Lint、13,817 项断言通过，完整候选包因手机断线尚未安装复验。界面重排、水印和更成熟防抖仍待开发，见 [性能收尾与边界](docs/preview-performance-2026-10-14.md)。

[0.3.0 CI 构建包](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38583427109)已上传到 Artifacts。它的签名可能不同于手机现有包；本地签名候选 APK 直传因网络超时未完成，空 Release 已转草稿。

新增独立 [CoverControls 外屏控件组件](docs/cover-controls.md)：左侧设置、右侧拍照/录像使用三个小型原生窗口，按当前尺寸、密度与 Insets 布局，避免新增整屏触摸覆盖。0.3.1 已接入并验证实际模式切换、设置、快门和确认；加载时短暂窗口及更多生命周期边界见本轮报告。

新增独立 [自制文字水印处理器](docs/text-watermark.md)：原图只读、EXIF 方向归一，文字副本另存，复用发布与回滚检查。两个 flavor 各 8 项合成图/故障回归通过；0.3.1 已接入设置与拍摄页，真实 JPEG 的 FD 写入成功；这是自制文字水印。SDR JPEG 重编码/底部覆盖、元数据及 GPS 保留规则见文档。

独立“照片像素检查”现已保存最近一次分享/选择器选中的照片及文字报告，避免页面重建后回读最初分享的照片；已完成报告直接恢复。状态核心通过 35 个新增场景断言，页面恢复和临时权限仍待实机验收，见[照片检查恢复审查](docs/code-review-2026-10-14.md)。

## 技术方向

优先运行时查询 vivo X Flip 上可用的 CameraX/Camera2 厂商扩展。Android 官方已知支持设备列表包含 Vivo X Flip，但具体镜头和模式仍需在设备上逐项查询。Google Camera 的专有 APK、库、素材或代码不会在未获授权时复制或分发。

外屏可用区域、窗口尺寸和旋转必须运行时检测；682×422 仅作设备背景参考，不作为硬编码参数。

## 验证状态

新增[只读多曝光元数据初筛组件](docs/exposure-bracket-screening.md)，拒绝同曝光、ISO-only、缺字段或重复文件等本路线不适用输入。仅返回元数据候选，不证明真实采集/同场景/可用像素，未接入拍摄、不生成HDR。

通用保存事务已修复 Error 跳过回滚及清理错误覆盖原始失败，新增9项共享JPEG/EXIF/取消故障回归；主机测试不代表真实OOM可恢复。详见[本轮保存审查](docs/code-review-2026-10-16.md)。

新增[逐行色调核心与 13MP 主机对照](docs/photo-tone-row-performance.md)，已在10-17接入 `PhotoEnhancer`，旧/新设备合成输出最大通道差0。早期主机HDR行循环约1.435倍不代表手机总流程提速，默认无HDR未证明明显收益。

历史[NLM滚动原始行缓存](docs/denoise-row-window.md)在512/16配置下，把3072×4096的原始缓存payload由48MiB改为6.375MiB；后续已完成[同机验收](docs/nlm-device-2026-10-18.md)。当前0.4.4实测版使用768/16，理论缓存9.375MiB，耗时/进程内存与限制以调度报告为准，不把缓存payload当成峰值，也不重复实现已完成的缓存。

本地已验证 EXIF 1..8 的非对称图案、像素映射和反向变换，预览/成片镜像偏好独立性，以及旧拍照回调不能解锁新拍照的生命周期保护。可运行 bash scripts/check_core.sh 复查。

[最新诊断候选四APK、Lint、66,775,101核心断言及双flavor回归](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/39472794926)已核验成功；下载测试ZIP摘要匹配，12份JUnit XML共108项，各flavor54项，失败/错误/跳过均0，主机契约21项亦通过。候选未安装；当前手机仍是上述0.4.4实测Release。CameraX外屏仍黑，系统回传与Shell预览纠正、同窗口编辑已有合盖成果，不能据此视为前摄/内屏/全方向/长期耐久验收。初始化独立性、点击对焦、worker/切屏、CPU热点、Google成像及逐镜头扩展仍沿用待办。

