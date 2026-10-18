# 开发日志

## 2026-10-12｜首次技术基线

- 模型：运行环境未暴露可验证的型号，本次记录为“无法核实”，并按用户最新要求继续。
- 起始状态：Public 仓库 main 分支只有 README.md；最近提交为初始提交 918ca1506a0e948a6a5e735192140fdabffdfc12，没有源码、测试或开发日志。
- 本次目标：项目尚无实现，先建立合法成像路径、预览/成片变换边界和实机验收标准，给后续代码实现提供可追踪依据。
- 研究结果：Android 官方 CameraX 支持设备列表包含 Vivo X Flip；这只说明设备在已知支持名单中，不代表每个镜头支持每种扩展，必须运行时查询。前摄预览与保存照片应使用独立镜像策略；窗口尺寸、方向和折叠状态应运行时读取。“翻开手机”提示目前没有系统日志，根因未确认。
- 本次改动：新增 docs/technical-baseline.md，记录 API 依据、GCam 合法集成边界、双摄像头验收矩阵、系统提示复现流程和待办优先级。
- 文档提交：aa9ab40（technical baseline）。
- 验证：已对照 Android Developers 与 AOSP 官方文档核查公开接口、API 限制和 CameraX Extensions 版本提示；本次为文档增量，没有 Android 构建或代码测试。
- 未验证：X Flip 实机外屏预览/成片镜像、每摄像头扩展模式可用性、外屏第三方 Activity 权限及“翻开手机”弹窗来源。
- 下一步：
  1. 创建最小 CameraX 预览/拍照工程，记录运行时窗口尺寸、旋转及可用摄像头。
  2. 实现前后摄像头各自的预览与成片镜像策略，并加入可重复的变换测试。
  3. 借助 X Flip 查询扩展能力并按复现清单收集外屏限制证据。

## 2026-10-12｜Work 定时任务配置与仓库续接

- 起始提交：fedd9b7dac7955041b5b371d9e13f5d653b0f535。
- 目标仓库：按用户最新指定使用 Public 仓库 SturgeonIsSleepy/vivo-xflip-camera；已确认连接器具备读取和推送权限。
- 复用内容：已读取 README.md、原开发日志和 docs/technical-baseline.md，保留已完成的技术基线与实机验收清单。
- 本轮成果：保存并启用“开发 X Flip 外屏相机”Work 原生定时任务，时区 Asia/Shanghai，每天北京时间 00:00；已关联创建任务的当前 Work 对话。
- 下次计划执行：2026-10-13 00:00（北京时间），依据已保存日程推算；接口未返回非空 next_run_time，不宣称已实际执行。用户原指定的 2026-10-12 00:00 在配置时已过去，未调用立即运行。
- 执行规则：先读取现有代码和日志、完成一个可验证的未完成小目标、更新本日志、创建并确认远程 Commit；授权或设备不足时暂停相应操作，不重复开发。
- 模型：保留 GPT-6.1 Sol / Ultra 偏好；实际型号及推理强度无法从接口核实。按用户随后“模型无法核实时说明一下即可，但要继续进行任务”的授权继续。
- 本轮文件：新增 AGENTS.md 与 docs/work-task.md，并在 README.md 增加配置入口。本条目的对应 Commit 以 Git 历史为准。
- 验证：任务更新接口明确返回 success=true、is_enabled=true、Asia/Shanghai 和每日 00:00 日程；已确认仓库 Public 和读写权限，并检查仓库已有内容。
- 本轮只完成任务配置与续接文档，没有 Android 源码改动、应用构建或真机测试。
- 下一步：沿用上一条日志的待办，建立最小 CameraX 预览/拍照工程，并读取运行时窗口尺寸、旋转和可用摄像头；X Flip 真机验证保持待办。

## 2026-10-12｜新增中国工作日晨间任务

- 起始提交：bffd46ff9522c96d6cdbebbfc616c451eb0aecf7。
- 用户增量要求：中国工作日上午北京时间 09:00 继续工作，优先检查代码、修复错误和规划下一步；在限额充足时继续开发。
- 已保存并启用“检查 X Flip 相机代码”原生任务，时区 Asia/Shanghai，首次计划运行 2026-10-12 09:00，已关联本 Work 对话。接口 next_run_time 为空，未声称已实际执行。
- 原每日 00:00 开发任务的日程与启用状态保持不变，新增维护晨间官方工作日表与新年度日程例外的执行规则。
- 工作日依据：已核查国务院 2026 年放假调休通知的政府网站转载；缓存包括 33 天假期与 6 个周末补班日。2026-10-12 起至年底的中国工作日与周一至周五一致，2027 年表标记为尚未核查可靠依据，后续按官方公告补齐。
- 已写明晨间任务先审查、修复、规划，再利用可用额度推进一个可验证的小目标。精确余额读取接口当前不可用，不能保证余额百分比；遇到实际限额提示结束本轮，保留后续日程。
- 本轮文件：新增 docs/morning-review-task.md、docs/china-workday-calendar.json；更新 docs/work-task.md、AGENTS.md、README.md 和本日志。
- 验证：工作日缓存的假期数量、补班与假期互斥、补班周末/假期工作周代表日期、今日工作日判定及本年剩余日程一致性均已检查；任务创建/更新接口明确返回成功，晨间任务已启用，午夜日程未变。
- 本轮为任务配置与文档增量，没有 Android 应用代码、构建或真机测试。
- 后续应用开发仍沿用原待办，避免重复研究已有技术基线。

## 2026-10-12｜明确午夜任务在休息日照常执行

- 起始提交：1730d68cf426b79418e74859eef0f66ed1b92280。
- 明确执行日期范围：午夜 00:00 任务每天运行，包括周末、法定节假日及其他休息日；中国工作日限制仅适用于 09:00 晨间检查任务。
- 已在两项原生任务提示及项目规则中写明范围，避免读取晨间工作日规则时误跳过午夜开发。
- 验证：更新接口确认两项任务仍启用，午夜 DAILY 与晨间工作日日程均保持原值。
- 同步修改任务配置文档、AGENTS.md 与 README.md；本轮没有 Android 代码或真机验证变化。

## 2026-10-12｜最小相机工程与方向核心

- 起始提交：772b7763b72753aa2951739f59554de98b593143。
- 审查发现：起始仓库只有技术与任务文档，没有 Android 源码、构建配置或已有失败检查；本轮复用现有研究，继续 P0 工程实现，没有声称修复不存在的旧应用问题。
- 新增实现：Android/Java CameraX 1.6.2 最小工程、前后摄切换、前台生命周期绑定、MediaStore 拍照、独立且默认关闭的预览/照片镜像偏好、共享 ViewPort、动态窗口/旋转/摄像头诊断，以及最近成片 EXIF 信息。
- 新源码审查与修复：增加 CaptureGate token，退出前台取消当前 UI 拍照状态，旧或重复回调不能解锁恢复后的新拍照；成片 EXIF 异步结果按保存 token 归属，避免旧结果覆盖新照片信息。偏好以不可变快照传给每次拍照，防止预览镜像影响成片。
- 独立验证：bash scripts/check_core.sh 使用现有 JDK 17 编译模块，开启 --release 17、-Xlint:all、-Werror；13,486 个断言通过，覆盖 EXIF 1..8 非对称样例、矩形映射边界与反向变换、镜像开关独立性和拍照生命周期 token。
- 源码检查：java scripts/ParseJava.java 通过 5 个 Java 文件的语法解析，仅证明语法合法，不代表 Android 类型或依赖解析通过。Manifest/资源 XML 和工作流 YAML 已解析；提交前检查差异空白。
- 构建：本地没有 Android SDK 与 Gradle，因此没有执行本地 APK 构建或 Android Lint。新增 GitHub Actions 定义，包含核心检查、assembleDebug、lintDebug 和成功后 APK 上传；定义本身不代表远程构建成功，需读取实际结果。
- 文档：更新 README 与技术基线当前默认镜像策略，新增 docs/build-and-verify.md，记录版本、复现命令、真实验证范围和下一步。
- 限制：没有 X Flip 实机或系统日志，外屏实际启动、预览/成片方向、折叠切屏和“翻开手机”提示原因仍待验证。没有引入 Google Camera 专有算法或厂商扩展成像，不将标准 CameraX 结果称为 GCam。
- 模型/推理强度无法核实，剩余额度无法读取；按用户已授权规则利用当前可执行能力推进本轮目标。
- 更新待办：P0 核验远程 APK 构建与 Lint；P1 外/内屏前后摄和四方向真机验收；P2 取得 ROM/Android 与启动/折叠日志定位系统提示；P3 增加可用厂商扩展查询并实测。仅暂停依赖设备的验证，项目仍有独立可继续事项。

## 2026-10-12｜远程构建验证结果

- 验证源码提交：efe0150d275bc5b84a19e65228c52e93e0e531f3。
- 构建链接：https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38191883873。
- 远程执行成功：核心检查 13,486 个断言、Android assembleDebug、lintDebug，日志明确 BUILD SUCCESSFUL；APK 与 Lint 报告已成功上传。
- artifact：xflip-camera-debug，大小 5922640 bytes，archive digest sha256:c25389d165b5dd59db3fdbf2bf5a4c8b5a680a8460a18dff71451d57ad3bd00b，当前到期时间 2026-10-19T01:28:25Z。
- 已核对远程 main 指向源码提交，全部 21 个新增/修改文件的 Git blob 哈希与本地一致，脚本可执行位也匹配。
- 规划更新：P0 最小工程源码与 APK/Lint 构建验证完成；P1 前后摄、内/外屏、四方向及折叠切屏真机验收继续待设备；P2 系统提示根因待 ROM/Android 与复现日志；P3 厂商扩展查询与可用成像模式待实现和实测。
- 未声称进行任何 X Flip 真机测试，未集成 Google Camera 专有算法。项目仍有可继续的开发事项，两项既有任务继续保留。

## 2026-10-12｜第一版有线实机验收与协作交接

- 起始提交：776a9b867271ac434be2f30cb9993a092b56af5f；测试 APK 对应源码 efe0150d275bc5b84a19e65228c52e93e0e531f3，版本 0.1.0-dev。
- 用户指定本轮角色为唯一有线实机测试负责人，通过本 GitHub 仓库向代码撰写与基础错误检查两个 agent 交接。已建立 Issue 1，未通过其他聊天渠道协调。
- 下载 Actions run 38191883873 / artifact 11722060632；ZIP SHA256 与上传 digest 完全一致。用户手动安装后，从实机拉取 base.apk，SHA256 与原始 APK 一致，为 8329d69e6a0b460e18c87748595c4168e44c04cf72ca21d43ee45674b7436659。
- 实机为 V2256A，Android 14 / API 34，ROM PD2256_A_14.1.16.1.W10.V000L1；合盖 device_state=CLOSED、无 override；外屏 displayId=1，窗口和预览均为 682×422，display_rotation=0，installOrientation=ROTATION_270。
- 安装前置：ADB install 与 pm install 未完成，解锁后仍等待；合盖打开 APK 被 com.vivo.fliplauncher/.FlipToContinue 拦截。用户通过文件管理完成一次安装，随后相机验收全程合盖。
- 首轮结果：外屏 Activity 冷启动成功、设置与诊断可操作、快门保存成功；预览黑屏，两张原始 JPEG 均为全黑。每张 62,824 bytes，原始尺寸 4096×2534，独立 Pillow 解码确认三个 RGB 通道 extrema 均为 [0,0]，EXIF=6，无 GPS。
- 系统提示已定位：com.vivo.daemonService 的 com.vivo.camera.action 窗口，UID 1000、displayId 1、type 2009。相机权限已授予，CameraService 可连接 ID 0 且产生帧；不能把连接或文件保存成功当作有效图像证明。
- 对照：用户确认镜头无遮挡、朝向明亮物体后重复拍照，仍全黑。同一合盖场景的 vivo 原生相机能显示真实画面，使用 Camera ID 4；本应用诊断仅枚举 ID 0、1，均为 facing=1、sensor_rotation=90，切换按钮提示“另一摄像头不可用”。ID 差异是后续排查线索，尚不能保证第三方可访问 ID 4。
- 镜像：默认两项为 false，成片 EXIF 6 不含反射；预览镜像开关曾实际写入 back.preview=true、back.photo=false。因为没有有效图像，像素方向与照片镜像仍未通过验收；受其他前台页面切换影响的点击不计为成功。
- 本轮整理 docs/device-test-2026-10-12.md、四张应用/系统提示截图、两张原始黑色 JPEG、UI 诊断、必要系统摘录、照片量化 JSON 与可复现 inspect-photos.py。真实场景、图库缩略图、完整全机日志及设备序列号不公开上传。
- 检查：原始照片独立解码、GPS 检查、APK/ZIP/已安装包哈希、系统窗口归属与原生相机对照；提交前运行 git diff --check。本轮未改相机源码，不重复运行与该证据增量无关的 Android 构建或核心测试。
- 清理：已恢复本轮新建镜像偏好为初始默认状态，外屏密度从被触控干扰后的 200 恢复为原来的 160，并读取显示信息核验；停止测试相机后 CameraService 无活动客户端，取消明确属于本轮的安装会话。
- 更新待办：P0 优先修复合盖有效图像路径与诊断，P1 建立独立外屏入口并恢复镜像实拍验收，P2 Google Camera 合法可用成像接入仍未实现。具体分工见实机报告和 Issue 1。
- 代码 agent：做运行时 Camera2 小规模探测，调查公开 ID 与原生 ID 4 的差异，补齐相机/预览流/Surface 诊断，提供明确源码 SHA 对应的下一版 APK；禁止仅移除提示或硬编码 ID 4 后宣称成功。
- 检查 agent：独立复核证据与镜像验收边界，审查 selector、bindCamera、显示变更和 CaptureGate 生命周期，下一版运行核心检查、assembleDebug、lintDebug，再交回实机验收。

