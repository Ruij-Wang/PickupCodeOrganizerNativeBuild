package com.pickuporganizer.model

import android.content.Context
import android.net.Uri
import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.pickuporganizer.extract.ExtractedPickup
import com.pickuporganizer.extract.PickupExtractor
import com.pickuporganizer.extract.SourceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.io.File

class LlamaModelManager private constructor(private val appContext: Context) {
    private val mutex = Mutex()
    private val modelsDir = File(appContext.filesDir, "models")
    private val modelFile = File(modelsDir, MODEL_FILE_NAME)
    private var engine: InferenceEngine? = null

    private val _status = MutableStateFlow(modelFile.toStatus())
    val status: StateFlow<ModelStatus> = _status.asStateFlow()

    suspend fun importModel(uri: Uri): ModelStatus = withContext(Dispatchers.IO) {
        mutex.withLock {
            modelsDir.mkdirs()
            appContext.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "无法打开模型文件" }
                modelFile.outputStream().buffered().use { output ->
                    input.copyTo(output)
                }
            }
            _status.value = modelFile.toStatus(engineState = "已导入，未加载")
            _status.value
        }
    }

    suspend fun loadModel(): ModelStatus {
        mutex.withLock {
            if (!modelFile.exists()) {
                return modelFile.toStatus(lastError = "没有找到模型文件，请先导入 GGUF")
                    .also { _status.value = it }
            }
            if (_status.value.ready) return _status.value

            return try {
                _status.update { modelFile.toStatus(engineState = "加载中", busy = true) }
                waitUntilEngineInitialized()
                val readyEngine = requireNotNull(engine) { "模型引擎未初始化" }
                readyEngine.loadModel(modelFile.absolutePath)
                readyEngine.setSystemPrompt(SYSTEM_PROMPT)
                modelFile.toStatus(engineState = "已加载", ready = true).also { _status.value = it }
            } catch (e: Exception) {
                modelFile.toStatus(engineState = "加载失败", lastError = e.message ?: e::class.java.simpleName)
                    .also { _status.value = it }
            }
        }
    }

    suspend fun extractIfReady(rawText: String, packageName: String, appName: String?): ExtractedPickup? {
        if (!_status.value.ready) {
            if (modelFile.exists() && !_status.value.busy) {
                loadModel()
            }
            if (!_status.value.ready) return null
        }
        return mutex.withLock {
            if (!_status.value.ready) return@withLock null
            try {
                _status.update { it.copy(engineState = "推理中", busy = true, lastError = null) }
                val normalized = PickupExtractor.normalize(rawText)
                val prompt = buildPrompt(normalized)
                val readyEngine = requireNotNull(engine) { "模型引擎未加载" }
                val response = withTimeout(45_000L) {
                    val builder = StringBuilder()
                    readyEngine.sendUserPrompt(prompt, predictLength = 192).collect { builder.append(it) }
                    builder.toString()
                }
                _status.update { it.copy(engineState = "已加载", busy = false) }
                parseModelResponse(response, packageName, appName, normalized)
            } catch (e: Exception) {
                _status.update {
                    it.copy(engineState = "推理失败", busy = false, lastError = e.message ?: e::class.java.simpleName)
                }
                null
            }
        }
    }

    fun hasLoadedModel(): Boolean = _status.value.ready

    private suspend fun waitUntilEngineInitialized() {
        val readyEngine = engine ?: AiChat.getInferenceEngine(appContext).also { engine = it }
        withTimeout(20_000L) {
            while (true) {
                when (readyEngine.state.first()) {
                    is InferenceEngine.State.Initialized,
                    is InferenceEngine.State.ModelReady -> return@withTimeout
                    is InferenceEngine.State.Error -> throw IllegalStateException("推理引擎初始化失败")
                    else -> delay(120L)
                }
            }
        }
    }

    private fun buildPrompt(normalizedText: String): String =
        """
        请从下面通知中抽取快递取件信息，只返回一行 JSON。
        JSON 字段固定为：
        {"app_source": string|null, "station": string|null, "pickup_code": string|null, "confidence": number}
        通知：$normalizedText
        """.trimIndent()

    private fun parseModelResponse(
        response: String,
        packageName: String,
        appName: String?,
        normalizedText: String
    ): ExtractedPickup? {
        val jsonText = Regex("""\{[\s\S]*}""").find(response)?.value ?: return null
        val json = JSONObject(jsonText)
        val pickupCode = json.optString("pickup_code").takeIf { it.isNotBlank() && it != "null" }
        if (pickupCode.isNullOrBlank()) return null
        val station = json.optString("station").takeIf { it.isNotBlank() && it != "null" }
        val confidence = json.optDouble("confidence", 0.6).toFloat().coerceIn(0f, 1f)
        val appSource = json.optString("app_source")
            .takeIf { it.isNotBlank() && it != "null" }
            ?: SourceResolver.resolve(packageName, normalizedText, appName)

        return ExtractedPickup(
            appSource = appSource,
            station = station,
            pickupCode = pickupCode,
            confidence = confidence,
            normalizedText = normalizedText
        )
    }

    private fun File.toStatus(
        engineState: String = if (exists()) "已发现模型，未加载" else "未安装模型",
        ready: Boolean = false,
        busy: Boolean = false,
        lastError: String? = null
    ): ModelStatus = ModelStatus(
        modelFileName = takeIf { exists() }?.name,
        modelFileBytes = takeIf { exists() }?.length(),
        engineState = engineState,
        ready = ready,
        busy = busy,
        lastError = lastError
    )

    companion object {
        private const val MODEL_FILE_NAME = "Qwen2.5-0.5B-Instruct-Q4_K_S.gguf"
        private const val SYSTEM_PROMPT =
            "你是快递通知结构化抽取器。严格只输出 JSON，不输出解释、Markdown 或多余文字。"

        @Volatile private var instance: LlamaModelManager? = null

        fun get(context: Context): LlamaModelManager =
            instance ?: synchronized(this) {
                instance ?: LlamaModelManager(context.applicationContext).also { instance = it }
            }
    }
}
