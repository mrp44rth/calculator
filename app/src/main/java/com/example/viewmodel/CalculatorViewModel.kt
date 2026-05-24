package com.example.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.entity.CalculationHistory
import com.example.data.repository.HistoryRepository
import com.example.util.CalculatorEvaluator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CalculatorUiState(
    val expression: String = "",
    val previewResult: String = "",
    val error: String? = null,
    val isDarkTheme: Boolean = true
)

class CalculatorViewModel(private val repository: HistoryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = _uiState.asStateFlow()

    val historyList: StateFlow<List<CalculationHistory>> = repository.history
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

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
            // Rules for decimals: make sure we don't double up decimals in the same number token
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
                repository.insert(
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
            repository.clearAll()
        }
    }

    private fun deleteHistoryItem(id: Int) {
        viewModelScope.launch {
            repository.delete(id)
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

    companion object {
        fun provideFactory(repository: HistoryRepository): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CalculatorViewModel(repository) as T
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
}