## 2026-10-13｜黑屏证据复核与独立 JPEG 像素检查

- 起始提交：c3f50ce4acbfb87c088490ecb9539e3b51f66e84；读取最新 main、项目规则、近期日志、任务配置、首版实机报告及 Issue 1 后续交接，确认 Public 仓库与连接器 push 权限。
- 工作日：Asia/Shanghai 实际日期 2026-10-13，复用已核查 2026 年缓存，属于工作日；2027 年下一次检查不早于 11-01，未重复查询、未更改任何已有日程。
- 审查：设备证据一致指向合盖全黑且系统提示来自 vivo daemonService；两项公开 ID 都为后摄与前摄 selector 不可用相符，不能据此断言 ID 4 可被第三方访问。CaptureGate、镜像快照及 EXIF 检查继续复用。
- 静态发现：bindCamera 缓存不含 displayId，同尺寸/旋转的显示屏迁移可能遗漏重绑定；缺少实际选中 ID、相机及 PreviewView 流状态。按 Issue 1 最新交接避免并发修改 MainActivity，本轮保留该文件原 blob，缺口写入 docs/code-review-2026-10-13.md 交回实机负责人，未宣称已修复或确认黑帧根因。
- 修复：inspect-photos.py 在原始 JPEG 缺失时原先输出 []、退出 0；本轮已复现，现明确报错并退出非零，避免把未执行检查计为成功。技术基线与当前构建规划中“无实机证据/未知提示归属”的过时状态已按首版报告更新。
- 实际开发：新增独立 PhotoInspectionActivity 分享入口和 JpegPixelInspector，只读用户选择的 content JPEG，后台预读尺寸、按 2 的幂降采样至长边不超过 512，显示 RGB 范围、非黑样本、EXIF；失败不计为全黑或验收通过。版本更新为 0.1.1-diagnostics / 2，未改相机主页面，未打开或硬编码厂商 ID。
- 核心：新增 RgbFrameStats 与 JpegSamplePlan；单通道仅值 1 也识别为非零，拒绝空输入、无效尺寸、超过 1 亿像素和整数溢出输入。新增 JVM PhotoEvidenceChecks 纳入 Gradle check，对原始设备 JPEG 校验 SHA-256、尺寸及全分辨率 RGB，不以采样代替原始证据。
- 本地验证：bash scripts/check_core.sh 通过 JDK 17 / --release 17 / -Xlint:all / -Werror 的 13,511 个断言；java scripts/ParseJava.java 通过 10 个 Java 文件语法解析，未代表 Android 类型解析。修复后的 Python 脚本在缺照片目录退出 1，真实原图未在本地重新解码。
- 环境限制：没有 Android SDK、Gradle、ADB 或已连接 X Flip；GitHub 文本连接器拒绝 JPEG 二进制，原始照片重解码交给远程 CI checkout 后执行。新源码 APK/Lint 与原始照片检查已通过实际远程日志核验，见下一条；没有进行本轮真机测试。
- 待办：P0 有线负责人继续 Camera2 ID/合盖策略与实际绑定状态探测，接续 displayId 修复并证明明亮场景有效图像；P1 验收新 JPEG 页面，再在有效图像恢复后执行前后摄/内外屏/四方向/折叠与镜像矩阵；P2 合法成像接入与厂商扩展查询。当前只暂停依赖设备的操作，未集成 GCam 专有算法。
- 模型/推理强度无法核实，剩余额度无法读取；没有限额提示，按已授权规则完成范围明确的独立目标。项目仍未完成，未停用后续任务。

- 远程验证完成：源码提交 800935ea24ad1638749dd4ed0cae8985a7a868e9；https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38331262454 已 completed / success。实际执行核心 13,511 个断言、photoEvidenceChecks、assembleDebug、lintDebug，日志为 BUILD SUCCESSFUL。两张原始 JPEG 的 SHA-256 匹配，JVM ImageIO 完整解码 20,758,528 个像素，RGB 全零。
- 新版 artifact：xflip-camera-debug / 11782463018，ZIP 5928273 bytes，digest sha256:f8285f504436ea7b4ebd03f639d121a52cecb02fb1954e545ac3e35b18973f33，APK 与 Lint 报告成功上传，保留至 2026-10-20T01:16:11Z。已核对远程 16 个新增/修改文件 blob 与本地一致、MainActivity 保持原 blob。真机页面/采样验收及黑屏修复仍未完成。

## 2026-10-13｜有线续接修复与合盖系统相机回传

- 起始本地提交 c3f50ce；先 fetch 并快进到 9404224，保留另一 agent 新增的 JPEG 工具、照片证据与核心检查。合并只在版本号配置冲突，采用 0.1.6-dev / versionCode 7，保留两 flavor 与全部上游功能。
- 用户授权实机负责人优先修复、查现有方案和系统相机接口；仍只通过本仓库 Issue 1 与两个 agent 沟通，未启动另一个手机测试者或通过其他聊天分派。
- 本轮诊断：实际 Camera ID、CameraState、PreviewView stream、受控 camera_id 请求及直接 Camera2 查询错误；修复 displayId 缓存遗漏，加入 KEEP_SCREEN_ON 和采样黑图提示，复用上游 RgbFrameStats。35 秒无触摸时外屏仍 Awake、mStayOn=false；不修改设备全局休眠设置。
- 排除改包名假设：org.codeaurora.snapcam 仍只枚举 0、1，查询 ID 4 报 system only device。ADB Shell 的 SYSTEM_CAMERA 显示 granted，真实 UID 2000 / com.android.shell 通过 scrcpy 上下文直接查询 ID 4 仍被拒绝。scrcpy 4.1 也仅枚举 0、1；ID 0 短录像产生文件但视频解码未成功，不计有效像素。
- Google 现有方案：手机已安装 AGC 9.2 的公开 IMAGE_CAPTURE Activity 可在外屏启动，但出现 vivo 翻开提示、预览黑屏。未改其配置、未分发 APK、未接通 Google 成像。
- 有效图像路线：新增 SystemCaptureActivity，使用公开 ACTION_IMAGE_CAPTURE 调用 com.android.camera、明确 launchDisplayId，实际 ID 4 可真实取景。前期 MediaStore 输出直接回传取消，改用狭窄 cache FileProvider URI、ClipData 和临时读写授权后全尺寸 JPEG 成功返回，再复制到本应用 MediaStore 并发布。
- 0.1.6 coverCompat 实际安装包与本地 SHA256 均为 9a5bdf82581c3991dffcd33f33214589a0cd1020cc967401c10f9420fcf00d80，15,641,006 bytes。全程实际 CLOSED、无 override、display 1。连续两次拍照均返回自有外屏显示并保存：3,096,607 / 5,014,711 bytes、3072×4096，Pillow 独立全图解码 RGB 非零；取消未新增空文件，已保存照片保留。0.1.5 另有一次有效回传。
- 原生成片含 GPS 元数据，公开证据只含其存在标志、图像统计和 SHA，不含坐标、真实场景 JPEG 或照片截图。未上传厂商 APK、反编译结果或完整全机日志。
- 原生预览仍镜像，外屏设置未发现对应开关；本应用 CameraX 两项镜像偏好不作用于外部相机。没有宣称镜像、Google 算法或独立外屏入口已完成。既有外屏助手影响普通 Activity 的合盖启动，暂停它后出现 FlipToContinue，恢复后可启动；保留用户该配置。
- 最终 CameraX ID 0 为 OPEN、STREAMING、CAMERA 已授权、last_error=none，却仍拍出 62,824 bytes 全黑图。新黑图提示实际显示，独立全图解码三个 RGB 范围均 [0,0]。
- 检查 agent 的独立 JPEG 页面已在外屏完成真实黑图与有效系统照片连续分享：黑图 512×317、全零，明亮图 384×512、196,608 个非零采样，EXIF 缺失显示 unknown，没有旧结果覆盖。
- 本机 JDK 21 / Java 17 目标、共享 SDK、Gradle 8.11.1：两个 flavor assemble 与 Lint、13,511 项核心断言、两张原始黑 JPEG 共 20,758,528 个像素及哈希检查通过。一次构建因挂起的旧 ADB install 占用 APK 而失败，终止安装命令、重跑后 BUILD SUCCESSFUL in 19s；后续安装使用独立副本。standard 仅构建，coverCompat 才有上述真机验证。
- APK 全程外屏文件管理安装，版本化文件名避免旧安装元数据缓存；未要求展开。已恢复本测试包默认镜像偏好，保留系统相机原 4:3 / 无延时设置，没有修改全机 DPI。
- 文档与证据：docs/device-test-2026-10-13.md、docs/evidence/2026-10-13-system-capture，更新 README、技术基线、双 flavor 构建与工作流。实测 APK另上传 GitHub 预发布供其他 agent 下载，发布结果以实际远程核验为准。
- 下一步：代码 agent 在系统回传基线上完成外屏直接入口与可恢复状态，再研究取得真实预览的可行接口；检查 agent 审核 Activity Result、URI 授权、取消/读取失败/退出与恢复的输出归属。共同保留独立 JPEG 工具，禁止把系统成像称为 Google 或把 STREAMING 当像素验收。
- 未验收：非对称卡片左右方向、四方向、展开/内屏、独立入口、进程恢复、损坏/无权限 JPEG、厂商扩展及 Google 成像。用户无法展开，只暂停需要展开的验证；继续可独立推进事项。实际模型/推理强度无法从任务接口核实。

## 2026-10-13｜实测 APK 发布与双 flavor CI 读回核验

- 验证源码提交：6f9815e9bcd3e329c30892967bef289b3dbe2cc8；已 push main 并用 ls-remote 确认完全一致。
- GitHub 预发布 v0.1.6-device-test 已建立，实测附件 xflip-camera-0.1.6-cover-compat-debug.apk / asset 636886828 / 15,641,006 bytes / state uploaded。远程 digest sha256:9a5bdf82581c3991dffcd33f33214589a0cd1020cc967401c10f9420fcf00d80，与本地实测文件及手机 base.apk 完全一致。
- 发布链接：https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.1.6-device-test。该本地签名 debug 包已实机验证，CI 签名可能不同，不能把两个文件混为同一安装产物。
- 远程 run 38393319956 已 completed / success，head_sha 精确对应源码提交。实际日志确认 13,511 项断言、原始 JPEG 全图与哈希、assembleStandardDebug / assembleCoverCompatDebug、lintStandardDebug / lintCoverCompatDebug，BUILD SUCCESSFUL in 38s。
- artifact xflip-camera-debug / 11810447546，11,869,859 bytes，digest sha256:34f0618937f2a9b5d63b36f99e51e07b7b0751325d40d74f368fdc7791e6ce6b；日志确认上传 4 个文件，即两个 APK 和各自 Lint 报告，保留至 2026-10-20T11:54:14Z。
- 上传必要成果和远程核验均完成。README 更新到当前下载与构建入口，证据目录增加发布收据和 CI 日志摘要；没有为文档结果重复构建 Android。
- 实测结束时停止本轮相机进程，CameraService 无活动客户端，真实状态仍 CLOSED / 无 override，清理本轮明确创建的诊断临时文件，保留新 APK 与用户手机上的成片。最终方向与两个 agent 分工沿用上一条及 Issue 1。

## 2026-10-13｜每日开发任务调整至凌晨 02:00

- 起始提交：32822d981154b5d47364f816352f069c891a4f65。
- 用户要求将原每日北京时间 00:00 的“开发 X Flip 外屏相机”任务调整为凌晨 02:00。
- 在同一原生任务上更新 Asia/Shanghai 的 DTSTART=20261014T020000 与 DAILY / BYHOUR=2 / BYMINUTE=0 / BYSECOND=0，保留原任务 ID、启用状态、关联对话及开发范围，没有新增重复任务。
- 周末、法定节假日及其他休息日仍执行。晨间任务的 09:00 工作日日程保持原值，仅同步其提示中对凌晨任务的时间引用。
- 下次计划时间为 2026-10-14 02:00（北京时间），按已保存日程推算；接口 next_run_time 为空，不声称已实际运行。
- 同步 README.md、AGENTS.md、docs/work-task.md 与 docs/morning-review-task.md。此轮仅调整任务与文档，不修改相机代码，不重复 Android 构建。
- 验证：读回原生任务，确认凌晨任务启用、Asia/Shanghai、每日 02:00 与新提示一致；晨间任务日程、启用状态和关联对话保持原值。

## 2026-10-13｜实时预览、录像与后处理开发检查点

