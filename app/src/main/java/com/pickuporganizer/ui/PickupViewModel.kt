package com.pickuporganizer.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pickuporganizer.data.PickupItemEntity
import com.pickuporganizer.data.PickupRepository
import com.pickuporganizer.data.RawMessageEntity
import com.pickuporganizer.extract.ExtractedPickup
import com.pickuporganizer.extract.PickupExtractor
import com.pickuporganizer.model.LlamaModelManager
import com.pickuporganizer.model.ModelStatus
import com.pickuporganizer.settings.AppSource
import com.pickuporganizer.settings.ListenerPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PickupViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PickupRepository.get(application)
    private val modelManager = LlamaModelManager.get(application)

    init {
        viewModelScope.launch {
            modelManager.loadModel()
        }
    }

    val pickupItems: StateFlow<List<PickupItemEntity>> = repository.observePickupItems()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val rawMessages: StateFlow<List<RawMessageEntity>> = repository.observeRawMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _enabledPackages = MutableStateFlow(ListenerPreferences.enabledPackages(application))
    val enabledPackages: StateFlow<Set<String>> = _enabledPackages

    private val _testResult = MutableStateFlow<ExtractedPickup?>(null)
    val testResult: StateFlow<ExtractedPickup?> = _testResult

    val modelStatus: StateFlow<ModelStatus> = modelManager.status

    private val _modelTestResult = MutableStateFlow<ExtractedPickup?>(null)
    val modelTestResult: StateFlow<ExtractedPickup?> = _modelTestResult

    fun updateStatus(id: Long, status: String) {
        viewModelScope.launch {
            repository.updateStatus(id, status)
        }
    }

    fun updatePickupItem(id: Long, appSource: String, station: String?, pickupCode: String, status: String) {
        viewModelScope.launch {
            repository.updatePickupItem(id, appSource, station, pickupCode, status)
        }
    }

    fun togglePackage(appSource: AppSource, enabled: Boolean) {
        ListenerPreferences.setAllowed(getApplication(), appSource.packageName, enabled)
        _enabledPackages.value = ListenerPreferences.enabledPackages(getApplication())
    }

    fun testExtraction(text: String, packageName: String = "com.jingdong.app.mall") {
        _testResult.value = PickupExtractor.extract(text, packageName, appName = "规则测试")
    }

    fun importModel(uri: Uri) {
        viewModelScope.launch {
            modelManager.importModel(uri)
        }
    }

    fun loadModel() {
        viewModelScope.launch {
            modelManager.loadModel()
        }
    }

    fun testModelExtraction(text: String, packageName: String = "com.jingdong.app.mall") {
        viewModelScope.launch {
            modelManager.loadModel()
            _modelTestResult.value = modelManager.extractIfReady(text, packageName, appName = "模型测试")
        }
    }

    fun saveSample(text: String, packageName: String = "com.jingdong.app.mall") {
        viewModelScope.launch {
            repository.ingestRawMessage(
                RawMessageEntity(
                    sourcePackage = packageName,
                    appName = "规则测试",
                    title = "测试通知",
                    body = text,
                    postedAtMillis = System.currentTimeMillis(),
                    notificationKey = "manual-${text.hashCode()}-${System.currentTimeMillis()}"
                )
            )
        }
    }
}
