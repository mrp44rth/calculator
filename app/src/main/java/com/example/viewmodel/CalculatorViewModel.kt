package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.CalculationHistory
import com.example.data.entity.VaultFile
import com.example.data.repository.HistoryRepository
import com.example.data.repository.VaultRepository
import com.example.util.CalculatorEvaluator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class CalculatorUiState(
    val expression: String = "",
    val previewResult: String = "",
    val error: String? = null,
    val isDarkTheme: Boolean = true,
    
    // Secret Vault States
    val isVaultOpen: Boolean = false,
    val vaultSetupStep: Int = 0, // 0=Closed, 1=Create PIN, 2=Confirm PIN, 3=Enter PIN, 4=Vault Dashboard
    val vaultInputPin: String = "",
    val tempSetupPin: String = "",
    val pinErrorMessage: String? = null
)

class CalculatorViewModel(
    application: Application,
    private val historyRepository: HistoryRepository,
    private val vaultRepository: VaultRepository
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    var isPickerActive: Boolean = false

    val historyList: StateFlow<List<CalculationHistory>> = historyRepository.history
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val vaultFiles: StateFlow<List<VaultFile>> = vaultRepository.allFiles
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun getPrefs() = getApplication<Application>()
        .getSharedPreferences("vault_prefs", Context.MODE_PRIVATE)

    fun onEvent(action: CalculatorAction) {
        when (action) {
            is CalculatorAction.Digit -> appendDigit(action.value)
            is CalculatorAction.Decimal -> appendDecimal()
            is CalculatorAction.Operator -> appendOperator(action.symbol)
            is CalculatorAction.Parenthesis -> appendParenthesis(action.symbol)
            CalculatorAction.Percent -> appendPercent()
            CalculatorAction.Power -> appendPower()
            CalculatorAction.SquareRoot -> appendSquareRoot()
            CalculatorAction.ToggleSign -> toggleSign()
            CalculatorAction.Backspace -> backspace()
            CalculatorAction.Clear -> clear()
            CalculatorAction.Evaluate -> evaluateExpression()
            CalculatorAction.ClearHistory -> clearHistory()
            is CalculatorAction.DeleteHistoryItem -> deleteHistoryItem(action.id)
            is CalculatorAction.UseHistoryItem -> useHistoryItem(action.item)
            CalculatorAction.ToggleTheme -> toggleTheme()
            
            // Vault Events
            CalculatorAction.OpenVaultRequest -> initiateVaultAccess()
            is CalculatorAction.VaultPasscodeDigit -> appendPasscodeDigit(action.digit)
            CalculatorAction.VaultPasscodeBackspace -> backspacePasscode()
            CalculatorAction.CloseVault -> closeVault()
            is CalculatorAction.SetPickerActive -> { isPickerActive = action.active }
            is CalculatorAction.SecureMediaPicked -> hideSelectedMedia(action.uri, action.isVideo)
            is CalculatorAction.UnhideVaultFile -> unhideVaultFile(action.file)
            is CalculatorAction.DeleteVaultFile -> deleteVaultFile(action.file)
        }
    }

    private fun toggleTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    private fun appendDigit(digit: String) {
        _uiState.update { state ->
            val newExpr = state.expression + digit
            state.copy(
                expression = newExpr,
                previewResult = tryEvaluate(newExpr),
                error = null
            )
        }
    }

    private fun appendDecimal() {
        _uiState.update { state ->
            val expr = state.expression
            val lastNumToken = expr.split(Regex("[+\\-−×÷()^√]")).lastOrNull() ?: ""
            if (!lastNumToken.contains('.')) {
                val newExpr = if (expr.isEmpty() || expr.last() in listOf('+', '−', '×', '÷', '(', '^')) {
                    expr + "0."
                } else {
                    expr + "."
                }
                state.copy(
                    expression = newExpr,
                    previewResult = tryEvaluate(newExpr),
                    error = null
                )
            } else {
                state
            }
        }
    }

    private fun appendOperator(symbol: Char) {
        _uiState.update { state ->
            val expr = state.expression
            if (expr.isEmpty()) {
                if (symbol == '−') {
                    CalculatorUiState(expression = "−")
                } else {
                    state
                }
            } else {
                val lastChar = expr.last()
                val isLastOperator = lastChar in listOf('+', '−', '×', '÷', '^')
                val newExpr = if (isLastOperator) {
                    expr.dropLast(1) + symbol
                } else if (lastChar == '(') {
                    if (symbol == '−') {
                        expr + "−"
                    } else {
                        expr
                    }
                } else {
                    expr + symbol
                }
                state.copy(
                    expression = newExpr,
                    previewResult = tryEvaluate(newExpr),
                    error = null
                )
            }
        }
    }

    private fun appendParenthesis(symbol: Char) {
        _uiState.update { state ->
            val expr = state.expression
            val newExpr = expr + symbol
            state.copy(
                expression = newExpr,
                previewResult = tryEvaluate(newExpr),
                error = null
            )
        }
    }

    private fun appendPercent() {
        _uiState.update { state ->
            val expr = state.expression
            if (expr.isNotEmpty() && (expr.last().isDigit() || expr.last() == ')')) {
                val newExpr = expr + "%"
                state.copy(
                    expression = newExpr,
                    previewResult = tryEvaluate(newExpr),
                    error = null
                )
            } else {
                state
            }
        }
    }

    private fun appendPower() {
        appendOperator('^')
    }

    private fun appendSquareRoot() {
        _uiState.update { state ->
            val expr = state.expression
            val newExpr = if (expr.isNotEmpty() && (expr.last().isDigit() || expr.last() == ')' || expr.last() == '%')) {
                expr + "×√"
            } else {
                expr + "√"
            }
            state.copy(
                expression = newExpr,
                previewResult = tryEvaluate(newExpr),
                error = null
            )
        }
    }

    private fun toggleSign() {
        _uiState.update { state ->
            val expr = state.expression
            if (expr.isEmpty()) {
                state.copy(expression = "−")
            } else {
                val regex = """(?:\b|(?<=[+\-−×÷(^√]))[−]?\d*(?:\.\d*)?$""".toRegex()
                val match = regex.find(expr)
                val newExpr = if (match != null && match.value.isNotEmpty()) {
                    val lastNum = match.value
                    val toggled = if (lastNum.startsWith("−")) {
                        lastNum.substring(1)
                    } else {
                        "−$lastNum"
                    }
                    expr.substring(0, match.range.first) + toggled
                } else {
                    expr + "−"
                }
                state.copy(
                    expression = newExpr,
                    previewResult = tryEvaluate(newExpr),
                    error = null
                )
            }
        }
    }

    private fun backspace() {
        _uiState.update { state ->
            val expr = state.expression
            if (expr.isNotEmpty()) {
                val newExpr = expr.dropLast(1)
                state.copy(
                    expression = newExpr,
                    previewResult = tryEvaluate(newExpr),
                    error = null
                )
            } else {
                state
            }
        }
    }

    private fun clear() {
        _uiState.update {
            it.copy(
                expression = "",
                previewResult = "",
                error = null
            )
        }
    }

    private fun evaluateExpression() {
        val state = _uiState.value
        val expr = state.expression
        if (expr.isEmpty()) return

        try {
            val resultValue = CalculatorEvaluator.evaluate(expr)
            val resultString = CalculatorEvaluator.formatResult(resultValue)
            
            viewModelScope.launch {
                historyRepository.insert(
                    CalculationHistory(
                        expression = expr,
                        result = resultString
                    )
                )
            }

            _uiState.update {
                it.copy(
                    expression = resultString,
                    previewResult = "",
                    error = null
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    error = e.message ?: "Invalid Format"
                )
            }
        }
    }

    private fun tryEvaluate(expr: String): String {
        if (expr.isEmpty()) return ""
        
        val lastChar = expr.last()
        if (lastChar in listOf('+', '−', '×', '÷', '(', '^', '√')) {
            return ""
        }
        
        val hasOperators = expr.any { it in listOf('+', '−', '×', '÷', '%', '^', '√') }
        if (!hasOperators) return ""

        return try {
            val result = CalculatorEvaluator.evaluate(expr)
            CalculatorEvaluator.formatResult(result)
        } catch (e: Exception) {
            ""
        }
    }

    private fun clearHistory() {
        viewModelScope.launch {
            historyRepository.clearAll()
        }
    }

    private fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            historyRepository.delete(id)
        }
    }

    private fun useHistoryItem(item: CalculationHistory) {
         _uiState.update { state ->
             state.copy(
                 expression = item.expression,
                 previewResult = item.result,
                 error = null
             )
         }
    }

    // ==========================================
    // Vault Crypt Flow Logics
    // ==========================================

    private fun initiateVaultAccess() {
        val hasPin = getPrefs().contains("vault_pin")
        _uiState.update { state ->
            if (hasPin) {
                state.copy(
                    isVaultOpen = true,
                    vaultSetupStep = 3, // Enter PIN
                    vaultInputPin = "",
                    pinErrorMessage = "Enter secure 4-digit PIN"
                )
            } else {
                state.copy(
                    isVaultOpen = true,
                    vaultSetupStep = 1, // Set up PIN
                    vaultInputPin = "",
                    tempSetupPin = "",
                    pinErrorMessage = "Set a secure 4-digit PIN for access"
                )
            }
        }
    }

    private fun appendPasscodeDigit(digit: String) {
        val state = _uiState.value
        val currentPin = state.vaultInputPin
        if (currentPin.length >= 4) return
        
        val newPin = currentPin + digit
        _uiState.update { it.copy(vaultInputPin = newPin, pinErrorMessage = null) }
        
        if (newPin.length == 4) {
            handleCompletePasscode(newPin)
        }
    }

    private fun backspacePasscode() {
        _uiState.update { state ->
            if (state.vaultInputPin.isNotEmpty()) {
                state.copy(vaultInputPin = state.vaultInputPin.dropLast(1), pinErrorMessage = null)
            } else {
                state
            }
        }
    }

    private fun closeVault() {
        _uiState.update { state ->
            state.copy(
                isVaultOpen = false,
                vaultSetupStep = 0,
                vaultInputPin = "",
                tempSetupPin = "",
                pinErrorMessage = null
            )
        }
    }

    private fun handleCompletePasscode(pin: String) {
        val state = _uiState.value
        when (state.vaultSetupStep) {
            1 -> {
                _uiState.update {
                    it.copy(
                        vaultSetupStep = 2,
                        tempSetupPin = pin,
                        vaultInputPin = "",
                        pinErrorMessage = "Confirm your 4-digit PIN"
                    )
                }
            }
            2 -> {
                if (pin == state.tempSetupPin) {
                    getPrefs().edit().putString("vault_pin", pin).apply()
                    _uiState.update {
                        it.copy(
                            vaultSetupStep = 4, // Unlocked
                            vaultInputPin = "",
                            tempSetupPin = "",
                            pinErrorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            vaultSetupStep = 1,
                            vaultInputPin = "",
                            tempSetupPin = "",
                            pinErrorMessage = "PIN codes didn't match. Set PIN again."
                        )
                    }
                }
            }
            3 -> {
                val savedPin = getPrefs().getString("vault_pin", null)
                if (pin == savedPin) {
                    _uiState.update {
                        it.copy(
                            vaultSetupStep = 4, // Unlocked
                            vaultInputPin = "",
                            pinErrorMessage = null
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            vaultInputPin = "",
                            pinErrorMessage = "Incorrect PIN. Try again."
                        )
                    }
                }
            }
        }
    }

    private fun hideSelectedMedia(uri: Uri, isVideo: Boolean) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val (originalName, size) = getFileInfo(context, uri)
                val mimeType = context.contentResolver.getType(uri) ?: if (isVideo) "video/mp4" else "image/jpeg"
                
                val ext = originalName.substringAfterLast('.', "").ifEmpty { if (isVideo) "mp4" else "jpg" }
                val localFileName = "vault_${System.currentTimeMillis()}.$ext"
                
                val subDir = if (isVideo) "videos" else "images"
                val vaultDir = File(context.getExternalFilesDir(null), ".hiddenfold/$subDir")
                if (!vaultDir.exists()) {
                    vaultDir.mkdirs()
                }
                
                // Create .nomedia within .hiddenfold
                val rootVaultDir = File(context.getExternalFilesDir(null), ".hiddenfold")
                if (!rootVaultDir.exists()) {
                    rootVaultDir.mkdirs()
                }
                val nomedia = File(rootVaultDir, ".nomedia")
                if (!nomedia.exists()) {
                    nomedia.createNewFile()
                }
                
                val targetFile = File(vaultDir, localFileName)
                
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        targetFile.outputStream().use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }
                
                val vaultFile = VaultFile(
                    fileName = localFileName,
                    filePath = targetFile.absolutePath,
                    originalName = originalName,
                    originalPath = uri.toString(),
                    mimeType = mimeType,
                    isVideo = isVideo,
                    size = size
                )
                
                vaultRepository.insert(vaultFile)
                
                // Attempt to delete original securely
                try {
                    context.contentResolver.delete(uri, null, null)
                } catch (se: SecurityException) {
                    // Fine on Q+ where permissions prevent direct deletion
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.update { it.copy(error = "Failed to secure media: ${e.message}") }
            }
        }
    }

    private fun unhideVaultFile(vaultFile: VaultFile) {
        viewModelScope.launch {
            try {
                val context = getApplication<Application>()
                val success = withContext(Dispatchers.IO) {
                    unhideFileHelper(context, vaultFile)
                }
                if (success) {
                    vaultRepository.delete(vaultFile)
                } else {
                    _uiState.update { it.copy(error = "Failed to restore file to gallery.") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Restore error: ${e.message}") }
            }
        }
    }

    private fun unhideFileHelper(context: Context, vaultFile: VaultFile): Boolean {
        val sourceFile = File(vaultFile.filePath)
        if (!sourceFile.exists()) return false

        val resolver = context.contentResolver
        val isVideo = vaultFile.isVideo
        
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, vaultFile.originalName ?: vaultFile.fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, vaultFile.mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = if (isVideo) "Movies/CalculatorVault" else "Pictures/CalculatorVault"
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        }

        val collectionUri = if (isVideo) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val targetUri = resolver.insert(collectionUri, contentValues) ?: return false

        return try {
            resolver.openOutputStream(targetUri)?.use { outputStream ->
                sourceFile.inputStream().use { inputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(targetUri, contentValues, null, null)
            }
            sourceFile.delete()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            try {
                resolver.delete(targetUri, null, null)
            } catch (ex: Exception) {}
            false
        }
    }

    private fun deleteVaultFile(vaultFile: VaultFile) {
        viewModelScope.launch {
            try {
                val file = File(vaultFile.filePath)
                if (file.exists()) {
                    file.delete()
                }
                vaultRepository.delete(vaultFile)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Delete error: ${e.message}") }
            }
        }
    }

    private fun getFileInfo(context: Context, uri: Uri): Pair<String, Long> {
        var name = ""
        var size = 0L
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIndex != -1) {
                        name = cursor.getString(nameIndex)
                    }
                    if (sizeIndex != -1) {
                        size = cursor.getLong(sizeIndex)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        if (name.isEmpty()) {
            name = uri.lastPathSegment ?: "file_${System.currentTimeMillis()}"
        }
        return Pair(name, size)
    }

    companion object {
        fun provideFactory(
            application: Application,
            historyRepository: HistoryRepository,
            vaultRepository: VaultRepository
        ): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CalculatorViewModel(application, historyRepository, vaultRepository) as T
                }
            }
        }
    }
}

