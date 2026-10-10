package com.gson.model.annotation

import kotlin.reflect.KClass

/**
 * 注解参数没写时使用的占位值，表示改走 KSP 参数。
 * 显式传入空字符串会覆盖全局配置。
 */
const val MODEL_UNSET = "__MODEL_UNSET__"

/**
 * 标记一个接口或类为 data class 规格。处理器只读取这里声明的属性，
 * 不会把这个类型本身变成 data class。
 *
 * 全局默认值写在模块的 `ksp { arg(...) }` 里，单个模型上的参数会覆盖它们：
 *
 * ```
 * ksp {
 *     arg("model.classPrefix", "Sparrow")
 *     arg("model.classSuffix", "Request")
 *     arg("model.paramPrefix", "")
 *     arg("model.paramSuffix", "BySparrow")
 *     arg("model.superClass", "com.example.BaseRequest")
 *     arg("model.superArgs", "")
 *     arg("model.packageName", "")
 *     arg("model.nameRule", "base64") // raw、base64、reverse、xor、dict
 *     arg("model.nameEncoder", "com.example.MyEncoder")
 *     arg("model.xorKey", "bd")
 *     arg("model.dict", file("wire-names.properties").absolutePath)
 *     arg("model.mappingFile", file("build/outputs/model-wire-mapping.json").absolutePath)
 * }
 * ```
 *
 * 属性名是语义名。生成后的构造参数名是「参数前缀 + 语义名 + 参数后缀」，
 * `@SerializedName` 的值由 [nameRule] 或 [nameEncoder] 在编译期算好。[WireField.raw] 可以跳过算法。
 * 单个规格写了 [nameEncoder] 时不再使用 [nameRule]。编码器类要有无参构造，并且放在 `ksp` 能看见的依赖里。
 *
 * 规格用抽象类。父类和默认值直接写在声明上，生成时原样使用：
 *
 * ```
 * @GenModel(classSuffix = "Wire")
 * abstract class DemoLogin(
 *     val phone: String,
 *     val clientName: String = SparrowInitDataUtils.appName,
 * ) : SparrowBaseRequest()
 * ```
 *
 * [superClass] 只在规格本身没有父类时生效。
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenModel(
    val classPrefix: String = MODEL_UNSET,
    val classSuffix: String = MODEL_UNSET,
    val paramPrefix: String = MODEL_UNSET,
    val paramSuffix: String = MODEL_UNSET,
    /** 规格没有写父类时使用的全限定名。空字符串表示不继承。 */
    val superClass: String = MODEL_UNSET,
    /** 写在父类构造调用括号里的源码，例如 `"Android"`。 */
    val superArgs: String = MODEL_UNSET,
    /** 生成类所在包。空字符串表示与规格类型同一个包。 */
    val packageName: String = MODEL_UNSET,
    val nameRule: SerializedNameRule = SerializedNameRule.OPTION,
    /**
     * 自定义 `@SerializedName` 算法。默认 [SerializedNameEncoder] 表示不指定，改走 [nameRule] 或全局 `model.nameEncoder`。
     */
    val nameEncoder: KClass<out SerializedNameEncoder> = SerializedNameEncoder::class,
)

/**
 * 调整单个属性。不写这个注解时，语义名就是属性名，可空属性默认 `= null`。
 */
@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class WireField(
    /** 参与算法的语义名。空字符串表示用属性名。 */
    val key: String = "",
    /** 非空时直接作为 `@SerializedName` 的值，不再走算法或 [SerializedNameEncoder]。 */
    val raw: String = "",
    /**
     * 规格属性没有写 `=` 时，用这段 Kotlin 表达式作为默认值。
     * 写了 `= SparrowInitDataUtils.appName` 这种默认值时，以源码为准。
     */
    val defaultCode: String = "",
    /** [WireMutability.FOLLOW] 时跟规格上的 `val` / `var`。 */
    val mutable: WireMutability = WireMutability.FOLLOW,
)

enum class WireMutability {
    /** 生成结果和规格声明一样。 */
    FOLLOW,

    /** 生成 `val`。 */
    VAL,

    /** 生成 `var`。 */
    VAR,
}

/**
 * 把语义名换成 `@SerializedName` 的值。实现类需要公开的无参构造，
 * 并且放在独立模块里，同时加入使用方的 `ksp` 依赖。
 *
 * ```
 * class MyEncoder : SerializedNameEncoder {
 *     override fun encode(semantic: String): String = semantic.reversed()
 * }
 * ```
 */
fun interface SerializedNameEncoder {
    fun encode(semantic: String): String
}

enum class SerializedNameRule {
    /** 使用 KSP 参数 `model.nameRule`，没配时等价于 [RAW]。 */
    OPTION,

    /** 语义名原样作为 JSON key。 */
    RAW,

    /** URL-safe Base64，不带填充。 */
    BASE64,

    /** 反转语义名。 */
    REVERSE,

    /** 用 `model.xorKey` 对 UTF-8 字节做异或，再输出小写十六进制。 */
    XOR,

    /** 查 `model.dict` 指向的 properties 文件，键是语义名。 */
    DICT,
}
