package com.pickuporganizer.settings

import android.content.Context

object ListenerPreferences {
    private const val PREF_NAME = "listener_preferences"
    private const val KEY_ENABLED_PACKAGES = "enabled_packages"

    val defaultPackages = listOf(
        AppSource("com.jingdong.app.mall", "京东"),
        AppSource("com.taobao.taobao", "淘宝"),
        AppSource("com.cainiao.wireless", "菜鸟"),
        AppSource("com.xunmeng.pinduoduo", "拼多多"),
        AppSource("com.tencent.mm", "微信"),
        AppSource("com.android.mms", "短信"),
        AppSource("com.google.android.apps.messaging", "短信")
    )

    fun isAllowed(context: Context, packageName: String): Boolean =
        enabledPackages(context).contains(packageName)

    fun enabledPackages(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getStringSet(KEY_ENABLED_PACKAGES, null)
            ?: defaultPackages.map { it.packageName }.toSet()
    }

    fun setAllowed(context: Context, packageName: String, allowed: Boolean) {
        val next = enabledPackages(context).toMutableSet()
        if (allowed) {
            next.add(packageName)
        } else {
            next.remove(packageName)
        }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY_ENABLED_PACKAGES, next)
            .apply()
    }
}

data class AppSource(
    val packageName: String,
    val displayName: String
)