sealed class CalculatorAction {
    data class Digit(val value: String) : CalculatorAction()
    object Decimal : CalculatorAction()
    data class Operator(val symbol: Char) : CalculatorAction()
    data class Parenthesis(val symbol: Char) : CalculatorAction()
    object Percent : CalculatorAction()
    object Power : CalculatorAction()
    object SquareRoot : CalculatorAction()
    object ToggleSign : CalculatorAction()
    object Backspace : CalculatorAction()
    object Clear : CalculatorAction()
    object Evaluate : CalculatorAction()
    object ClearHistory : CalculatorAction()
    data class DeleteHistoryItem(val id: Int) : CalculatorAction()
    data class UseHistoryItem(val item: CalculationHistory) : CalculatorAction()
    object ToggleTheme : CalculatorAction()
    
    // Vault Actions
    object OpenVaultRequest : CalculatorAction()
    data class VaultPasscodeDigit(val digit: String) : CalculatorAction()
    object VaultPasscodeBackspace : CalculatorAction()
    object CloseVault : CalculatorAction()
    data class SetPickerActive(val active: Boolean) : CalculatorAction()
    data class SecureMediaPicked(val uri: Uri, val isVideo: Boolean) : CalculatorAction()
    data class UnhideVaultFile(val file: VaultFile) : CalculatorAction()
    data class DeleteVaultFile(val file: VaultFile) : CalculatorAction()
}
