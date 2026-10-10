package com.wiregen.encoder

import com.wiregen.annotation.WireNameEncoder

/**
 * 编译期示例：语义名前面加 `k_`。
 * 传入的是属性名或 [com.wiregen.annotation.WireField.key]，不含参数前后缀。
 */
class PrefixNameEncoder : WireNameEncoder {
    override fun encode(semantic: String): String = "k_$semantic"
}
