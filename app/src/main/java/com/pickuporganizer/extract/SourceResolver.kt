package com.pickuporganizer.extract

object SourceResolver {
    private val packageNames = mapOf(
        "com.jingdong.app.mall" to "京东",
        "com.taobao.taobao" to "淘宝",
        "com.cainiao.wireless" to "菜鸟",
        "com.xunmeng.pinduoduo" to "拼多多",
        "com.tencent.mm" to "微信",
        "com.android.mms" to "短信",
        "com.google.android.apps.messaging" to "短信",
        "com.samsung.android.messaging" to "短信"
    )

    private val textHints = listOf(
        "京东" to "京东",
        "菜鸟" to "菜鸟",
        "淘宝" to "淘宝",
        "天猫" to "淘宝",
        "拼多多" to "拼多多",
        "微信" to "微信",
        "短信" to "短信",
        "丰巢" to "丰巢"
    )

    fun resolve(packageName: String, text: String, appName: String? = null): String {
        packageNames[packageName]?.let { return it }
        textHints.firstOrNull { (hint, _) -> text.contains(hint) }?.let { return it.second }
        return appName?.takeIf { it.isNotBlank() } ?: packageName.substringAfterLast('.')
    }
}
