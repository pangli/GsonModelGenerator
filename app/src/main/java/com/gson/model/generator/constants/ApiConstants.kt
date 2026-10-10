package com.gson.model.generator.constants

import com.gson.model.annotation.ApiPath
import com.gson.model.annotation.GenApiConstants
import com.gson.model.annotation.SerializedNameRule
import com.gson.model.encoder.PrefixNameEncoder

/**
 * 编译期规格。真正给网络层用的是生成出来的 object，例如 [SparrowApiConstants]。
 * 类前缀与路径算法来自 app/build.gradle.kts 的 `path.*` / `model.classPrefix`。
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

@GenApiConstants(pathRule = SerializedNameRule.BASE64)
object EncodedApiConstants {
    const val PING_PATH: String = "api/ping"
}
