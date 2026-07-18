package com.pickuporganizer.extract

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupExtractorTest {
    @Test
    fun extractsPlannedExample() {
        val result = PickupExtractor.extract(
            rawText = "您的京东快递已到xx大学xx园x号菜鸟驿站京东点，请凭提货号A-1024前往领取",
            packageName = "com.jingdong.app.mall"
        )

        assertEquals("京东", result.appSource)
        assertEquals("A-1024", result.pickupCode)
        assertEquals("xx大学xx园x号菜鸟驿站京东点", result.station)
        assertTrue(result.confidence >= 0.85f)
    }

    @Test
    fun extractsThirtyCommonNotificationSamples() {
        val samples = listOf(
            Sample("您的京东包裹已到菜鸟驿站xx大学xx园店,凭取件码A12345领取", "com.jingdong.app.mall", "京东", "A12345"),
            Sample("菜鸟提醒: 包裹已到xx大学xx园x号菜鸟驿站,取件码 8-0091", "com.cainiao.wireless", "菜鸟", "8-0091"),
            Sample("淘宝订单已送至南校园妈妈驿站,提货码 T88012", "com.taobao.taobao", "淘宝", "T88012"),
            Sample("拼多多快递放在北门丰巢快递柜,编号: PDD7788", "com.xunmeng.pinduoduo", "拼多多", "PDD7788"),
            Sample("微信服务通知: 你的快递已到西区代收点,凭码3-7788领取", "com.tencent.mm", "微信", "3-7788"),
            Sample("【短信】快递已到东门菜鸟驿站,请凭提货号2-3456领取", "com.android.mms", "短信", "2-3456"),
            Sample("京东物流: 已到xx园京东点,货架号 JD9981", "com.jingdong.app.mall", "京东", "JD9981"),
            Sample("菜鸟: 到达大学城菜鸟驿站A区,领取码 CN2024", "com.cainiao.wireless", "菜鸟", "CN2024"),
            Sample("包裹存放在B栋丰巢快递柜,取货码 F9876", "com.android.mms", "短信", "F9876"),
            Sample("您的快递已到南门代收点，取件码：6-1000，请尽快领取", "com.google.android.apps.messaging", "短信", "6-1000"),
            Sample("天猫超市包裹已送至生活区菜鸟驿站,提货号 TM1234", "com.taobao.taobao", "淘宝", "TM1234"),
            Sample("拼多多: 快递已到东区驿站,取件码 P12345", "com.xunmeng.pinduoduo", "拼多多", "P12345"),
            Sample("您的包裹已放至xx园x号菜鸟驿站,编号 102938", "com.cainiao.wireless", "菜鸟", "102938"),
            Sample("快递到达xx大学快递柜,凭取货码 7-8888 开柜", "com.android.mms", "短信", "7-8888"),
            Sample("京东快递已到西门京东点，提货码：JD-7788", "com.jingdong.app.mall", "京东", "JD-7788"),
            Sample("菜鸟裹裹: 包裹到达xx园妈妈驿站,凭码 1-2233", "com.cainiao.wireless", "菜鸟", "1-2233"),
            Sample("微信: 快递助手通知,已送至宿舍区代收点,取件码 WX5566", "com.tencent.mm", "微信", "WX5566"),
            Sample("淘宝: 您的快件已到北区菜鸟驿站,编号 TB8888", "com.taobao.taobao", "淘宝", "TB8888"),
            Sample("PDD物流提醒 已到南门快递柜 取件码 9-0001", "com.xunmeng.pinduoduo", "拼多多", "9-0001"),
            Sample("短信提醒: 您的快递已到图书馆菜鸟驿站,凭提货码 SMS1", "com.android.mms", "短信", "SMS1"),
            Sample("包裹已到东苑驿站,取件码: ZXCV9", "com.android.mms", "短信", "ZXCV9"),
            Sample("京东服务通知 已到行政楼代收点 提货号 5-4321", "com.jingdong.app.mall", "京东", "5-4321"),
            Sample("菜鸟提醒 到达综合楼丰巢 取货码 FC1234", "com.cainiao.wireless", "菜鸟", "FC1234"),
            Sample("淘宝物流 包裹送至西区快递柜 领取码 LQ2026", "com.taobao.taobao", "淘宝", "LQ2026"),
            Sample("微信通知 包裹已到南区菜鸟驿站 凭码 4-5678", "com.tencent.mm", "微信", "4-5678"),
            Sample("拼多多订单已到楼下代收点，货架号 HJ9001", "com.xunmeng.pinduoduo", "拼多多", "HJ9001"),
            Sample("快递已存放至北门妈妈驿站，取件码 MM7788", "com.google.android.apps.messaging", "短信", "MM7788"),
            Sample("京东快递到达xx大学菜鸟驿站，请凭编号 778899 领取", "com.jingdong.app.mall", "京东", "778899"),
            Sample("菜鸟：您的包裹已到东门京东点，提货号 2-0008", "com.cainiao.wireless", "菜鸟", "2-0008"),
            Sample("短信：请到西苑代收点领取包裹，取件码 QWER12", "com.android.mms", "短信", "QWER12")
        )

        samples.forEach { sample ->
            val result = PickupExtractor.extract(sample.text, sample.packageName)
            assertEquals(sample.expectedSource, result.appSource)
            assertEquals(sample.expectedCode, result.pickupCode)
            assertTrue("Expected confidence for ${sample.text}", result.confidence >= 0.7f)
        }
    }

    @Test
    fun doesNotInventPickupCodeForIrrelevantText() {
        val result = PickupExtractor.extract("您的订单已发货，预计明天送达，请关注物流更新", "com.taobao.taobao")

        assertNull(result.pickupCode)
        assertTrue(result.confidence < 0.65f)
    }

    @Test
    fun normalizesFullWidthPunctuation() {
        val result = PickupExtractor.extract("快递已到南门菜鸟驿站，取件码：6－2230", "com.android.mms")

        assertEquals("6-2230", result.pickupCode)
        assertNotNull(result.station)
    }

    private data class Sample(
        val text: String,
        val packageName: String,
        val expectedSource: String,
        val expectedCode: String
    )
}
