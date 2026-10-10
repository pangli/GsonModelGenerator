package com.wiregen.sample.constants

import com.wiregen.annotation.ApiPath
import com.wiregen.annotation.GenApiConstants
import com.wiregen.annotation.WireNameRule
import com.wiregen.encoder.PrefixNameEncoder

/**
 * 编译期规格。真正给网络层用的是生成出来的 object，例如 [SparrowApiConstants]。
 * 类前缀与路径算法来自 app/build.gradle.kts 的 `wire.path.*` / `wire.classPrefix`。
 */
@GenApiConstants
object ApiConstants {
    const val GET_APP_CONFIG_PATH: String = "api/app/ext/config/getApp"

    @ApiPath(raw = "healthz")
    const val HEALTH_PATH: String = "api/health"
}

/** 整段路径走自定义编码器（每个 path segment 前加 `k_`）。 */
@GenApiConstants(pathEncoder = PrefixNameEncoder::class)
object CustomApiConstants {
    const val LOGIN_PATH: String = "api/user/login"
}

@GenApiConstants(pathRule = WireNameRule.BASE64)
object EncodedApiConstants {
    const val PING_PATH: String = "api/ping"
}
