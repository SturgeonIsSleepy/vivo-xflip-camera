# 2026-10-19 编辑器预览与保存审查

起始main：`09bda3f7b283775822422e38db6e42f733f13868`。复用0.4.4同包NLM、滚动缓存和同窗口编辑，改动限定PhotoEditor及专用测试。用户选择仍为仅最新.lab，不恢复基础版，不改变设备授权或已有发布资产。

## 实际复现

1. 黑场10的预览完成后，将黑场改20，新预览尚未执行时另存暂时禁用。
2. 连续切换两次原图对照。旧showImage仅要求processed非空和当前参数changed，重新启用另存，未核对屏幕预览对应的参数。
3. 新参数预览失败后重复对照，也得到相同错误结果。保存使用新参数，用户看到的却是旧预览。

PhotoEditorTest以真实Android控件/Bitmap、可控工作队列和合成Processor，把这两个错误结果断言为true作临时基线特征。[基线源码afca6ed](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/commit/afca6ed353448ed4da116561f6aa6cb23fe638ef)的[CI39183455005](https://github.com/SturgeonIsSleepy/vivo-xflip-camera/actions/runs/39183455005)通过；下载测试ZIP校验一致，两套各2项特征成立。这只确认缺陷，后续已改为要求错误结果false的正式回归，基线绿灯不表示正确行为验收。

## 修复

成功接收预览时，同时保存不可变PhotoAdjustmentProcessor.Options。按钮与保存入口共用canSave，要求当前五项参数与预览快照相等，并且未关闭、未忙碌、不在原图对照且参数有实际调整。原始source固定不变。

| 状态 | 另存 |
| --- | --- |
| 新参数尚无匹配预览 | 禁止，对照不能重开 |
| 新参数预览失败，仍显示旧图 | 禁止 |
| 匹配预览已完成 | 允许 |
| 原图对照 / 保存中 / 已关闭 | 禁止 |
| 保存失败，参数仍匹配预览 | 允许重试 |

仍保留generation保护，旧成功/失败回调不能覆盖新状态。预览最长边960、保存全尺寸，NLM/细节的分辨率差异没有消除，不声称预览和成片逐像素相同。

## 排队与位图所有权

原实现先在主线程为每个排队请求复制Bitmap。关闭移除主线程回调，也可能使尚未显示的结果失去显式回收机会。

现在工作任务先检查关闭/generation，再在短临界区复制原图。关闭回收原图使用同一锁，不会同时复制和回收；NLM不在锁内。过期排队任务不复制、不调用处理器，仍使用单工作线程，不声称executor队列节点完全消失或native计算可以立即中断。

已处理结果在主线程接收前由pendingBitmaps持有，交给original/processed后移出；旧generation、关闭和Handler拒收均回收。初始化解码与预览共用交接，错误退出由finally清理本地位图。该改动不代表已测得手机峰值下降或OOM可恢复。

## 验证边界

- 本地JDK17核心66,775,056项、57文件语法、差异空白检查通过。无本地Gradle/ADB，没有连接或安装手机。
- 新版10项UI回归覆盖上表、旧回调、12个过期请求、三种关闭/未交付时序及Bundle在新owner恢复原始输入。合成Processor不执行OpenCV NLM或真实MediaStore，既有生产JPEG/EXIF事务回归继续执行。
- 修复源码的双flavor单测、四APK/Lint待实际CI。基线10XML/64项，修复后数量按真实产物核对。

下一步优先真机快速滑动、对照、失败重试、Back和实际Activity重建，继续worker/Surface恢复与basic门禁。新ComponentActivity恢复Bundle的主机检查不代替vivo切屏/进程死亡验收。设备由负责人控制，实拍及标识不公开。CPU阶段诊断仍只有计划，未实现或测得热点；成像、Google合法集成与原待办沿用。
