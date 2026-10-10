package com.wiregen.sample.model

import com.wiregen.annotation.GenModel
import com.wiregen.annotation.WireNameRule
import com.wiregen.annotation.WireField
import com.wiregen.annotation.WireMutability
import com.wiregen.encoder.PrefixNameEncoder


/**
 * 编译期规格。真正参与 Gson 的是生成出来的 data class，例如 SparrowDemoLoginWire。
 * 类前缀、参数后缀和默认算法来自 app/build.gradle.kts 的 ksp 参数。
 */
@GenModel(classSuffix = "Wire")
abstract class DemoChild(
    val appName: String,
)

@GenModel(classSuffix = "Wire")
abstract class DemoLogin(
    var phone: String,
    var children: List<DemoChild>? = null,
    @WireField(raw = "clientTag")
    val tag: String? = null,
    var note: String? = null,
    @WireField(mutable = WireMutability.VAR)
    val forcedVar: String? = null,
    val clientName: String = "SparrowInitDataUtils.appName",
) : BaseRequest()

@GenModel(
    classSuffix = "Dict",
    nameRule = WireNameRule.DICT,
)
abstract class PhoneBook(
    val phone: String,
    val triggerPoint: String,
)

@GenModel(classSuffix = "Test")
abstract class TestBook(
    val phoneTest: String,
    val triggerTest: String,
    val annotationTest: String,
)

/** [PrefixNameEncoder] 只作用于这个规格。`raw` 仍然跳过自定义算法。 */
@GenModel(classSuffix = "Wire", nameEncoder = PrefixNameEncoder::class)
abstract class CustomBook(
    val phone: String,
    @WireField(raw = "fixedKey")
    val tag: String? = null,
)