- 起始 32822d9，开发期间合并 0fe8fc0 的 02:00 日程文档；保留其他 agent 成果与现有日程。
- 用户新增实时预览翻转、成片后处理、条件允许时连拍合成和录像；随后明确实时取景翻转为首要目标，成片反转不重要。用户要求先上传再继续，本条为当前源码检查点，不能当作实时预览已修复。
- 0.2.0-dev 实机新增 VIDEO_CAPTURE + FileProvider MP4：实际合盖、原生 ID 4、约 5 秒，3,414,491 bytes，返回本应用 Movies/XFlipCamera，MediaMetadataRetriever 成功解码视频画面。未公开真实场景 MP4。
- 原图保留的成片翻转已经实测：原始 3,219,422 bytes，另存翻转 JPEG 2,799,297 bytes；两者均 3072×4096，编辑版 EXIF 1。Pillow 完整解码与原图水平翻转比较，RGB RMSE 为 0.252 / 0.227 / 0.283，未翻转比较约 49.8 / 47.1 / 46.8。GPS 坐标和真实照片未上传。
- 显示捕获能力已验证：实际 UID 2000 捕获合盖原生相机 display 1 的 682×422 真实画面。Android 14 物理显示控制需通过系统服务 DisplayControl；类加载适配参考 scrcpy v4.1，保存 Apache-2.0 许可与说明，不打包系统 APK、反编译代码或 scrcpy server。
- 新增 PreviewMirrorWorker 和透明 SurfaceView 前景覆盖层，仅处理原生取景区域，排除自身图层避免反馈；快门与确认区域保持原生。此实现尚未产生已验收的实时翻转。
- 已启动手机现有 Shizuku 13.6 服务，实际 Shell UID；用户权限界面明确授权本测试相机。修复缺少 processNameSuffix 的启动错误后，用户服务仍超时。Shell 访问管理器的内容提供者返回 provider is null / Could not find provider，虽系统报告提供者已发布；管理器前台初始化未解决。
- 0.2.2-dev 增加自有 Binder 广播连接作为该 ROM 的回传替代通道：接收器由 DUMP 权限及真实发送 UID 2000 / 0 双重保护，普通应用不能注入服务。此通道待实机验证；不将源码、权限或显示截图证明当作实时翻转成功。
- 已通过 0.2.0 / 0.2.1 的 APK 构建与 Lint。0.2.2 assemble 完成，Lint 检出 Android 14 广播身份 API 与 minSdk 33 的边界；已增加运行时 API 34 检查，正在重跑构建和 Lint。所有变化均是待继续验证的检查点。
- 新增明确的“外屏系统相机”入口；录制与照片处理使用系统回传，仍依赖本机外屏助手的普通 Activity 启动能力。没有改全局 DPI，不能宣称独立入口已完全验收。
- 多帧：已通过 Issue 1 给代码 agent 独立配准/降噪研究方向；真正一次连拍及高分辨率多张回传尚未验证，未加入伪装成连拍的循环拍摄或视频截图。
- 下一步唯一优先项：把直接 Shell Binder 通道在系统拍摄界面跑通，验证画面连续更新、文字左右正确、快门/确认可操作，并保存实测 APK 与无私人场景的证据。源码及当前事实先上传，继续从本检查点工作。

## 2026-10-14｜独立媒体发布失败处理与预览边界审查

- 起始提交：cbe80af67337d6bc553a8775f6315fe2a484368f；读取 latest main、README、AGENTS、近期开发日志、技术基线、两项任务文档、工作日缓存与 Issue 1 最新交接。连接器确认 Public 仓库及 pull/push 权限。
- 优先级：用户已将实时系统取景左右纠正列为首要目标，成片反转次要；有线负责人暂时接管预览链路及系统入口。本轮保留 SystemCaptureActivity、MainActivity、PreviewMirrorService/Worker、Receiver/Starter、Manifest、app/build.gradle 和工作流共 9 个文件的原 blob，未并发修改或重新调查改包名、ID 4、AGC 黑屏等已验证路线。
- 静态审查：广播发送端已启用 API 34 身份共享，接收端有 API 34 判断、DUMP 权限与真实 UID 0/2000、接口描述符检查；不因此认定 Binder 通道已实机跑通。SurfaceDestroyed 尚无显式 STOP，线程停止 join(1500) 超时后仍共享 running，页面读取中重建可能重复处理同一输出；这些发现及待验收条件写入 docs/code-review-2026-10-14.md，没有伪造相应设备复现。
- 确认并修复：PhotoPostProcessor 忽略清除 IS_PENDING 的 update 返回行数，0 行仍返回“保存成功”；其失败清理还可能以 delete 异常覆盖写入/EXIF/发布的原始错误。新增 PendingMediaWrite，在写入与资源关闭完成后要求恰好发布 1 行；失败仅尝试清理本次新输出，保留原始失败并附加独立清理异常，不删除源图和已保存媒体。
- 实际接入：PhotoPostProcessor 已使用上述核心；像素翻转、JPEG 质量、Exif 与原图保留策略沿用已有实现，没有增加成片算法、Google 专有代码或新的 Shell 接口。该核心可在实机负责人解除独占后供系统回传路径复用；当前未把 SystemCaptureActivity 的同类发布与进程恢复问题写成已修复。
- 本地验证：JDK 17，bash scripts/check_core.sh 开启 --release 17、-Xlint:all、-Werror，13,595 个断言通过，其中 84 个为新增媒体提供者故障注入断言，覆盖插入失败/空 URI、部分写入、0/2/-1 发布行数、IO/运行时发布失败、清理失败和相同异常重复抛出；成功保留原图与旧图，失败不发布半成品。
- java scripts/ParseJava.java 通过 18 个 Java 文件语法解析，仅为语法检查。Manifest/资源 XML 解析、9 个协作文件 blob 保持一致；提交前检查差异空白。
- 原始照片复核：此次连接器支持 base64 读取，恢复两张原始 JPEG 并核对 Git blob；独立 JVM ImageIO 完整解码 20,758,528 个像素，两个 SHA-256 与记录一致、RGB 全零。没有重新采集照片或把原始黑图复核当成新版本真机测试。
- 环境：当前检测到 Java 17，Gradle 与 ADB 不在 PATH；没有执行本地 Android 构建。Android 类型、双 flavor 构建与 Lint 已通过既有 CI 的实际结果核验。新源码尚未在手机安装/复测，不在没有授权设备的环境尝试 Shell、悬浮窗或系统权限操作。
- 待办：P0 由有线负责人验证动态预览持续更新、文字左右与快门/确认操作；接续逐次渲染 token、STOP/死亡与 Surface 清理。P1 解除协作独占后复用发布检查到系统回传，加入输出归属与重建读取单一执行者，再在设备上验证取消/读取失败/退出/恢复。P2 扩展与 Google 合法成像路径继续沿用既有待办，不把 vivo 成像称为 Google。
- 工作日表：复用已核查 2026 年与 2027 年下一次检查不早于 11-01 的缓存；本任务不按晨间休息日规则跳过。没有新年度或更正依据、没有修改已有日程。
- 模型/推理强度无法核实，剩余额度无法读取；本轮限制为上述独立修复、审查与相称验证，项目仍有可继续事项。
- 连接身份已通过 authenticated profile 核验为 SturgeonIsSleepy，仓库 main 推送权限已确认。

- 远程验证：源码提交 1603d6e48b924862f5e21c2a879497456ced5171；https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38446311535 已 completed / success。实际日志确认 13,595 个断言、原始 JPEG 全图/哈希、assembleStandardDebug、assembleCoverCompatDebug、lintStandardDebug、lintCoverCompatDebug，BUILD SUCCESSFUL in 41s。
- artifact：xflip-camera-debug / 11838447488，ZIP 11,958,242 bytes，digest sha256:5a026e47b999dd2dc8a0d307eed311cf831d5635e9697a74faf74d261f228cc5，上传 4 个文件（两个 APK 与两个 Lint 报告），保留至 2026-10-20T18:24:21Z。7 个新增/修改文件已核对远程 blob 与本地一致，9 个协作文件保持原值。CI 产物未在手机安装，不能混同于既有实测发布包；实际预览与此次 Provider 故障处理待复测。

## 2026-10-14｜晨间审查与独立检查入口恢复

- 起始提交：1ea6ef629b9ffedc95f49f5fadfac923503f35a8。已核验连接身份 SturgeonIsSleepy、Public 仓库及 pull/push 权限，读取最新 main、项目规则、技术基线、两项任务、近期日志与 Issue 1。北京时间 2026-10-14 属于已核查工作日；复用 2026 年官方缓存及 2027 年 next_check_not_before=2026-11-01，本轮无须查询公告或调整任何日程。
- 衔接审查：凌晨发布失败修复的 run 38446311535 已 completed / success，未出现新的失败检查。复用其媒体写入与预览审查结论；实时取景仍是用户首要目标，有线负责人对预览链路和系统入口的交接没有变化，不并发修改这部分代码。
- 确认问题：PhotoInspectionActivity 通过分享 A 启动后，再用 OpenDocument 选 B，没有保存最新选择；页面重建会按原始 Intent 重读 A，选择器单独启动时则丢失 B。此为静态状态流确认，没有真机复现声明。
- 修复与实际开发：新增并接入 PhotoInspectionState，Bundle 只保存当前 URI 字符串和小型文字报告；恢复时优先当前选择，待完成则重读该选择，已完成则直接恢复报告、避免重复解码。换图立即清空旧报告，旧/重复/销毁后的读取结果不能覆盖新选择。沿用只读 ContentResolver 与现有错误提示，不保存 Bitmap、不扩大文件权限、不修改原图。
- 本地验证：bash scripts/check_core.sh 通过 13,630 个断言，JDK 17 / --release 17 / -Xlint:all / -Werror；新增 35 个状态场景断言，覆盖分享 A → 选择 B → 完成/未完成重建、快速换图的旧回调、重复完成、退出、失败报告与无效状态。java -cp .build/core-checks com.sturgeon.xflipcamera.core.PhotoInspectionStateChecks 独立通过 35 项；java scripts/ParseJava.java 通过 20 个 Java 文件语法解析；git diff --check 通过。
- 验证边界：核心场景在 JVM 执行，不是 Android Activity/文件提供者集成测试。Gradle 与 ADB 不在本地 PATH，未执行本地 Android 构建或手机操作；Android 类型、双 flavor assemble/Lint 和原始 JPEG 证据检查将读回源码 CI。没有声称获得真实动态镜像、修复 CameraX 黑屏或接入 Google 专有算法。
- 待办更新：P0 动态预览、Binder/Surface/STOP 与快门确认验收按凌晨规划继续，解除条件是有线负责人完成连续画面与左右测试并交接。P1 本次独立入口代码修复及 JVM 检查完成，等待已授权设备执行 docs/code-review-2026-10-14.md 的四组恢复/换图/失权验收；系统回传发布、读取归属与重建风险仍按原规划，协作交接后接续。P2 厂商扩展和合法成像待办复用，不增加重复研究。
- 已完成的附加目标：恢复已完成的小型报告，不因页面重建重复读取和采样同一照片。读取中的页面仍可能重新解码，未承诺任务跨实例继续或临时权限跨进程保留。
- 模型/推理强度无法核实，剩余额度无法读取；本轮范围限定为上述独立修复和相称验证，无限额百分比假设。设备仅阻塞相应验收，项目未全部阻塞。
- 源码已同步：6e811e6ded0b4fd2bc832b0c22c0c2e0774c797f；7 个变更文件远程 blob 与本地一致，其余远程文件和权限位全部保留，9 个协作文件未修改。推送使用 expected main SHA 的更新，没有强制覆盖。
- 远程验证已读回：https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38493995473 completed / success，head 精确对应本轮源码；实际日志确认 13,630 个断言、2 张原始 JPEG 全图 20,758,528 个像素及哈希、assembleStandardDebug / assembleCoverCompatDebug、lintStandardDebug / lintCoverCompatDebug，BUILD SUCCESSFUL in 44s。原始照片仍为历史证据，不是新版本设备验证。
- artifact xflip-camera-debug / 11863057147，ZIP 11,959,725 bytes，digest sha256:d4795df7b6f0859c3d88d3b44942c59119da6b6283ef3a4eaee4e2160dac256c，保留至 2026-10-21T01:08:44Z。两个 CI APK 和各自 Lint 报告已上传；源码版本号沿用 0.2.2-dev / 10，未改变实机负责人协作中的版本设置，没有发布实测包或操作手机。README 已更新构建入口，恢复/失权实机验收及 P0/P1 待办继续保留。

## 2026-10-14｜拍照与录像实时预览纠正验收

- 起始 026f964，保留两个 agent 的 PendingMediaWrite 与 PhotoInspectionState 修复。用户指定先完成拍照/录像左右纠正，再视频防抖，最后影像算法。
- 实机已安装 0.2.2，直接 Shell 广播握手成功，日志实际 uid=2000。Shizuku 管理器提供者仍无法被外部 Shell 获取，没有继续把权限文本或待连接状态当成功。
- 修复只输出第一帧及透明度叠加：取消改用独立 generation，同一 Surface 只 START 一次、销毁显式 STOP；取景覆盖层完整显示。统一逻辑坐标捕获，按右侧快门实际颜色/边界判断预览及确认状态，去掉预测 DPI 坐标依赖。
- 原生拍照动态取景已实测：连续计数 6 → 631，日历 31 正常可读；按快门后确认页 waiting_preview_controls，覆盖层透明，确认成功返回并保存。
- 修复同一工作者从照片切换视频时重复加载系统显示库的问题，缓存 DisplayControl 类；异常报告展开实际原因。录像预览与录制均持续更新，录制时计数 359 → 364，方向正常。计时文字保留原生方向。
- 最终约 5 秒录像成功返回、解码显示并保存至 Movies/XFlipCamera，2,348,489 bytes；返回后 PreviewMirrorService 已停止。真实照片、视频、截图不公开上传。
- 客户端实测版本 0.2.3，Shell 工作者迭代至 0.2.6；完整单一版本 APK 的安装复验仍需完成，不以混合调试会话当最终发布包验收。
- 本地各次 coverCompat APK / Lint 构建通过；此前核心检查及新合并断言通过，最终双 flavor 全部检查将随提交前执行记录。新增 nohup 直接桥接启动脚本，其断线、重启恢复仍待实测。
- 限制：覆盖层占用原生取景点按对焦区域，快门/确认正常；4:3 照片及 16:9 视频布局验证，其他比例/方向/切屏待验收；计时区域有小块背景拼接。手机或应用进程重启后仍需桥接初始化，原外屏助手依赖保留。
- 文档：docs/live-preview-2026-10-14.md、scripts/start_preview_bridge.ps1。代码检查 agent 可复核 generation/Surface/停止/死亡边界；下一步先完成最终包复验和上传，再添加视频防抖，影像算法排在防抖后。没有宣称已新增防抖或 Google 算法。

## 2026-10-14｜完整实时翻转包外屏复验

