package com.gson.model.generator.model

import android.os.Build
import java.io.Serializable


open class BaseRequest(
    val phoneOsBySparrow: String = Build.VERSION.RELEASE,
    val phoneModelBySparrow: String = Build.MODEL,
    val platformBySparrow: String = "Android"
) : Serializable
