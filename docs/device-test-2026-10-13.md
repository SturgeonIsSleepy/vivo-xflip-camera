# 合盖系统相机回传与权限探测

起始本地提交为 c3f50ce，续接并合并 GitHub 最新 main 9404224。保留另一 agent 的独立 JPEG 检查、13,511 项核心断言和原始黑图检查；没有覆盖其成果。

## 实测通过的路径

0.1.6-dev / versionCode 7 的 coverCompat APK 已在 V2256A、Android 14、原 ROM 上安装。实际 base.apk 与本地文件 SHA256 完全一致：`9a5bdf82581c3991dffcd33f33214589a0cd1020cc967401c10f9420fcf00d80`，15,641,006 bytes。

全程实际 CLOSED、无 device-state override，外屏 display 1、682×422。入口为本应用“设置 → 系统相机 → 拍照”，原生相机拥有取景与快门界面，确认后返回本应用外屏显示成片，并保存至 Pictures/XFlipCamera。

- 通过公开 ACTION_IMAGE_CAPTURE / EXTRA_OUTPUT 调用 com.android.camera，CameraService 实际客户端为该包、镜头 ID 4。
- 前期直接把 MediaStore pending URI 传给系统相机，确认后得到取消结果。改为狭窄 cache FileProvider URI、ClipData、临时读写授权，再由本应用复制到自己的 MediaStore 记录，完整照片回传成功。
- 0.1.6 在同一会话连续两次拍照均成功：3,096,607 和 5,014,711 bytes，均为 3072×4096。独立 Pillow 全图解码确认 RGB 有真实信号，统计见 evidence/2026-10-13-system-capture。0.1.5 另有一次 3,108,781 bytes 的成功回传。
- 0.1.6 取消拍照后显示“已取消拍照”，两张已保存照片仍保留，没有新增空照片；证据 UI XML 只含本应用文字与控件。
- 原生预览与成片确认页的左右位置不同，预览仍有镜像现象。原生外屏设置提供延时、画幅和快门方式，没有发现预览镜像开关。尚未完成非对称卡片及旋转验收，不能宣称镜像修复。

系统成片含原生相机写入的 GPS 元数据，统计只记录其存在，不公开坐标。真实场景 JPEG、照片截图、厂商 APK 和反编译文件均不上传。

## 排除的假设与现有方案

| 路线 | 实测结果 | 结论范围 |
| --- | --- | --- |
| 包名 org.codeaurora.snapcam | 公开 ID 仍为 0、1；直接查询 4 返回 system only device 错误 | 该包名在此 ROM 上没有赋予系统镜头访问权 |
| ADB Shell / scrcpy 4.1 | Shell 权限文本有 SYSTEM_CAMERA granted；真实 UID 2000、com.android.shell 上下文直接查询 4 仍被拒绝；scrcpy 只枚举 0、1 | 不能据权限文本认定 Shizuku 能使用 ID 4 |
| 已安装 AGC 9.2 | IMAGE_CAPTURE 可解析并在外屏启动，出现 vivo“翻开手机以使用摄像头”和黑预览 | 未接通合盖 Google 成像；没有修改其镜头配置或分发其 APK |
| vivo 系统 IMAGE_CAPTURE | ID 4 真实预览，全尺寸 JPEG 回传、连续拍摄和取消已实测 | 可用的原生回传基础，成像来自 vivo，尚未提供我们自己的有效预览 |

scrcpy 的 Shell 公开 ID 0 录像生成了文件，但本轮视频解码没有成功，因此不把录像文件存在计为有效像素证明。探测源码与真实错误文本已保留，未复制 scrcpy 或厂商代码到应用。

外屏助手 cn.pashiguoke.subscreen 在本机承担普通应用外屏启动能力。暂时停止它后，文件管理及包装页出现 FlipToContinue；恢复它后可重新启动。相机测试保持实际合盖，但本轮仍使用既有外屏助手与 ADB 启动入口，独立外屏入口验收未完成。

## 本轮代码与验证

- MainActivity 加入窗口 KEEP_SCREEN_ON、实际选中 Camera ID / CameraState / PreviewView stream 诊断、受控 camera_id 探测，以及采样黑图提示。
- 缓存加入 displayId，修复检查 agent 指出的同尺寸显示屏迁移早退；此修复经过构建，尚未实测展开切屏。
- SystemCaptureActivity 明确指定当前 display；FileProvider 仅开放 cache/system-camera，取消或读取失败清理本次输出，已保存图库照片独立保留。
- standard 保持原包名；coverCompat 是复现包名假设的兼容测试包，不是系统相机伪装、系统权限或 Google 算法实现。
- 双 flavor APK、双 flavor Lint、13,511 个核心断言、两张首版原始照片共 20,758,528 个像素及哈希检查通过。一次构建曾因旧 ADB install 占用 APK 而失败，终止该安装命令后重跑成功。
- 安装全程外屏文件管理完成，使用带版本号文件名避免缓存旧安装元数据。ADB install 曾挂起，也曾被系统拒绝，不能笼统断言 ADB 不支持安装。
- 最终 CameraX ID 0 诊断为 CameraState OPEN、PreviewView STREAMING、权限已授予且 last_error=none，但 JPEG 仍全零。已实测显示“已保存，疑似黑图，请检查相机访问”；独立解码最新黑图为 62,824 bytes、4096×2534、三个 RGB 范围均 [0,0]。
- 独立 JPEG 分享页已在真实外屏接收该黑图，显示 512×317 采样、all_sampled_rgb_zero=true、EXIF 6；再分享有效系统成片时显示非零统计，验证其基础入口与连续分享更新。损坏输入、权限拒绝与进程恢复仍待运行时验收。

## 下一轮分工

代码撰写 agent：先以本轮系统回传作为有效照片基线，完成外屏可直接进入的入口及可恢复拍摄状态。优先验证普通应用能否取得真实像素；只有取得有效预览后再控制镜像。若研究 GCam，先核实已安装应用的公开回传接口与合盖可用性，不重复改包名，也不把 vivo 输出称为 Google 算法。

基础错误检查 agent：审阅外部 Activity Result、URI 临时授权、取消/解码失败/页面退出/恢复时的文件清理和旧结果归属；复查两个 flavor 的构建及产物路径。保留现有 JPEG 工具和核心检查，对系统回传的原始文件与元数据做检查。无实机的检查须保持“待设备验证”。

实机负责人：黑图提示及 JPEG 检查页黑/明亮图对照已完成；后续用非对称卡片核对预览/照片、四方向和外屏冷启动。用户目前无法展开，内屏/展开验收保持待办。

公开接口依据：[Android 拍照 Intent](https://developer.android.com/guide/components/intents-common#Camera)、[FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)、[系统相机权限](https://source.android.com/docs/core/camera/system-cameras)、[scrcpy 相机模式](https://github.com/Genymobile/scrcpy/blob/v4.1/doc/camera.md)。AOSP 的系统相机权限是系统/签名权限；本轮设备结果进一步表明，仅看 Shell 权限文本还不足以判断 vivo 的真实访问策略。