- 起始 1113c84538af498234193b494ea813d7215a6bdc，远程 main 已核验一致；通过 Issue 1 更新两 agent 的顺序为视频防抖优先、影像算法随后，保留当前实机负责人协作文件。
- 完整 0.2.6-dev / 14 经外屏文件管理安装，无展开。base.apk 与本地 APK SHA256 均为 192a7140edf211f60160486a7cf5bcf5d1945350cc62850a760173e1cd91d3ae，15,739,910 bytes。
- scripts/start_preview_bridge.ps1 从实际安装包启动真实 UID 2000 工作者，nohup 启动命令结束后继续完成拍照和录像，进程仍运行。未实测 USB 物理断线、重启及进程丢失恢复。
- 照片预览 15 → 329 帧，文字与原生确认/返回页方向一致；确认页 waiting_preview_controls，保存返回后服务停止。随后同一工作者录制视频，实时计数 29，00:00:01 正常可读；停止、确认后保存 1,273,804 bytes MP4 并解码显示，服务停止。真实 CLOSED、无 override。
- 这是完整单一版本实测，取代上一条的混合版本边界。私有照片/视频/截图保留本地；公开只保存版本、状态与统计。两 flavor assemble/Lint 与 13,630 项核心断言在该源码下已完成，日志 live-final-build.log 显示 BUILD SUCCESSFUL in 22s，没有因本次文档更新重复构建。
- 待办：先上传已实测 APK，再实现保留原片的视频防抖和验证运动补偿，最后接影像算法。初始化/外屏助手依赖、取景点击对焦和计时背景拼接限制仍保留。
- 已发布 v0.2.6-preview-test，release 412641033 / asset 640176158；远程 state=uploaded、15,739,910 bytes、digest 与实机哈希一致。公开收据 docs/evidence/2026-10-14-live-preview/release-receipt.json，没有真实场景图像。发布指向源码 1113c84。

## 2026-10-14｜视频防抖开发与真实编码验收

- 起始 1ecf2b8646ecd15ec09db48521f337cea561a3e3。既有拍照/录像实时翻转已验收并发布，按用户顺序接视频防抖，影像算法随后通过 Issue 1 交接。
- 新增 VideoMotionPlan：灰度平移估计、亚像素拟合、按时间戳平滑与插值、固定放大和补偿边界。初版把亚像素邻近最小值误作竞争匹配，同一合成解码序列仅 8/39 可靠；修复后 39/39，同时保留低纹理、重复纹理与场景切换拒绝。
- 新增 VideoStabilizer：10 Hz 小帧运动分析、Media3 Transformer/effect 1.9.0 的逐帧 GPU 平移、H.264 输出、音频保留、缓存验证和 PendingMediaWrite 发布，原片保留，退出取消。原生录像后第三个按钮切换为“视频防抖”，成功另存后可播放；镜像偏好持久保存并默认开启。
- 0.2.8-dev / 16 完整包本地与实机 SHA256 均为 37090646c34bd3cde61f952f4f8a06dacf5ef32779fa67d9d448a29f53442e44，19,833,768 bytes；全程 CLOSED/display 1/无 override。同版 Shell 桥接启动，录像实时计数 27、计时正常，确认保存返回。
- 实机合成编码：41 个样本、40 个有效位移，输出 709,677 bytes；独立完整解码 120/120 帧、H.264/AAC、360×640，扣除放大后两轴抖动残留 14.4%/34.3%，无黑边。该数值只针对合成输入，不推广为真实手持效果。
- 原生素材按钮实测：1,961,072 bytes 原片保留，另存 873,704 bytes，源/输出显示 720×1280、约 3.43 秒、H.264/AAC，返回显示并能解码播放。发现播放 Surface 与底图同时显示；0.2.9-dev / 17 已修为居中播放、隐藏底图、完成后恢复预览，最终包正在复验。
- 双 flavor assemble/Lint、13,783 个核心断言及原始 JPEG 全图/哈希验证通过，BUILD SUCCESSFUL in 19s（stabilization-final-build.log）。0.2.9 的 UI 修复后再次执行相称双 flavor 检查，结果随最终包记录。
- Windows/ADB 二进制标准输入只传出 917 bytes，-T 仍相同；改为本应用 run-as 的 base64 分块并校验 SHA256 后得到完整测试输入。无前台 instrumentation 被 ROM 暂停，Shell 立即启动外屏自有 Activity 后处理约 11 秒完成；不修改用户电池或系统设置。
- 公开成果 docs/video-stabilization-2026-10-14.md、生成/实机/独立验证脚本与 docs/evidence/2026-10-14-video-stabilization；仅生成的合成 MP4、统计和哈希公开，真实素材及完整日志不上传。初始化脚本修复首次无桥接进程时的空输出 Trim 错误。
- 限制：录后平移防抖、0.3 至 60 秒 SDR、约 9.1% 可见边缘裁切与有损编码；没有实时 EIS、旋转/透视/滚动快门补偿或 Google 专有算法。实时翻转的初始化/助手依赖、点击对焦被占用和计时背景拼接仍保留。下一步先上传最终实测包，再交两 agent 做影像算法与状态/清理复核。

## 2026-10-14｜0.2.9 完整包、播放与远程 CI 复验

- 源码 f2e9a8ca47a692cb5d49065f48e567ab974e7335 已上传，并用 ls-remote 确认远程 main 一致。0.2.9-dev / 17 在外屏安装，本地与手机 APK SHA256 同为 594f41888966fb7b57f223aba3b8f898306f6837a6cd84e51c87227515441387，19,817,652 bytes。
- 从实际安装包重新启动同版 Shell 桥接；真实 CLOSED、display 1、无 override。照片实时预览计数 8，文字方向与确认页一致，确认正常返回保存；同一工作者录像实时计数 23、00:00:01 正常，停止确认返回后预览服务已停止。
- 最终原生录像防抖：1,671,675 bytes 原片保留，另存 764,620 bytes。独立 FFmpeg 完整解码源/输出均 82 帧、720×1280、约 2.74 秒、H.264/AAC；native-029-metadata.json 只公开格式、帧数、体积、哈希，不含真实场景和 GPS。播放仅一幅居中画面，完成后恢复预览图，替代上一条的播放待验收状态。
- 已上传的 scripts/test_stabilization_device.py 在最终包重跑成功，输出 709,677 bytes / SHA256 5c4abd2fb24bf676bb675ac4bc583c7e959eb6e37a152efb4a290c96ece780a1，与既有公开合成证据一致；120/120 帧、音频、无黑边和两轴残留指标全部再次通过。
- 最终本地双 flavor assemble/Lint、13,783 项核心断言及原始 JPEG 验证成功，BUILD SUCCESSFUL in 18s。对应远程 run 38564399301 已 completed/success，head_sha=f2e9a8c；实际日志确认相同检查，BUILD SUCCESSFUL in 1m 4s。
- CI artifact xflip-camera-debug / 11893304790，15,548,322 bytes，digest sha256:22ba87c6bbb826cff1fc1cc6d5b708670de4e54eaae90b18bdefffe0f719a352，保留至 2026-10-21T12:32:35Z。CI 包与本地实测包的 debug 签名可能不同，Release 使用本机实测 APK，不将两者混为同一文件。
- 源码、防抖生成/实机/独立验证脚本与非私人证据已可供两 agent 获取；实测 APK 已发布 v0.2.9-stabilization-test，release 412706644 / asset 640357907，19,817,652 bytes、state=uploaded；远程 digest 与手机/本地 SHA256 三者一致。收据 release-receipt.json 已保存。后续只改文档，不重复 Android 构建。
- 下一步：代码 agent 在有效 JPEG/MP4 回传基线上接合法公开的影像算法，保留原图并明确 RGB/JPEG 与 RAW/连拍的输入边界；检查 agent 审核防抖取消/发布/恢复与 Binder/Surface 边界，提交有内容的必要修复后交有线负责人复测。Google 专有 HDR+ 尚未接通，Media3 不作为 Google Camera 算法宣传。

## 2026-10-14｜预览卡顿特调与睡前收尾

- 起始 48830c67cc75415638d8119900b71c6745c7038d。用户补充 0.2.9 预览卡顿且偶尔翻回，防抖效果偏弱；希望打开即拍照、右侧切模式、左侧设置及原生/自制水印，并评估成熟防抖。随后明确 10 分钟内收尾、准备睡觉，本轮不再扩展新功能。
- 旧版实际测得 10.15 fps；GPU 工作者实验客户端 0.2.9 + 工作者 0.3.0 测得 29.24 fps，8.07 秒 18→254 帧，漏检 3 次后仍 phase=1 且方向正向，空捕获 0。证据 docs/evidence/2026-10-14-preview-performance，不上传场景截图。
- 移除每帧整屏 CPU 拷贝，直接硬件位图渲染；只读小块按钮区域判断，完整周期调度约 30 fps。复用参数和反射方法，空帧保留；漏检不清空翻转，确认/设置显式退出，旧线程清理不影响新会话。前台相机仍每帧核验，未改系统全局设置。
- 0.3.0-dev / 18 双 flavor assemble/Lint、13,817 个核心断言、原始 JPEG 全图/哈希通过，BUILD SUCCESSFUL in 37s。初始化脚本增加 Status: ok 检查，102 拒绝不再继续。实验中首次启动失败由未唤醒外屏造成，唤醒并打开既有助手后恢复；失败测量未计入性能结果。
- 最终 APK SHA256 9a7a8825c99985415fc541b25cd6847113f1209a788a92d410e2bc592d45a1b1，19,817,652 bytes。准备安装时 ADB 无设备，最终完整包未装，不能标注完整实机验收。按用户收尾要求不再请求接线，作为候选包上传。
- 原生外屏设置已经实际查看，只提供定时、画幅、触屏/手掌/语音快门，未找到蔡司水印或防抖入口；深层/内屏未验收，不声称设备不支持。OpenCV Android 4.12.0 官方依赖获取成功，但未完成算法接入，移除未使用依赖及 ABI 限制，本轮没有防抖升级或水印/界面完成声明。
- 后续顺序：先安装最终 GPU 包验收拍照/录像/设置及长时间稳定，再完成直接拍照入口、右侧模式、左侧设置与水印；随后成熟光流/稳健估计、旋转防抖和手持对照。已通过 Issue 1 给两 agent 更新分工；用户无须睡前继续操作。
- 报告 docs/preview-performance-2026-10-14.md。源码、候选 APK 与统计上传后核验远程；已有 0.2.9 实测 Release 保留。最终发布收据与交接见本轮后续记录。
- 远程源码 5ff5040e305c9a9628cda0593f21aa29a6d84077 已核验 main 一致；对应 CI run 38583427109 completed/success，artifact 11903658385 / xflip-camera-debug / 15,550,646 bytes / sha256:311666f7cc65cada1591aa4def8e2dd4a823f32586dec725fc8f1b6195f5cb87，CI APK 和 Lint 已上传。
- 本地签名 APK 直传挂起；终止本任务上传进程后，curl 报证书吊销服务离线，保留证书链验证的限时重试仍发送 0 bytes 并超时。未上传本地签名资产，19,817,652 bytes APK 保留本地；release 412835355 已转 draft=true，避免空下载入口。candidate-publication.json 记录真实状态，不把 CI 包称作实测签名包，不为此次文档变更再构建。

## 2026-10-15｜独立外屏原生控件

- 起始提交：8c29dc5c0c947800488fbadda5bcce30312c5992。已确认 GitHub 连接为 SturgeonIsSleepy、Public / main 及 pull/push 权限；读取 README、AGENTS、近期日志、技术基线、两项任务文档、工作日缓存、最新 CI 与 Issue 1 交接。从 GitHub 固定版本恢复构建所需文件，旧临时文件仅在 Git blob 与远程相同时复用。
- 衔接事实：0.2.9 实测、0.3.0 GPU 工作者性能实验和完整候选包未安装的边界按既有报告保留；run 38583427109 状态为 success，不重新调查已完成的显示捕获/ID 4/Shizuku/算法路线。手机已断开，不请求用户睡前接线。本轮选择 Issue 1 交给代码 agent 的 CoverControls.java；WatermarkProcessor 继续留给独立检查任务，未重复实现。
- 实现：CoverControls(Context, Callbacks)，onMode(boolean video) / onSettings() 与 attach/detach/setMode/setEnabled；设置、拍照、录像各有独立原生 Button / TYPE_APPLICATION_OVERLAY 小窗口。当前模式点击不重复回调，新模式须由宿主确认后 setMode；禁用、主线程约束、部分加窗失败清理、逆序移除及配置重排均有明确处理。组件不自动申请权限、发送触摸或切换外部相机。
- 布局：实际接入 CoverControlLayout，运行时 WindowMetrics、Insets 和密度决定左右位置与 64×48 dp 按钮；三矩形不交叠且中间至少 48 dp 贯穿区域留空，空间不足拒绝显示。NOT_TOUCH_MODAL / NOT_FOCUSABLE 仅用于三个按钮窗口，不增加透明整屏容器；没有声称修复既有预览 SurfaceView 的点击对焦限制。
- 范围边界：遵循独立接口交接，未改 PreviewMirrorWorker、PreviewMirrorService、SystemCaptureActivity、MainActivity、VideoStabilizer、Manifest、app/build.gradle 与工作流共 8 个原文件。组件已编入源码但尚未接入拍摄流程，新直接拍照入口/实际右侧模式/原生设置尚未完成；下一步由接续整合将回调与原有媒体结果归属、安全停止及确认阶段关联，不能单纯连发新的外部拍摄 Intent。
- 本地真实检查：旧基线核心先通过 13,817 项；新增后 bash scripts/check_core.sh 在 JDK 17 / --release 17 / -Xlint:all / -Werror 下通过 15,375 个断言，其中 1,558 个新增布局断言覆盖 75 组方向/密度/Insets、最小边界、大尺寸/非法值及中央快门空区；独立 CoverControlLayoutChecks 同样通过。java scripts/ParseJava.java 通过 28 个 Java 文件语法解析，git diff --check 通过。Android WindowManager 添加/清理故障与真实触摸分发没有在 JVM 上执行，不把几何检查称为设备验收。
- 本地环境未发现 PATH 中的 Gradle / ADB；没有本地 Android 构建或手机操作。双 flavor assemble/Lint 和原始 JPEG 检查随源码 CI 执行，结果将读回核验；版本沿用候选 0.3.0-dev / 18，以源码 SHA 区分，不生成未经实测的 Release。
- 文档 docs/cover-controls.md 保存接口、接入责任、验证命令与四组实机验收。P0 仍是先安装完整 GPU 包复验拍照/录像/设置与长期稳定；P1 本轮独立控件完成，下一项整合入口/模式/设置并验证窗口残留和快门；水印由另一项既有交接继续。P2 成熟防抖与合法成像研究沿用原计划，不引入 Google 专有代码或声称 Media3 为 GCam。
- 工作日维护复用已核查 2026 年及 2027 年 next_check_not_before=2026-11-01 缓存，未到新查询窗口，无正式更正，不修改日程；此开发任务不受晨间休息日跳过规则影响。
- 模型/推理强度无法核实，剩余额度无法读取；本轮限定一个可验证组件。仅相关实机验收暂缺设备，项目仍有独立工作可继续。
- 源码已推送 f275fdc36d042713bc92e86c6f41e6778c784f66，使用 expected main SHA 且 force=false；读回 main 一致，7 个变更 blob 与本地相同，其余 97 个远程文件和权限位均保留。8 个原拍摄/预览/构建文件没有修改。
- 远程 https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38613279289 已 completed / success，head 精确对应本轮源码；实际日志确认 15,375 个断言、原始 JPEG 2 张 / 20,758,528 个完整像素及哈希、两个 flavor assemble 与各自 Lint，通过 BUILD SUCCESSFUL in 47s。上传 artifact xflip-camera-debug / 11917708331，15,563,604 bytes，digest sha256:e12d322ae01206514ef011a9ab82adcc36626a64b3b5dc9ccf2ed4249a22d8f1，4 个文件为两个 APK 和两份 Lint，保留至 2026-10-21T18:14:26Z。README 与组件文档更新为实际验证结果；CI 包未安装，不与既有实测包混同。下一步及设备阻塞沿用上述 P0/P1，未因本次组件完成停用项目任务。

