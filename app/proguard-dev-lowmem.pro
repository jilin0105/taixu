# TaiXuDev 低内存构建专用 R8 规则（仅在 TAIXU_DEV_BUILD=1 时追加）。
#
# 手机 PRoot 沙箱可用物理内存有限，R8 的 IR 优化阶段（inlining / class merging /
# outlining）内存峰值远高于裁剪与混淆阶段，是 minifyReleaseWithR8 OOM 的主因。
# 关闭优化后仍完整保留：
#   - 代码裁剪（tree shaking，移除未引用类与方法）
#   - 标识符混淆（class/method/field 重命名）
#   - 资源压缩（shrinkResources 由 AGP 独立执行，不受此规则影响）
# 正式发布构建不加载本文件，优化强度保持不变。
-dontoptimize
