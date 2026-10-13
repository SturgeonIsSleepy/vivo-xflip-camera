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
- 环境限制：没有 Android SDK、Gradle、ADB 或已连接 X Flip；GitHub 文本连接器拒绝 JPEG 二进制，原始照片重解码交给远程 CI checkout 后执行。新源码 APK/Lint 与照片检查需核验实际远程日志，不以工作流定义宣称成功。
- 待办：P0 有线负责人继续 Camera2 ID/合盖策略与实际绑定状态探测，接续 displayId 修复并证明明亮场景有效图像；P1 验收新 JPEG 页面，再在有效图像恢复后执行前后摄/内外屏/四方向/折叠与镜像矩阵；P2 合法成像接入与厂商扩展查询。当前只暂停依赖设备的操作，未集成 GCam 专有算法。
- 模型/推理强度无法核实，剩余额度无法读取；没有限额提示，按已授权规则完成范围明确的独立目标。项目仍未完成，未停用后续任务。