## 2026-10-15｜晨间审查与原图保留的文字水印

- 起始提交：b1d2689224ab87d37e279ed1e4bfe9ddf5b5ec18。连接身份 SturgeonIsSleepy、Public / main 与 pull/push 已确认；读取最新 README、AGENTS、日志/待办、技术基线、两项任务、工作日缓存和 Issue 1。Asia/Shanghai 的 2026-10-15 是已核查工作日；次年检查不早于 11-01，不重复查询、不调整日程。
- 审查衔接：凌晨 CoverControls 的 run 38613279289 为 success，本地旧核心 15,375 项也通过；复用已完成结论，没有新的失败检查。不把未接入组件的几何保证当真实触摸验收。已知系统回传恢复/发布、视频退出边界继续原计划，不做无依据重构。
- 实际开发：按 Issue 1 检查任务接口新增 WatermarkProcessor.mark(ContentResolver, Uri, String)。JPEG 原图只读，20MP 上限且双边至少 128，软件解码归一 EXIF；普通文字只绘制于另存副本底部，sRGB / JPEG 95，不改变显示尺寸，无商标图案、不称原生蔡司。拍摄 EXIF/GPS 白名单保留、Orientation=1、Software 标记编辑副本；原图不写入或清理。
- 发布/回滚复用 PendingMediaWrite：资源关闭及元数据完成后恰好发布一行，失败不报成功，清理拒绝不覆盖原错误。非法文字/尺寸、解码/输入失权在输出创建前拒绝；未接入拍摄/设置页面，未增加运行时库、Manifest 权限或 Google 专有代码。
- 验证增量：Robolectric 4.17 / JUnit 4.13.2 仅 testImplementation，API 34 NATIVE graphics 实际图形/JPEG 回归定义 8 个方法，覆盖 EXIF 1..8 非对称角点/宽高、文字像素差异、真实拍摄字段与合成 GPS、原图字节、非法输入、0 行发布、清理异常、失权、损坏和写入失败。MediaStore 是故障可控假提供者，不是手机测试。CI 增加双 flavor unit test 与 JUnit XML；不能凭测试定义声称通过，须读回结果。
- 本地 JDK 17 核心检查仍 15,375 项；ParseJava 增加 app/src/test 语法覆盖，类型和 Android/JUnit 依赖由实际构建确认。Gradle/ADB 不在 PATH，无本地 APK/真机操作。真实 MediaStore、中文字体、HDR/广色域与生命周期取消待设备/整合验证。
- P0 继续复验完整 GPU 候选及长期实时方向；P1 两组件按原交接整合入口、左右控件/设置与文字另存，验证快门、原图、确认/退出和错误归属；P2 厂商水印、成熟防抖、合法成像沿用已有路线。docs/text-watermark.md 保存内存、元数据、底部覆盖/有损代价及复验命令，不反复生成同规划。
- 模型/推理强度无法核实，剩余额度无法读取；本轮仅上述独立目标，设备仅阻塞相关验收，不停用既有项目任务。
- 首次源码 4fa509a0c315cbdb4327e75903153d636bdea489 已推送；run 38664028710 失败，standard 的 8 项测试中 4 项在输出 EXIF 构造失败，未执行后续 coverCompat/完整构建，未宣称通过。已下载并读取 artifact 11940406555 的真实 JUnit XML：NPE 来自 ExifInterface(FileDescriptor) 经 Os.dup 得到 null。对照 AndroidX 和 Robolectric 4.17 源码，测试 Linux shadow 缺少 dup 实现；仅增加测试适配，把已登记 FD 映射为同一临时文件，调用真实 ExifInterface(File)，保留实际元数据字节验证，不改生产 FD/保存代码。真实 dup/lseek/所有权仍待手机；另开启完整异常日志，适配后重新运行检查。
- 最终验证源码 f1a98b3341ea8ba48ecfd9e5fed93de827bae282（测试适配提交），https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38664547403 已 completed / success，head 精确匹配。standard / coverCompat 各 8 项 native graphics 回归全部通过，failures/errors/skipped 均 0；原图字节、EXIF 1..8 角点/归一尺寸、真实文字像素、元数据与发布/清理故障均实际检查。FD 系统调用不在此验证范围，不能替代手机。
- 日志确认核心 15,375 项、2 张原始 JPEG 的 20,758,528 个完整像素及哈希、双 APK assemble / 各自 Lint，通过 BUILD SUCCESSFUL in 1m 5s；本地 30 文件语法、差异空白及工作流 YAML/测试命令检查通过。watermark-test-results / 11939674124 含两份 JUnit XML，1,725 bytes，已下载并核对 ZIP SHA256 e8766d1b7efa2e36c9948dfbd16d0b59f81c36edc79910c6bfdbb2fa1ff3b513；xflip-camera-debug / 11940187088，15,574,697 bytes，digest sha256:9d31f0a53aa15f4ceff7b8597ca7cf8e760877ee0eb27c91789e06c83e88b04b。两 artifact 保留至 2026-10-22T01:15:58Z / 01:15:59Z，CI 包未安装，历史 JPEG 不是新设备验收。
- 两次推送均使用 expected main SHA / force=false，8 个新增或修改文件远程 blob 已核对，其余 99 个原远程文件及权限位保持不变。README 与组件说明补齐真实结果；仍未接入水印按钮或增加新相机算法，P0/P1 待办及设备/FD 边界继续按上述计划。


## 2026-10-15｜有线负责人整合与完整包实测

- 起始 dfd7d89，复用两 agent 已交付的 CoverControls/WatermarkProcessor，按 GitHub Issue 1 接口整合 0.3.1-dev / 19；未重写独立组件。只通过仓库通信，未直接操控其他 agent。
- 冷启动在桥接就绪后直接拍照；右侧模式关闭自己启动的请求码 4102 子相机，等待旧服务销毁再启动下一模式。设置原生外屏对话框提供文字水印/原有实验平移防抖，设置时暂停渲染，录制/确认/原生设置移除三按钮窗口。用红色圆/方填充率区分待机/录制，避免缩小采样误识别隐藏控件。
- 照片确认后先保存原图，再调用既有水印处理器另存；水印失败保留原图，取消恢复最近保存媒体。保留结果归属检查、处理时禁止播放、静态宿主销毁释放；Debug 视频测试用 manual_entry 避免自动开相机。
- 两 flavor assemble/Lint、核心 15,377 断言、各 8 个水印 native graphics 测试及原始 JPEG 证据通过，BUILD SUCCESSFUL in 1m 9s。完整 coverCompat 19,982,560 bytes，SHA256 184c3c582f261abf2900cc54b4d12ca1e1daf5112f20e56997cdf3a86a8714de；真实安装 base.apk 哈希一致，客户端和 Shell 工作者同版。
- 真实 CLOSED / display 1，冷启动、原生设置、水印实际 FD/保存和六次双向模式切换通过。文字场景照片/录像实时方向正确，视频预览 1,494 帧平均 28.6 fps、0 capture_misses。原图与水印均 3072×4096，水印可见、原图仍在；录像确认后完整独立解码 48 帧、720×1280、24 fps、2.01 秒、H.264/AAC。真实图片/视频只保留本地，公开维度、哈希及统计在 docs/evidence/integration-2026-10-15.json。
- 900ms 自动化连点曾抢在新按钮显示前触发原生取消；按实际窗口就绪复验连续六次正常，记录为短暂加载边界，不凭相位就声称窗口已可点击。一次录制确认因长时间分析触发原生超时未保存，随后短批次重新录制确认保存并独立解码成功。
- 系统相机闲置退出、电脑初始化/外屏助手依赖、取景点击对焦、计时背景拼接与部分齿轮露出仍保留；未做全方向/开合屏/长录像验收。GPS 实拍只有空 IFD，不把合成 GPS 测试说成真实坐标验收。详见 docs/integration-2026-10-15.md。
- 防抖配置仍是原有平移，没有冒称成熟算法升级。下一步代码 agent 独立实现 AffineStabilizationPlan 与旋转/平移合成测试，负责人后续接成熟 LK/RANSAC；检查 agent 审查结果取消/重建/窗口清理。影像算法在其后，系统蔡司和 GCam 均未接通。不更改日程或全机设置。

- 源码 059f28e7eace0fb07a33e6e868fc279e4aa3c42c 已推送，远程 main 读回一致；对应 CI 38720404954 completed/success，下载 artifact 11965910260 的两套 JUnit XML 各 8/0/0/0，ZIP digest 一致；CI APK/Lint artifact 11966055164，15,579,640 bytes。
- 实测 APK 已发布 https://github.com/SturgeonIsSleepy/vivo-xflip-camera/releases/tag/v0.3.1-cover-controls-test ，Release 413678632 / asset 643497848，state=uploaded / 19,982,560 bytes，digest 与手机/本地三者一致；tag 核对精确指向源码 059f28e。补充 24.282 秒照片预览区间 28.95 fps、零 capture_misses、phase 1，实际三个原生控件窗口；返回后服务退出。Issue 1 已同步整合源码、验收边界和两个 agent 下一项。
- 远程机器收据在 db21f8274d28339dce3e2c918e7169285a625a1d 已保存；随后文档更新脚本因 Windows 默认 GBK 读取 UTF-8 文档失败，仅前置 JSON 写入完成。本提交明确指定 UTF-8 补齐 README 下载入口、实机报告和统一日志；APK 与生产源码未改变，不重复构建或安装。最终以本次完整文档为准。


## 2026-10-15｜修复用户实际重开后失联

- 起始 fc02852，用户明确反馈原始相机、翻转失败、功能键缺失，推翻此前同进程演示可代表日常可用的判断。实见 app PID 重建、Shell PID 仍在、服务 not_connected 且没有控件窗口。修复工作由唯一有线负责人承担，Issue 1 已同步根因和文件独占，继续仅 GitHub 协作。
- Receiver 增加应用私有外部目录短请求，Shell 固定 DUMP 接收器重新共享真实 Binder，保留 UID 与描述符验证；授权 Shizuku 支路也等待真实服务连接。应用没拿到工作者不再进入原始相机，前台重开自动恢复，后台不弹起。
- 首轮 force-stop 暴露原生子相机残留旧任务栈，am start 只带出原生页而不启动应用；入口改 singleTask / onNewIntent，正常图标只保留外屏拍照，旧 Main 外屏入口转发。第二轮故障注入发现 HWUI native abort 与旧缓冲队列：增加独立 Shell 监护，worker 替换时重建 Surface，避免连续崩溃；这属于恢复机制，未声称 native 风险消失。
- 最终 0.3.2-dev / 21 双 flavor assemble/Lint、15,377 核心断言、各 8 项水印 native graphics 回归及 JPEG 证据通过，BUILD SUCCESSFUL in 39s。19,850,408 bytes，SHA256 ed7eddc8b67c7ae3fdf4e2c4ca7fef0946fd1f7ace10769ca5677cf59fb02925，与手机已安装包一致，最终同版脚本已初始化。
- 最终包连续三次强制停止后正常入口重开，三个 app PID 均恢复 phase=1 和 3 个独立功能键，监护会话不变且没有重跑初始化；含 ADB/dumpsys 的 9.87/7.22/10.64 秒只作为诊断耗时。主动中断 worker 后同一相机会话约 7.96 秒恢复、左设置可打开；退出预览服务停止。旧中间包失败与最终包结果分开记录，截图/场景均不上传。
- 报告 docs/reconnect-2026-10-15.md，安全统计 docs/evidence/reconnect-2026-10-15.json；新增 scripts/test_preview_reconnect.ps1 保存可复现的有线进程死亡回归。没有改全机 DPI/性能、日程或防抖/成像算法。
- 下一步检查 agent 审查新连接、singleTask、Surface 更换与清理；代码 agent 的独立成熟防抖核心保留原任务。手机重启/授权撤销/监护死亡仍需初始化，实际拔线与重启自动授权未验收。后续考虑将绘制移入应用进程消除远端 GPU native 退出风险。

