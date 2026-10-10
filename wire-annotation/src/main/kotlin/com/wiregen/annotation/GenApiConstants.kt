package com.wiregen.annotation

import kotlin.reflect.KClass

/**
 * 标记一个 `object` 为 API 路径常量规格。处理器读取其中的 `const val` 字符串，
 * 按段（或整段）做算法后生成新的 object，例如 `SparrowApiConstants`。
 *
 * 全局默认值写在 `ksp { arg(...) }` 里；未单独配置时，类名前缀/后缀/包名会回退到
 * 对应的 `wire.*` 共享参数：
 *
 * ```
 * ksp {
 *     arg("wire.classPrefix", "Sparrow")
 *     arg("wire.path.nameRule", "dict")
 *     arg("wire.path.dict", file("path-names.properties").absolutePath)
 *     arg("wire.path.mappingFile", file("build/outputs/path-mapping.json").absolutePath)
 * }
 * ```
 *
 * ```
 * @GenApiConstants
 * object ApiConstants {
 *     const val GET_APP_CONFIG_PATH: String = "api/app/ext/config/getApp"
 * }
 * ```
 *
 * [pathEncoder] 与模型侧共用 [WireNameEncoder]；默认按 `/` 分段后对每一段调用算法。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenApiConstants(
    val classPrefix: String = WIRE_UNSET,
    val classSuffix: String = WIRE_UNSET,
    /** 生成 object 所在包。空字符串表示与规格同一个包。 */
    val packageName: String = WIRE_UNSET,
    val pathRule: WireNameRule = WireNameRule.OPTION,
    /**
     * 自定义路径算法。默认 [WireNameEncoder] 表示不指定，改走 [pathRule] 或全局 `wire.path.nameEncoder`。
     */
    val pathEncoder: KClass<out WireNameEncoder> = WireNameEncoder::class,
)

/**
 * 调整单个路径常量。[raw] 非空时直接作为生成值；[key] 非空时作为算法输入（替代 const 字面量）。
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ApiPath(
    /** 参与算法的语义路径。空字符串表示用 const 的字符串字面量。 */
    val key: String = "",
    /** 非空时直接作为生成路径，跳过算法和编码器。 */
    val raw: String = "",
)
