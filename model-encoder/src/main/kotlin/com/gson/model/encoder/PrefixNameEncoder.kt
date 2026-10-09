package com.gson.model.encoder

import com.gson.model.annotation.SerializedNameEncoder

/**
 * 编译期示例：语义名前面加 `k_`。
 * 传入的是属性名或 [com.gson.model.annotation.WireField.key]，不含参数前后缀。
 */
class PrefixNameEncoder : SerializedNameEncoder {
    override fun encode(semantic: String): String = "k_$semantic"
}