- 源码 3ed7e6ea5005d911ce7f44cffc3639c1f01c5b2f 与 main 已核对，CI 38724970989 success / head 一致；下载核对两套 JUnit XML 各 8 项全通过，artifact 11967746158 ZIP hash 与远程相同，CI APK/Lint artifact 11968430618 为 15,583,784 bytes。
- Release v0.3.2-reconnect-fix / 413709949 已公开预发布，APK asset 643594007 / 19,850,408 bytes / digest 与手机和本地一致；初始化脚本 asset 643600283 / 2269 bytes，SHA256 8f64831415d4ab46fa8502d24b7024017d268eba697c8436d675a9d23c414bc2 已核对。tag 精确指向源码 3ed7e6e，0.3.1 Release 已加已知重开失联及替代版本警示。
- 额外实机从旧 MainActivity 外屏入口强制停止后重开，实际进入正确取景并切录像；第一次过早点击原生录制未开始，窗口就绪后再点正常，仍记录加载边界。最终视频 9,382,885 bytes 独立解码 551 帧、720×1280、约 22.96 秒、H.264/AAC；确认后服务停止，不将前版 29fps 当成本轮录像帧率。私有视频仍仅本地，统计更新到同一 JSON。


## 2026-10-15｜照片三帧对齐降噪与全尺寸实拍

- 起始 24628aeee334708490f7ae511d134dad3ad461ec。用户要求照片多帧降噪和清晰度增强；先查原生输入边界并通过 Issue 1 分配合成对照/审查，保持仅 GitHub 沟通。实机 IMAGE_CAPTURE 长按明确提示不支持连拍，因此实现连续三次真实拍摄确认，再自动融合，不拿截图/重复图/视频帧冒充全尺寸连拍。
- 新增 MultiFrameAccumulator 与 PhotoBurstProcessor：较清晰参考、OpenCV 4.12 ORB/Hamming/RANSAC/ECC、运动/异常帧拒绝、分块稳健平均及受限亮度锐化；13MP 内 SDR JPEG 归一 EXIF、尺寸不变、JPEG97 另存、保留真实元数据与融合标记，复用 PendingMediaWrite。分块与单帧轮换控制内存；仅打包实机 ARM64 库，实际依赖许可随 APK assets/licenses 保留。
- 原生左设置增加“连续三张合成：每张需确认”，确认1/2自动进入下一张，右按钮显示2/3、3/3。取消、关闭设置或切录像清空本组，保存的原图保留；销毁取消处理。第三张后对融合结果应用已有文字水印，水印保留 ImageDescription，并增回归断言。发布完成后的读取/后处理错误不再清理已保存原图。
- 核心 27,677 断言通过，独立噪声 MSE 比值0.3802、运动块参考保护和受限锐化检查通过；双flavor assemble/Lint、各8项水印native graphics及JPEG证据通过，最终BUILD SUCCESSFUL in 1m19s。0.4.0-dev/24，45,245,244bytes，SHA256 0165ad4b32d0b8a4dee322285997ddb57951d194ed7ec1f1768fc238c9e64328，与手机base.apk一致。
- 真实ARM64手机同生产路径执行good/moving/rotated/unrelated/duplicate/cancel六组，输入哈希逐字节保持；前三组3/3融合，后三组正确拒绝。加入同Android sRGB/JPEG97单帧编码基准，最终平坦MSE比0.3572/0.3515/0.3843、边缘0.9918/0.9940/0.9936，运动区MSE比0.6540；合成处理约1.2–1.4s。初期19%比值包含JPEG重编码影响，以最终匹配基准为准，不外推真实通用质量收益。
- 真正合盖3张3072×4096回传后融合3/3成功，原图3,152,627/3,164,314/3,157,412bytes均保留；合成2,920,729bytes、水印副本2,345,862bytes，均原尺寸与Orientation1、融合描述仍在。第三张缓存/图库原图哈希相同，真实场景只保留本地，不报告无真值PSNR。右侧2/3、3/3和最终状态截图已私下核对，回传后服务停止。
- 测试前处理了vivo安装器复用旧完成页：实际versionCode/hash暴露仍是22，停止当前安装器会话并用唯一v24路径后验证真正安装。夹具先放外部目录被NIO拒绝，改为同UID内部文件并逐文件核对哈希；ASCII base64流传输加哈希校验和旧分块回退有效。失败不冒充算法验收。
- 新增scripts/generate_photo_burst_fixture.py、scripts/test_photo_burst_device.py及debug-only PhotoBurstDeviceTest，公共仅合成图对照与统计。报告docs/multiframe-2026-10-15.md，统一JSON/对照图在docs/evidence。原有手机重启初始化、原生确认/闲置退出、点击对焦和计时拼接限制保留；没有Google专有算法、RAW HDR+、神经生成或超分辨率，未修改日程和全机设置。
- 代码agent后续优先核查已生成的合成夹具/独立质量评价，避免重复实现；检查agent审查会话取消/原图发布/元数据、对齐拒绝、内存和发布回滚。下一步减少拍摄确认交互，并扩展低照/局部运动实拍质量对照，成熟防抖原任务继续待办。


## 2026-10-15｜自动色阶、细节增强与JPEG HDR色调

- 起始b77245a。用户先要直方图两端处理，选择文字/纹理优先，另希望HDR。新增PhotoToneCurve/PhotoEnhancer，去空白端的自动色阶、方差/噪声区分平坦与低对比结构、受限CLAHE细节和双边log基础层JPEG HDR色调。RGB同增益并限制通道余量，避免饱和色变白；不声称恢复已过曝信息或真正包围曝光HDR。Issue1已分配输入HDR研究和颜色/光晕/取消审查，继续仅GitHub沟通。
- 单张与多帧新工作图在首次输出编码前处理，共用JpegPhotoWriter保留元数据与PendingMediaWrite发布/回滚。原图保留，水印仍在最后，六项原生设置可滚动。自动色阶/细节默认开、HDR色调默认关，不改全机设置或日程。
- 最终0.4.1-dev/27双构建/Lint、30437核心断言、两套各8项水印回归与JPEG证据通过，41秒成功。APK45,261,656bytes，SHA25677a20167df57a47726902baf780bfc375ed08df5d23ba1990dd183e49c097cec，与手机已安装软件一致。
- 八组真实ARM64同生产路径合成图检查全过：色阶40–215到0–255，低对比文字90–149到约0–254，独立细节边缘比1.0994，HDR暗部29.24到45.08/亮部224.01到197.15/亮部纹理比0.8796，平坦噪声MSE比1.0255且不扩展，颜色/EXIF6/取消及原输入不变均通过。三帧同编码回归MSE0.3572/边缘0.9918保持；仅特定合成图结论，不外推真实通用收益。
- 合盖实际通过单张和三帧加全部新处理的保存、原图保留、水印、设置和服务退出检查。实拍私有证据只留本地；公开仅功能通过结论，不上传私人照片的哈希、文件名、EXIF等派生标识。机器收据同步去掉上版real_photos数组，保留合成测试与软件APK校验。未重写历史。
- 首次包含实拍派生字段的上传命令在执行前被自动审批拒绝，原因是未获明确授权公开私人照片的哈希和元数据；改为移除这些字段的安全替代，未绕过审批。报告docs/photo-tone-2026-10-15.md和公开合成对照/JSON可复现，debug夹具及设备测试脚本一并交付。
- 下一步代码agent研究真正多曝光输入和低内存HDR，优化全尺寸处理耗时并保留曲线一致性；检查agent复核颜色、直方图尾部、光晕、共享输出与取消。已有初始化、系统每张确认等限制仍在。
- 收尾：源码605014ac7a7ca111ce66be0956e4ce41d903b15d已推送，CI38744983176成功；APK/Lint artifact11978385837和测试artifact11978355875已上传，后者下载校验摘要且各flavor JUnit为8/0/0/0。Release413861188已公开，tag v0.4.1-photo-tone-test核对指向该源码。手机本地签名实测包已安装，但45MB APK直传超时，17MB无损ZIP及小分卷亦连接重置；停止继续消耗用户额度重试，Release清除失败附件并明确提供同源码CI下载，不能称CI签名与手机文件相同。私有实拍仍不上传。本次仅补交付文档/合成对照比例，无运行时代码改动，不重复构建实测。

## 2026-10-16｜独立逐行色调核心与 13MP 对照

- 起始提交：44c200644cb6ab1fc2abecb92be3fc4d8d34cde2。GitHub 身份 SturgeonIsSleepy、Public/main、pull/push 权限已确认；固定该远程版本读取 README、AGENTS、近期日志/待办、技术基线、两项任务、工作日缓存、最新成功 CI 及 Issue 1。复用 0.4.1 已接入/实测的控件、水印、三张融合和 JPEG 色调结论，不重复实现。当前是独立环境，没有手机；唯一有线负责人继续保留实机控制。
- 按最新交接先优化全尺寸处理且保留曲线语义。已在 Issue 1 评论 6198997547 说明独立接口、重复逐像素 HDR log/羽化计算与验证方案；依照“集成类先交接、不并发改”，不改 PhotoEnhancer、PhotoToneCurve、JpegPhotoWriter、拍摄/预览/版本/依赖及日程。
- 实际新增 PhotoToneRowProcessor：每图有限 256 级缓存，逐行原位映射，复用现有 RGB 增益与细节公式，不改指数、舍入、饱和色保护及光晕限幅。输入长度在首个写入前验证；除 colors 外行缓冲只读，取消/进度/首尾行夹紧继续由宿主负责。尚未接入实际 PhotoEnhancer，不宣称手机处理已加速；现有 Bitmap/OpenCV 全图峰值分配保持不变。
- 实际验证：bash scripts/check_core.sh 在 Java 17.0.20 / --release 17 / -Xlint:all / -Werror 下通过 942,626 项断言，新增 912,189 项；固定旧循环/公式作为独立预期，覆盖全亮度、HDR阈值/噪声、饱和色/灰/alpha、邻行/窄行/非法缓冲和只读输入。java scripts/ParseJava.java 通过 41 文件语法检查，git diff --check 通过。Gradle/ADB 不在 PATH，没有本地 Android 构建或本轮手机测试；Android 类型/双flavor测试/构建/Lint由源码提交后的真实 CI 结果确认。
- 另执行 bash scripts/benchmark_photo_tone.sh 5：3072×4096、每模式12,582,912个合成流像素，计时外逐像素差异均0。四对短预热、五对交替顺序，主机 amd64 / Java17 / 128MiB：HDR+细节循环中位数867.605→604.612ms（1.4350倍）；非HDR491.496→483.627ms（1.0163倍，波动内，不声称明确收益）。预热后新循环线程分配0bytes；不包括初始化/缓冲/OpenCV/编解码/MediaStore，不代表手机峰值或端到端。完整原始数据仅合成输入，见 docs/evidence/tone-row-host-2026-10-16.json，无私人照片及其标识。
- 文档 docs/photo-tone-row-performance.md 保存调用接口、实际范围、接入步骤与复现命令。待办：[x] 独立精确逐行核心/回归/主机对照；[ ] 负责人协调后最小接入，使用真实 OpenCV 路径核对像素、单张/三帧及取消/EXIF；[ ] 同机13MP端到端计时与Java/native峰值，评估行/块内存方案，未经此验证不把本轮叫完整性能优化。直方图噪声/尾部、光晕、共享输出回滚留给既定审查，真正不同曝光输入与成熟防抖沿用原待办。
- 阻塞及解除：接入由仓库文件协调解除；X Flip 性能/真机图像验收需有线负责人设备，不要求本轮用户操作，其他独立验证可继续。模型/推理强度无法核实，剩余额度无法读取，没有虚构额度或改变模型的声明。复用2026已核查工作日表与2027 next_check_not_before=2026-11-01，本轮不重复查年表、不更改两项任务。
- 远程核验：源码 fee44f8b02ac7581aae9a9e660ca0d0eadb94c7f 已使用 expected main SHA / force=false 推送并读回一致，10 个新增/修改文件 blob 和权限位匹配，其他150个原远程文件及权限位保留。PhotoEnhancer/PhotoToneCurve/JpegPhotoWriter/拍摄/版本/工作流的原 blob 均未改。
- CI https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38784336760 已 completed/success，head 精确对应本轮源码。真实日志确认942,626核心断言、2张原始JPEG的20,758,528完整像素及哈希、两个flavor各8项水印回归、双APK assemble与Lint，BUILD SUCCESSFUL in 1m 3s。已下载测试artifact11997850600 / 1,721bytes，ZIP SHA256 2394e748fd956880aaabf70214c629366e577acfa4a7211f60248189f13523b6匹配，两份JUnit XML各8项且失败/错误/跳过均0。APK/Lint artifact11997715799 / 36,710,191bytes / digest eb7dfe3a415763a86961eb3fa892982069258f04a462308dc6eda006df6bb2d2，保留至2026-10-22T18:18:39Z，测试ZIP至18:18:37Z。未安装CI包、不发新实测Release，主机性能与手机及未接入状态边界不变。收尾仅补验证文档，无源码变动，不重复构建。

## 2026-10-16｜晨间保存事务审查与 Error 回滚

