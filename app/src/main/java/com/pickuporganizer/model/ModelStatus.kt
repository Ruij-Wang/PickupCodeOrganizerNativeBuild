package com.pickuporganizer.model

data class ModelStatus(
    val modelFileName: String? = null,
    val modelFileBytes: Long? = null,
    val engineState: String = "未加载",
    val ready: Boolean = false,
    val busy: Boolean = false,
    val lastError: String? = null
) {
    val modeLabel: String
        get() = when {
            ready -> "规则 + 本地模型"
            modelFileName != null -> "模型未加载"
            else -> "规则引擎"
        }
}
