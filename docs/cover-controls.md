# 外屏原生控件组件

按 Issue 1 的独立接口交接新增 CoverControls.java。组件已实现并编入应用，尚未接入 SystemCaptureActivity / PreviewMirrorService；当前启动入口和实际拍摄操作没有因此改变。0.3.0 完整候选包的安装验收仍按原计划进行，不能把新组件的构建或布局断言当作完整界面实测。

## 接口与窗口

构造为 CoverControls(Context displayContext, Callbacks callbacks)，回调为 onMode(boolean video) 和 onSettings()；公开 attach()、detach()、setMode(boolean)、setEnabled(boolean)、isAttached()。所有操作在主线程调用。

调用者必须传入与实际拍摄 Display 关联的 Activity、display context 或 window context，不能用未指定 Display 的普通 Service/Application context 推测外屏。组件创建并复用对应 TYPE_APPLICATION_OVERLAY 的 window context，通过当前 WindowMetrics、系统栏/刘海 Insets 与该 context 的密度布局，不使用固定屏幕尺寸或全局 DPI。

设置、拍照、录像分别为一个原生 Button 和一个独立小型 WindowManager 窗口。设置位于左上，拍照位于右上，录像位于右下；按钮为 64×48 dp，边距 8 dp。三个触摸矩形互不相交，中央保留至少 48 dp 高的贯穿可用宽度的区域，不为控件添加整块透明触摸窗口。窗口太小则拒绝 attach，不靠压小触摸目标覆盖中间区域。682×422 只在测试固定样例中出现。

窗口采用 NOT_FOCUSABLE / NOT_TOUCH_MODAL，不订阅窗口外触摸。此几何保证只涵盖新增的三个控件，不代表已解决既有预览 SurfaceView 占用取景点击对焦的问题，也不保证 vivo 会接受其他应用窗口后的所有点击；原生快门、确认、对焦、系统遮挡策略仍需实际设备验证。

## 接入责任

```java
// overlayWindowContext 必须关联当前拍摄的 display；callbacks 由拍摄流程提供。
CoverControls controls = new CoverControls(overlayWindowContext, callbacks);
controls.setMode(video);
controls.attach();
// 实际模式切换成功后，再 controls.setMode(newVideo)。
// 离开取景、进入确认/设置、前台相机变化及服务销毁时 controls.detach()。
```

- onMode 只发请求，点击当前模式不重复回调，也不在外部相机尚未接受时假装切换成功。宿主负责待处理结果/缓存 URI/媒体归属，不能简单叠加第二次拍摄 Intent；本组件不自动发送触摸、切换相机或操作系统权限。
- setEnabled(false) 禁用按钮回调并显示禁用状态，但小窗口仍存在；确认页、原生设置、录像停止按钮可能出现在这些位置，应该 detach，而非仅禁用。
- attach 已附着时不重复加窗；缺少悬浮窗授权时抛 SecurityException，不自行弹授权页。添加失败尝试移除本次已成功添加的窗口并保留原始异常。
- detach 注销配置监听并逆序尝试移除所有已添加窗口；一个移除失败不阻止其他窗口的清理，异常合并且失败窗口保留重试记录。尚未在真实 WindowManager 上注入故障，属于已实现、待集成验收的处理。
- window context 的配置变化重新查询尺寸/密度/Insets 并调整三个窗口。重排失败尝试移除窗口并记录异常；切换到另一个 Display 时，宿主应先 detach，再用新 Display 的 context 创建组件。没有自动跨 Display 迁移或后台保活功能。
- 不修改现有 Manifest、权限、Shell/Binder/预览渲染逻辑，不复制 Google Camera 界面资产或厂商标志。水印仍按独立 WatermarkProcessor 交接继续。

## 已执行的检查

基线 main：8c29dc5c0c947800488fbadda5bcce30312c5992。先执行旧核心检查得到 13,817 项通过；新增后如下：

```sh
bash scripts/check_core.sh
java -cp .build/core-checks com.sturgeon.xflipcamera.core.CoverControlLayoutChecks
java scripts/ParseJava.java
git diff --check
```

JDK 17 / --release 17 / -Xlint:all / -Werror，15,375 个核心断言通过，新增布局断言 1,558 项；涵盖 75 组方向/密度/Insets 组合、最小可用边界、极大尺寸与非法输入，检查矩形不重叠、不越界及中央快门行/区域不被新控件覆盖。语法解析通过 28 个 Java 文件，不能代替 Android API 和类型解析。本地 Gradle、ADB 不在 PATH，没有本地 APK 构建或实机操作；源码提交后的双 flavor assemble/Lint 结果需读回实际 CI。

## 后续验收

1. 先由有线负责人安装最终性能候选包，复验连续拍照、录像、设置、停止和长时间方向稳定，再按本接口接入三个控件。
2. 合盖后核对三个独立窗口的实际范围；中央及原生右侧快门/停止点击仍生效。确认页/设置页/切换其他前台应用后无窗口残留。重复 attach/detach 不新增多余窗口。
3. 换模式只有一次有效回调，失败时仍显示旧模式；录制或保存期间按宿主状态禁用/移除，不能丢弃已确认结果或误删原片。
4. 测试悬浮窗授权撤回、加窗中途失败、服务销毁、窗口变小、密度/字体变化、显示屏消失及重建。失权/空间不足时不留下透明触摸窗口；所有这些属于待手机验证，布局断言没有覆盖实际 InputDispatcher。

官方 API 依据：[Context.createWindowContext](https://developer.android.com/reference/android/content/Context#createWindowContext(int,%20android.os.Bundle)) 与 [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams)。授权、窗口类型、Insets 和窗口外触摸依据公开接口，不引入系统输入注入接口。