- 起始提交：d5be7e5e14a328beca1e5e40eda8f13880cbb610。GitHub 连接身份 SturgeonIsSleepy、Public/main、pull/push 权限已确认；读取 README/AGENTS、近期日志/待办、技术基线、两项任务、工作日缓存、最新5轮CI与Issue1。Asia/Shanghai 实际日期2026-10-16是已核查工作日；次年检查不早于11-01，不重新搜索、不修改任何日程。凌晨独立逐行核心的CI38784336760成功且仍未接入，复用其断言与范围，不重复基准或成像开发。
- 审查范围：共享JpegPhotoWriter、PhotoEnhancer/PhotoBurstProcessor保存调用、PendingMediaWrite及既有图形回归。发现通用事务仅捕获IOException/RuntimeException：写入或发布Error会跳过清理，清理Error会覆盖原始失败。新增故障测试在旧核心分别失败于“New partial output is removed once”和“Cleanup Error cannot mask the primary failure”，均退出1，不是推测的真机现象。
- 实际修复：PendingMediaWrite对已获得身份的新输出，在IOException/RuntimeException/Error下均尝试一次回滚，并抛出同一原始实例；清理Error作为suppressed诊断，防自我抑制，诊断自身分配失败也不替换原错误。插入失败没有可归属输出时不删除任何未知项。成功发布项、源图和旧输出不回滚；没有吞掉Error或保证真实OOM后进程可用。两个Error分支仅故障注入，未耗尽堆、未在手机制造OOM。
- 本地验证：修改前旧核心942,626项通过；故障回归在旧核心失败，修复后bash scripts/check_core.sh通过942,702项（新增76），独立write-errors/cleanup-errors选择分别54/22项通过。主机JDK17、--release17/-Xlint:all/-Werror。尚未执行本轮Android构建/真机测试；源码推送后读回CI，不能以此前成功替代本轮结果。
- 已在Issue1评论6205654194交接具体复现与修复范围。不修改并发保留的JpegPhotoWriter、PhotoEnhancer、PhotoToneCurve、PhotoBurstProcessor或拍摄集成；集成文件的进一步问题先交接，不并发重写。
- 本轮规划：[x] Error回滚/异常归属修复和独立复现；[x] 额外推进共享JPEG输出的真实编码/EXIF、取消前后、发布/清理故障与源图保护图形回归；[ ] 负责人协调后接逐行核心并做13MP手机端总耗时/Java和native峰值。现有直方图/噪声/光晕、真正不同曝光输入、成熟防抖沿用既定待办，不重复记录不变研究。
- 模型/推理强度无法核实，剩余额度无法读取；没有限额信号，先保存本次真实修复，再推进上述限定验证目标。没有设备仅暂停本轮真机检查，不停用仍有独立工作的既有任务，不请求本轮用户操作。
- 修复已保存到02f1567ae5bc644fc2e46ba145faf9959a0713b1，expected main SHA / force=false推送并读回，4个变更blob核对。CI38833455721 completed/success，日志实际确认942,702核心断言、2张历史JPEG完整像素/哈希、两个flavor各8项既有水印回归与双APK/Lint，BUILD SUCCESSFUL in 1m 6s。此处仅确认修复，不以它替代后续新增测试的结果。
- 保存上述进展后，额外新增JpegPhotoWriterTest的9个真实图形/JPEG/EXIF测试方法：归一EXIF1..8后角点/宽高/所选元数据和工作图只读；写前/发布前取消与发布后保留；0/2行发布、元数据/输出失权；发布Error同实例与回滚、清理Error诊断及未清理状态。可控提供者逐次检查源图和旧结果字节/归属；测试源图仅合成色块/零坐标，不含私人照片。沿用既有FD到同文件Exif适配，仅增加测试登记接口，不改保存器或拍摄/算法集成。新增定义尚待本轮后续CI执行；本地42文件语法与git diff --check通过，未假称手机或本地Android运行。
- 额外回归已提交cc97e21986c456d83f21344e8532c96a28967414，CI https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38833842432 completed/success且head一致。日志确认942,702核心断言、两张历史JPEG的20,758,528个完整像素与哈希、两flavor各17项图形测试（原水印8+新JPEG保存9）、双APK/Lint，BUILD SUCCESSFUL in 44s。已下载artifact12018303388 / 3,316bytes，ZIP SHA256 5e5567b93658dfddaf75a85c0202ae9a224b80fea7396e7a409817711b6d500d匹配；实际4份XML分别9/8项，每份失败/错误/跳过均0。沿用artifact名watermark-test-results，现在包含两个测试类；APK/Lint artifact12018413140 / 36,710,133bytes / digest cb1b12ff582b495f4094f9cd3bd50de082885a7d975321a5d6261ed2d3d78a99，保留至2026-10-23T01:13:33Z，测试ZIP至01:13:32Z。
- 两次源码推送均使用expected main SHA / force=false并读回核对；6个新增/修改文件blob匹配，其余156个原远程文件与权限位保留。并发保留的保存/拍摄/成像集成、版本/依赖/工作流均未改。README与审查文档补齐实际结果；后续仅文档，无新运行时代码，不重复构建。未安装CI包、不创建实测Release；真实MediaStore/Android FD系统调用/低内存/开合屏仍待有线负责人，原始Error继续抛出、不保证OOM恢复，既有待办已将本轮两项标完成。

## 2026-10-17｜只读多曝光元数据初筛

- 起始提交：0a9e62f0dd92c30449bac794cf96d025ab8ced3c。已确认GitHub身份SturgeonIsSleepy、Public/main与pull/push；固定远程SHA读取README/AGENTS、近期日志及待办、技术基线、两项任务、工作日缓存、最新CI和Issue1。最新main仍为昨日交付，无新整合/实机结果；保留Error事务修复与34项图形回归，未重复基准或影像功能。
- 按Issue1现有协调，不并发修改PhotoEnhancer/PhotoToneCurve/JpegPhotoWriter/PhotoBurstProcessor或拍摄/预览/版本/依赖/Manifest。逐行核心仍待负责人接入；本轮选择后续真正不同曝光输入的前置小目标，并在评论6221707934先给接口/边界。
- 实际新增ExposureBracketAssessment：恰好3帧，检查有限正曝光时间/光圈/ISO/焦距、13MP内归一尺寸、相机标识/光圈/焦距一致、无闪光及完整文件摘要不同。按log2(t/N²)排序，并检查此代理与计入ISO后的log2(t*ISO/N²)每侧均至少1EV；1EV是本项目保守首阶段策略，不是HDR通用标准。ISO-only、增益抵消、相同曝光、缺字段/尺寸或镜头不一致均拒绝；光圈/焦距1%容差逐对比较，不依赖输入排列。返回仅元数据候选及索引/相对档位，Frame.toString隐藏原图标识；不将EXIF、摘要或候选当真实采集、同场景或质量证明。
- Android只读JpegExposureInspector读取3个content URI，单文件32MiB预算、每次读/skip检查取消、逐流SHA-256不保留整张RGB；原图只读、不创建媒体/日志/上传。BoundedDigestInputStream位于解析器mark/reset缓冲下，skip也纳入摘要，读/available的I/O、预算和取消错误保持粘滞，解析器吞掉异常后仍不能返回候选；预算边界最多探测1个额外字节。库内部元数据分配、阻塞提供者的强制中断、真实性或完整JPEG像素解码均非保证范围。
- 依据：核对Android Camera2官方CaptureResult中曝光时间、ISO增益、光圈/实际结果与AE覆盖规则，以及AndroidX官方ExifInterface输入流源码（只读、调用方关闭，兼容性捕获I/O）。将t/N²及ISO乘积仅作为项目代理，未复制外部算法代码。没有控制系统/Google相机曝光，没有集成闭源Google代码或把自制检查称GCam/HDR+。
- 本地实际验证：基线942,702项通过；新增后bash scripts/check_core.sh在JDK17/--release17/-Xlint:all/-Werror通过942,894项（新增192），覆盖6种顺序、双跨度、ISO-only/抵消、阈值、缺字段/重复/镜头/尺寸/闪光及流摘要/mark/reset/skip/预算/取消粘滞。java scripts/ParseJava.java通过48文件语法，git diff --check通过。无PATH中Gradle/ADB，未在本地构建或操作手机。
- 新增JpegExposureInspectorTest的10项NATIVE graphics/真实JPEG-EXIF定义，包含元数据排序、缺字段、ISO/补偿标记不冒充实际曝光、重复文件和尾字节摘要、非content/失权/坏文件/取消/预算、只读及描述符关闭模型；专门演示相同像素仅改EXIF也可成为“元数据候选”，防止错误真实性声明。此时尚待源码CI实际执行，不能把测试定义说成通过。
- 本轮待办：[x] 独立只读初筛与核心/流验证；[x] 读取双flavor新增回归、构建/Lint和原始JPEG证据的真实CI结果；[ ] 负责人核验系统路径能否取得真实不同曝光输入、实际曝光/场景/像素证据后再设计低内存融合。逐行整合及13MP总耗时/Java-native峰值沿用原待办，不重复研究或基准。原始拍摄、EXIF、指纹等真实私有数据只在授权设备本地，本轮公开测试全为合成。
- 仅集成/真机能力核验暂缺负责人设备/协调，其他独立工作继续。模型/推理强度无法核实，剩余额度无法读取；不编造额度或模型设置。复用2026已核查工作日缓存、2027下一检查不早于11-01，未查新年表/改日程；晨间休息日规则不用于本次每日开发。
- 源码dba482baeb05a9f57601d916e2f871060d3e8887已使用expected main SHA / force=false推送并读回，10个变更blob/权限位一致，其余159个原文件和权限位保留。9个保留的原成像/保存/拍摄/版本/Manifest/工作流文件哈希单独核对不变，原逐行核心也未重做或偷偷接入。
- CI https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/38946658536 completed/success，head精确对应本轮源码，日志确认942,894核心断言、原始JPEG2张/20,758,528完整像素及哈希、双APK/Lint与各flavor27项图形/元数据回归（既有8+9、新增10），BUILD SUCCESSFUL in 57s。已下载测试artifact12066362235 / 5,160bytes，ZIP SHA256 a3394f707a6dbf97d88dc7fb16db54a5f5104e03decc642d854e67b3d84c0782匹配；实际6份JUnit XML分别10/9/8项各两份，失败/错误/跳过均0。APK/Lint artifact12066177289 / 36,725,085bytes / digest 68efb0dc645dded40b0d62715f13c16254cda98ff50d20f969153bd4c0c54d08，保留至2026-10-23T18:22:27Z，测试ZIP至18:22:26Z。未安装CI包、不创建新实测Release；没有把元数据候选说成拍摄/图像质量验收。收尾仅更新文档和待办，不重复构建。

## 2026-10-17｜先交付基础拍照版，移除重叠按钮

- 从d15fbb1续接全部前两天成果：逐行色调核心尚待接入、通用保存Error回滚已生效、曝光元数据初筛不能证明真实HDR采集。用户反馈附加按钮和系统控件重叠，优先只保留基础拍照，再要求可选直方图/PS式裁切黑白场与成熟算法。Issue1同步两位agent的文件协调、算法评估与基础保存审查，继续仅仓库交流和负责人独占有线设备。
- 新增basic构建类型，0.4.2-basic/28沿用包名和签名可更新安装；拍摄只保留系统快门/确认与GPU实时左右纠正，不创建三个附加控件，返回页仅拍照和返回。通过编译配置强制禁用多帧/水印/视频/照片增强，忽略旧增强偏好但不删除偏好或旧源码，拍照只复制原始JPEG不重编码。CI增加基础APK/Lint产物；既有重连脚本增加-BasicCapture检查0个自有按钮。
- 本地942894核心断言、基础APK/Lint及coverCompat的27项图形/保存/EXIF回归通过，49秒构建成功。最终45,261,516bytes，SHA256 e7b226989a6948f4675eff65b7b5c090f73b2f0dedfc9515f3102c9cdb343332与真实已安装包一致。ADB安装由外屏对本包的风险提示确认完成，无需展开。
- 合盖实际两次保存、重拍回到取景、取消退出服务、三次强制停止后正常重开通过，均phase1/额外控件0个，诊断耗时1.959/2.267/2.284秒。文字与31方向正确；系统回传JPEG与保存JPEG逐字节相等且完整独立解码。一次自动点击间隔过短停在review，按真实phase等待后重拍/确认通过，未把失败序列计为成功。实拍及其标识只留本地；报告docs/basic-camera-2026-10-17.md，公开收据仅软件校验与功能统计。
- 未改变全机DPI/偏好，读取到本次外屏density200而早期为160。外屏助手、手机重启初始化、逐张系统确认和未完成前后摄/展开/全方向/长期耐久边界保留。本版是经短时回归的基础基线，不作永久稳定承诺。先发布此独立APK/tag，再在增强构建中接逐行核心、直方图黑白场和成熟算法对照；本条不提前宣称后续功能完成。


## 2026-10-17｜增强版实时直方图、同窗口色阶和成熟NLM

- 从已独立发布的基础版d8e4bb8续接，保持前两天的Error回滚/共享JPEG事务及曝光元数据初筛，实际接入PhotoToneRowProcessor。HDR与细节两组真实设备合成输出对比旧版最大通道误差0，未把已有算法重写或元数据候选冒充HDR采集。基础Release415523527 / asset649227308已远程校验，tag固定d8e4bb8，CI38992698267成功。
- 增强lab为独立org.codeaurora.snapcam.lab，不覆盖基础包。可选取景RGB综合直方图在原GPU预览内每500ms采样，不新增按钮窗/覆盖系统快门；原图采样分布不冒称RAW曝光。用户回复要求实时也显示后已实现并合盖验证开/关。拍后手动黑白场0<=black<white<=255，RGB通道裁切/线性映射、原图对照/还原、完整另存和取消，源图保留。
- 初版独立编辑Activity偶发被vivo FlipToContinue拦截，改为SystemCaptureActivity内替换内容并接管/释放Back回调，最终实测无新Activity切换/展开要求。首个实时HUD在Shell画文字触发默认字体native断言并由监护重启，已去掉文字绘制，只保留几何曲线，最终连续预览正常。基础冻结包不含这些实验变化。两次自动点击过快没等到页面就绪的序列未计作成功，后续按真实界面与phase操作。
- 成熟算法采用现有OpenCV4.12官方fastNlMeansDenoising直接RGB三通道h5/template7/search21，512块/16重叠从不可变源读取，降噪先于手动色阶，原有受限细节/JPEG HDR在后。Lab色彩NLM候选在强拉伸时有额外量化色带，改RGB避免该往返；未声称所有JPEG都有收益、超分或Google算法。默认全部拍后算法关闭，基础快门不等待后处理。
- 本地944181核心断言、双flavor各27项图形/保存/EXIF回归通过；多次lab构建/Lint通过，最后仅保存提示文案修正，33秒成功。算法实际ARM64七组合成检查（手动色阶/色块/恒等/EXIF6/取消/NLM/13MP）通过；最终包再跑NLM及实际色阶另存。固定图NLM MSE比0.3676296、平坦噪声std比0.4594235、文字边缘比1.0001129，分块/独立整图最大通道差0。小图save1218/2415ms两次不同运行，不把包含参考计算的测试总耗时当算法耗时；13MP合成单次save21807ms，仍较慢，不是实时降噪，也未测峰值内存。
- 合盖实拍完整另存、控件参数和软件说明一致、原始JPEG逐字节保留、独立解码和原图对照/直方图切换均通过。实验最终APK45,512,720bytes，SHA256 89b8b4786b112df91057331c6885dd6424bcb3c09ecdf1c577c6337758d10f70与实际已安装包一致。公开报告docs/photo-editor-2026-10-17.md及evidence/editor-2026-10-17.json、合成PNG；私人实拍/哈希/EXIF等标识不上传。最后恢复手机基础版桥接及原基础安装校验，不让实验包替换日常基线。
- 待办：代码agent继续13MP真实流水线耗时/峰值与成熟算法跨场景取舍，避免直接把NLM塞快门；检查agent审查同窗口编辑的取消/重建/原图引用、实时采样和Shell渲染边界。实际不同曝光输入、成熟视频防抖、展开/前摄/全旋转/长期耐久仍未验收；继续仅GitHub协作，不修改日程或全机DPI。

- 最终交付核验：增强源码5ca935798003b54217a9ba28df93f33bfca77a69推送并读回一致。Release415534970 / tag v0.4.2-editor-lab精确指向该源码，ZIP649284619 / 18,434,875bytes / digest2bb3e963df9d18b0a88f1064f089c10215ef018f3bd4a7675380ee0e15e9a903，内APK再次核对与手机相同；脚本649285056 / digest8f64831415d4ab46fa8502d24b7024017d268eba697c8436d675a9d23c414bc2。基础Release另补同脚本649286884。两个ZIP和脚本均实际上传，不再仅提供CI候选。
- CI38994690542完成success/head=5ca9357，APK/Lint artifact12086218890已上传；测试artifact12086184130已下载、摘要c73f200282f7740c4b70b41f73e4455ca608e27a1d479ba2a2ac98107a64dbab一致，6份JUnit XML共54项、失败错误跳过0。本次只补收据/入口，不改运行时代码或重复构建。最终手机基础包仍e7b22698并恢复其桥接；两应用切换桥接目标需指定包名，不能称其同时独立获得授权。

## 2026-10-18｜NLM 去除整图原始副本

- 起始提交：6a05954704f905191dd628e89bb1529628a5c2ff；GitHub连接身份SturgeonIsSleepy，仓库Public/main及push权限已确认。读取最新README/AGENTS/近期日志/技术基线/两项任务文档/工作日缓存，以及10-17基础和实验版报告、Issue1交接、处理器/编辑器与已有检查。仅从新鲜GitHub清单恢复89个精确blob/权限位，不依赖旧目录状态；复用已完成的逐行集成、同窗口编辑和RGB NLM。
- Issue1 comment6238257735先报告具体文件和范围。冻结basic与lab的现有tag/Release/APK均不修改，不改UI/桥接/拍摄、版本/Manifest、工作流或依赖。PhotoAdjustmentProcessor仍用OpenCV h5/7/21及512块/16重叠，原完整不可变Bitmap副本换成UnmodifiedRowWindow滚动全宽行缓存；保留相邻带32行重叠，每个原始行只在写前读一次，横向tile共享原始快照。增加读带前/进度后/写回前取消检查，不承诺中断正在执行的native NLM。
- 缓存payload可计算：3072×4096原50,331,648bytes（48MiB）变6,684,672bytes（6.375MiB），4096×3072变8,912,896bytes（8.5MiB）。这是单一原始像素缓存差额，不是Java/native/进程峰值实测，不声称提速；仍有完整工作Bitmap、tile/Mat和编码器。原始JPEG、另存事务及未支持gainmap边界保持。
- 本地验证：基线944,181项通过；新版bash scripts/check_core.sh在JDK17/--release17/-Xlint:all/-Werror通过66,775,056项，新增65,830,875；覆盖两种13MP方向、边界/随机形状、逐tile和halo原始像素、源数组写后污染、每行一次、padding、溢出、取消/I/O/RuntimeException/Error原样传播和终止。临时将retained置0实际复现“Never read an already modified source row”，恢复后全量通过；故障版本未提交。java scripts/ParseJava.java通过56文件语法，git diff --check通过。本地PATH无Gradle/ADB，没有在本地构建Android或访问设备。
- 新增BitmapRowWindowTest三项API34 NATIVE图形回归定义，实际Bitmap分块覆盖对照旧整图副本；不执行ARM64 OpenCV。双flavor单测/四APK/Lint待源码提交后读真实CI。详见docs/denoise-row-window.md，README纠正逐行核心“尚未接入”的旧状态。
- 共享待办：[x] 行缓存实现、逐像素/失败和本地回归；[x] 核验本轮Android CI及测试XML；[ ] 负责人以本轮候选重跑独立整图NLM、取消、13MP保存并测完整流水线多次耗时/Java-native-进程峰值，设备和测量方法齐备后解除；[ ] 同窗口旧预览任务/Back/重建、实时采样/worker恢复和basic开关审查沿用交接。真实不同曝光采集、成熟视频防抖、前摄/内屏/全方向/长期耐久未完成，不重做旧研究。
- 当前没有授权X Flip设备，只暂停本轮设备验收。模型/推理强度无法核实，剩余额度无法读取。沿用已核查2026工作日缓存，2027下一核查不早于11-01，未重复搜索/修改日程；每日开发不因休息日跳过。原始私有照片及标识不上传，未复制Google闭源算法。
- 源码6e276c5e4204c6948386ba9e314b26cfbccf4139以expected main SHA / force=false推送并读回，8个变更blob/权限位一致，178个其余原文件及权限位保留。CI https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/39056967060 completed/success、head精确匹配；日志确认66,775,056核心断言、原始JPEG2张/20,758,528完整像素及哈希、两个debug单测各30项、四APK/Lint，BUILD SUCCESSFUL in 1m18s。实际下载测试artifact12109821472 / 6,321bytes，SHA256 0bee75c646391a1f25d48cd9dda60b63839eb11d3b96d896bba46f42e4ea2422匹配；8份XML分别3/10/9/8各两份，合计60项，失败/错误/跳过均0。APK/Lint artifact12109288874 / 73,491,524bytes / digest3a742e99b91ef8b5eb20d4a14fc56a9ef83cf21fa9d2d2bd011a4b1504132226已上传，测试ZIP至2026-10-24T18:12:38Z，APK/Lint至18:12:39Z。本轮不安装CI候选、不发布新实测包，真实NLM像素/13MP耗时/峰值依旧待设备。收尾只补本证据和文档表述，不重复构建。


## 2026-10-18｜负责人同机验收滚动行缓存

- 从62bbec19a9118cff5bcfc8c9ba52b651063445dd续接6e276c5实现与CI，不重做缓存/直方图，不更动冻结basic和旧lab的tag/APK。Issue1评论6246783433先协调真实设备测试，继续仅GitHub沟通；手机当时在线解锁，外屏助手启动后解除Activity102。仅将开发版本升级0.4.3/29，并新增profile_photo_adjustment_device.py重复现有13MP设备检查，末次独立作meminfo采样。
- 实际先测已装0.4.2-lab(89b8b478...)再装新包，APK SHA256与手机匹配。旧、新各3次同一合成JPEG完整保存，前2次不采样、末次采样：旧14236/15746/16181ms，新16003/18124/19812ms，末次带采样不混入无采样统计。未固定温度/频率和随机顺序，候选样本更慢，不宣称提速，更不拿昨日21807ms当今天基线。
- dumpsys meminfo以0.5s等待加命令延迟采样，26/31个有效样本；观测PSS175961→135664KiB（约23%降低），RSS287120→247372KiB，native allocated116265→91441KiB，Java allocated5653→12091KiB。是测试窗口离散观测峰值/下界，不是精确峰值或理论缓存payload；不相加各账户不同瞬间最大值，不把内存转移隐去。
- 六份13MP输出与旧首份逐像素最大差均0；OpenCV native库摘要相同；独立整图NLM最大差0、固定图MSE比0.3676296/边缘1.0001129维持，新取消检查通过。合盖真实拍照/实时直方图/同窗口编辑进入；开启NLM另存后处理中按Back，媒体行数不增加，保存引用和原图字节不变，再开编辑器正常。真实图片/哈希/文件名/EXIF不上传，公开仅合成功能与软件数据。
- 本地66775056核心、双debug各30图形回归、lab构建/Lint通过，1m50s完成。新包45310836bytes，SHA256 3a7ee0ac902adc7773614fd0ae59705c2435008440e310cff800f3aa70d541e1，与手机一致。报告docs/nlm-device-2026-10-18.md及evidence/nlm-device-2026-10-18.json含完整软件/合成测量，补旧缓存报告的设备验收状态。
- 手机结束恢复基础版桥接，原基础APK仍e7b226989a6948f4675eff65b7b5c090f73b2f0dedfc9515f3102c9cdb343332。未改变全机DPI/性能/日程。下一步代码agent做受控CPU瓶颈/耗时优化并保留内存和像素界限；检查agent继续同窗口快速滑动/重建、worker恢复、basic门禁。真正不同曝光输入、成熟视频防抖、前摄/展开/全方向/长期耐久仍待后续；不扩展本轮成功声明。

- 远程收尾：源码a0db9cfb18d3c36caf7fe3b5782c8593934c4830已推送读回；Release416296035 / tag v0.4.3-row-cache-lab绑定该源码。ZIP652767589 / 18434519bytes / digest36bbb373e2a55cf0245a2cb20ee824b5e16a552986d29454fed1941e0c7c91fc，服务端digest及内APK均核对；初始化脚本652773163 / digest8f64831415d4ab46fa8502d24b7024017d268eba697c8436d675a9d23c414bc2已上传。CI39106630156 success/head一致，测试artifact12128563320已下载校验53ea9f0d1b9eb6462e8ef35caee6141e9e810729698b871888252d5c5d09bc2d，8XML共60项失败/错误/跳过0，四APK/Lint artifact12128389053已保存。本次只补文档收据，不改运行时或重复构建。


## 2026-10-18｜按用户批准清理重复相机安装

- 用户反馈多个外屏相机入口混乱并明确批准卸载。实际确认最早com.sturgeon.xflipcamera为0.1.0-dev/1，独立org.codeaurora.snapcam.lab为0.4.3-lab/29，日常org.codeaurora.snapcam为0.4.2-basic/28。仅卸载前两者，两次ADB均Success；复查包管理与LAUNCHER活动，本项目只剩基础版入口。
- 卸载前后在设备本地逐文件比较Pictures/XFlipCamera共享照片摘要，完全一致；原始照片、摘要和文件名均不公开。系统相机及必需cn.pashiguoke.subscreen助手保留，未清理无明确归属的第三方GCam或系统组件，也未删除仓库/Release成果。
- 原桥接进程当时未运行，重新初始化保留的基础版，外屏正常进入phase1/gpu_preview，40帧、capture_misses=0；返回后退出取景。未要求展开或改变全机设置。
- 后续默认只有一个日常相机。实验包只供临时验证，测试结束卸载；不再默认长期并装重复入口，日常升级优先同包覆盖。本次只有安装清理与README/日志更新，git diff --check为对应检查，不重复构建或算法测试。


## 2026-10-18｜用户明确只保留最新测试版

- 用户进一步明确“只安装最新测试版”，覆盖此前默认保留基础版的选择。重新安装已核验的0.4.3-lab/29，APK与手机摘要3a7ee0ac902adc7773614fd0ae59705c2435008440e310cff800f3aa70d541e1一致；随后停止本项目旧基础桥接并卸载org.codeaurora.snapcam。确认本项目LAUNCHER和包清单只剩org.codeaurora.snapcam.lab，旧com.sturgeon.xflipcamera仍未安装；系统相机/外屏助手保留。
- 卸载前后在设备本地逐文件校验共享Pictures/XFlipCamera照片，完全一致，未公开真实照片/摘要/文件名。安装和移除顺序先确保新包成功，未修改源码算法或重新构建。
- 自动审批曾拒绝ADB开启相机/悬浮窗权限，理由为安装授权未覆盖持久安全设置；未绕过，先完成独立安装/清理，再向用户明确询问两项权限及覆盖其他应用的能力。用户随后明确回答“允许开启这两项权限”，再执行并读回CAMERA granted=true、SYSTEM_ALERT_WINDOW allow。
- 已按.lab包名初始化桥接，实际合盖进入gpu_preview/phase1、35帧、capture_misses=0，再返回退出取景。以后设备默认只保留最新测试版，优先对.lab同包更新，不再默认恢复基础版或增加并列入口；旧Release/tag留存不代表手机仍安装。README及GitHub交接同步这一最新偏好。
