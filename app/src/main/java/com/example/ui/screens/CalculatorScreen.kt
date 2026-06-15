package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.entity.CalculationHistory
import com.example.data.entity.VaultFile
import com.example.viewmodel.CalculatorAction
import com.example.viewmodel.CalculatorViewModel
import java.io.File

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CalculatorScreen(
    viewModel: CalculatorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val historyList by viewModel.historyList.collectAsStateWithLifecycle()
    val vaultFiles by viewModel.vaultFiles.collectAsStateWithLifecycle()
    var showHistory by remember { mutableStateOf(false) }

    val isDark = uiState.isDarkTheme

    val bgColors = if (isDark) {
        DarkCalculatorColors()
    } else {
        LightCalculatorColors()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Toolbar Row
            CalculatorHeader(
                appName = "Calculator",
                isDarkTheme = isDark,
                onToggleTheme = { viewModel.onEvent(CalculatorAction.ToggleTheme) },
                onToggleHistory = { showHistory = !showHistory },
                onHeaderLongClick = { viewModel.onEvent(CalculatorAction.OpenVaultRequest) },
                colors = bgColors
            )

            // 2. Display Viewport
            CalculatorDisplay(
                expression = uiState.expression,
                previewResult = uiState.previewResult,
                error = uiState.error,
                colors = bgColors,
                modifier = Modifier.weight(1f)
            )

            // 3. Buttons Grid Panel
            CalculatorButtonsPanel(
                onAction = { viewModel.onEvent(it) },
                colors = bgColors
            )
        }

        // 4. Sliding Custom History Overlay
        AnimatedVisibility(
            visible = showHistory,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(stiffness = 300f)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(stiffness = 300f)
            ) + fadeOut()
        ) {
            HistorySheetOverlay(
                history = historyList,
                onClose = { showHistory = false },
                onSelectItem = { item ->
                    viewModel.onEvent(CalculatorAction.UseHistoryItem(item))
                    showHistory = false
                },
                onDeleteItem = { id ->
                    viewModel.onEvent(CalculatorAction.DeleteHistoryItem(id))
                },
                onClearAll = {
                    viewModel.onEvent(CalculatorAction.ClearHistory)
                },
                colors = bgColors
            )
        }

        // 5. Secret Secured Vault Cover Overlay
        AnimatedVisibility(
            visible = uiState.isVaultOpen,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            VaultOverlay(
                uiState = uiState,
                vaultFiles = vaultFiles,
                onAction = { viewModel.onEvent(it) },
                colors = bgColors
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CalculatorHeader(
    appName: String,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onToggleHistory: () -> Unit,
    onHeaderLongClick: () -> Unit,
    colors: CalculatorColors
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = appName,
            color = colors.textPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .testTag("app_title")
                .combinedClickable(
                    onLongClick = onHeaderLongClick,
                    onClick = { /* normal press does nothing */ }
                )
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Theme Toggle Button
            IconButton(
                onClick = onToggleTheme,
                modifier = Modifier
                    .background(colors.buttonAccessoryBg, shape = CircleShape)
                    .testTag("theme_toggle_button")
            ) {
                Icon(
                    imageVector = if (isDarkTheme) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = "Toggle Theme",
                    tint = colors.buttonAccessoryText
                )
            }

            // History Panel Toggle Button
            IconButton(
                onClick = onToggleHistory,
                modifier = Modifier
                    .background(colors.buttonAccessoryBg, shape = CircleShape)
                    .testTag("history_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = "View History",
                    tint = colors.buttonAccessoryText
                )
            }
        }
    }
}

@Composable
fun CalculatorDisplay(
    expression: String,
    previewResult: String,
    error: String?,
    colors: CalculatorColors,
    modifier: Modifier = Modifier
) {
    val exprScrollState = rememberScrollState()

    // Trigger auto scroll to the end on any entry addition
    LaunchedEffect(expression) {
        exprScrollState.animateScrollTo(exprScrollState.maxValue)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .testTag("calculator_display"),
        verticalArrangement = Arrangement.Bottom,
        horizontalAlignment = Alignment.End
    ) {
        // Main Typed Equation Expression View Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(exprScrollState),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = expression.ifEmpty { "0" },
                color = if (expression.isEmpty()) colors.textMuted else colors.textPrimary,
                fontSize = if (expression.length > 12) 36.sp else 52.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.End,
                maxLines = 1,
                modifier = Modifier.testTag("expression_display")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Evaluation result preview or format errors view
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            if (error != null) {
                Text(
                    text = error,
                    color = Color(0xFFEF5350),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    modifier = Modifier.testTag("error_display")
                )
            } else if (previewResult.isNotEmpty()) {
                Text(
                    text = previewResult,
                    color = colors.textAccent,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.testTag("result_display")
                )
            } else {
                // Buffer space to prevent jumping layout heights when preview enters/leaves
                Spacer(modifier = Modifier.height(34.dp))
            }
        }
    }
}

@Composable
fun CalculatorButtonsPanel(
    onAction: (CalculatorAction) -> Unit,
    colors: CalculatorColors
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(colors.panelBg)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Scientific Accessor Buttons (Thin Pill Row)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AccessoryKey(symbol = "(", onClick = { onAction(CalculatorAction.Parenthesis('(')) }, colors = colors, modifier = Modifier.weight(1f))
            AccessoryKey(symbol = ")", onClick = { onAction(CalculatorAction.Parenthesis(')')) }, colors = colors, modifier = Modifier.weight(1f))
            AccessoryKey(symbol = "^", onClick = { onAction(CalculatorAction.Power) }, colors = colors, modifier = Modifier.weight(1f))
            AccessoryKey(symbol = "√", onClick = { onAction(CalculatorAction.SquareRoot) }, colors = colors, modifier = Modifier.weight(1f))
        }

        Divider(color = colors.divider, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))

        // Grid rows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CalculatorKey(
                symbol = "AC",
                onClick = { onAction(CalculatorAction.Clear) },
                colors = colors,
                keyType = KeyType.Utility,
                modifier = Modifier.weight(1f)
            )
            CalculatorKey(
                symbol = "⁺∕₋",
                onClick = { onAction(CalculatorAction.ToggleSign) },
                colors = colors,
                keyType = KeyType.Utility,
                modifier = Modifier.weight(1f)
            )
            CalculatorKey(
                symbol = "%",
                onClick = { onAction(CalculatorAction.Percent) },
                colors = colors,
                keyType = KeyType.Utility,
                modifier = Modifier.weight(1f)
            )
            CalculatorKey(
                symbol = "÷",
                onClick = { onAction(CalculatorAction.Operator('÷')) },
                colors = colors,
                keyType = KeyType.Operator,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CalculatorKey(symbol = "7", onClick = { onAction(CalculatorAction.Digit("7")) }, colors = colors, keyType = KeyType.Digit, modifier = Modifier.weight(1f))
            CalculatorKey(symbol = "8", onClick = { onAction(CalculatorAction.Digit("8")) }, colors = colors, keyType = KeyType.Digit, modifier = Modifier.weight(1f))
            CalculatorKey(symbol = "9", onClick = { onAction(CalculatorAction.Digit("9")) }, colors = colors, keyType = KeyType.Digit, modifier = Modifier.weight(1f))
            CalculatorKey(
                symbol = "×",
                onClick = { onAction(CalculatorAction.Operator('×')) },
                colors = colors,
                keyType = KeyType.Operator,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CalculatorKey(symbol = "4", onClick = { onAction(CalculatorAction.Digit("4")) }, colors = colors, keyType = KeyType.Digit, modifier = Modifier.weight(1f))
            CalculatorKey(symbol = "5", onClick = { onAction(CalculatorAction.Digit("5")) }, colors = colors, keyType = KeyType.Digit, modifier = Modifier.weight(1f))
            CalculatorKey(symbol = "6", onClick = { onAction(CalculatorAction.Digit("6")) }, colors = colors, keyType = KeyType.Digit, modifier = Modifier.weight(1f))
            CalculatorKey(
                symbol = "−",
                onClick = { onAction(CalculatorAction.Operator('−')) },
                colors = colors,
                keyType = KeyType.Operator,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CalculatorKey(symbol = "1", onClick = { onAction(CalculatorAction.Digit("1")) }, colors = colors, keyType = KeyType.Digit, modifier = Modifier.weight(1f))
            CalculatorKey(symbol = "2", onClick = { onAction(CalculatorAction.Digit("2")) }, colors = colors, keyType = KeyType.Digit, modifier = Modifier.weight(1f))
            CalculatorKey(symbol = "3", onClick = { onAction(CalculatorAction.Digit("3")) }, colors = colors, keyType = KeyType.Digit, modifier = Modifier.weight(1f))
            CalculatorKey(
                symbol = "+",
                onClick = { onAction(CalculatorAction.Operator('+')) },
                colors = colors,
                keyType = KeyType.Operator,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CalculatorKey(symbol = "0", onClick = { onAction(CalculatorAction.Digit("0")) }, colors = colors, keyType = KeyType.Digit, modifier = Modifier.weight(1f))
            CalculatorKey(symbol = ".", onClick = { onAction(CalculatorAction.Decimal) }, colors = colors, keyType = KeyType.Digit, modifier = Modifier.weight(1f))
            CalculatorKey(
                symbol = "⌫",
                onClick = { onAction(CalculatorAction.Backspace) },
                colors = colors,
                keyType = KeyType.Utility,
                modifier = Modifier.weight(1f)
            )
            CalculatorKey(
                symbol = "=",
                onClick = { onAction(CalculatorAction.Evaluate) },
                colors = colors,
                keyType = KeyType.Operator,
                isAccent = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

enum class KeyType {
    Digit, Operator, Utility
}

@Composable
fun CalculatorKey(
    symbol: String,
    onClick: () -> Unit,
    colors: CalculatorColors,
    keyType: KeyType,
    modifier: Modifier = Modifier,
    isAccent: Boolean = false
) {
    val background = when {
        isAccent -> colors.buttonAccentBg
        keyType == KeyType.Operator -> colors.buttonOperatorBg
        keyType == KeyType.Utility -> colors.buttonUtilityBg
        else -> colors.buttonDigitBg
    }
    val contentColor = when {
        isAccent -> colors.buttonAccentText
        keyType == KeyType.Operator -> colors.buttonOperatorText
        keyType == KeyType.Utility -> colors.buttonUtilityText
        else -> colors.buttonDigitText
    }

    val tagSymbol = when (symbol) {
        "+" -> "plus"
        "−" -> "minus"
        "×" -> "multiply"
        "÷" -> "divide"
        "=" -> "equal"
        "." -> "decimal"
        "⁺∕₋" -> "sign"
        "%" -> "percent"
        "⌫" -> "backspace"
        "AC" -> "clear"
        else -> symbol
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1.25f) // Styled chunky rounded aspect for satisfying touch
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .clickable(onClick = onClick)
            .testTag("btn_$tagSymbol")
    ) {
        Text(
            text = symbol,
            color = contentColor,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun AccessoryKey(
    symbol: String,
    onClick: () -> Unit,
    colors: CalculatorColors,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(colors.buttonAccessoryBg)
            .clickable(onClick = onClick)
            .testTag("btn_acc_${if(symbol=="^") "power" else if(symbol=="√") "root" else if(symbol=="(") "paren_open" else "paren_close"}")
    ) {
        Text(
            text = symbol,
            color = colors.buttonAccessoryText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistorySheetOverlay(
    history: List<CalculationHistory>,
    onClose: () -> Unit,
    onSelectItem: (CalculationHistory) -> Unit,
    onDeleteItem: (Int) -> Unit,
    onClearAll: () -> Unit,
    colors: CalculatorColors
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 80.dp)
            .testTag("history_sheet"),
        colors = CardDefaults.cardColors(containerColor = colors.panelBg),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            // Sheet Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.background(colors.buttonAccessoryBg, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close History",
                        tint = colors.textPrimary
                    )
                }

                Text(
                    text = "Calculation History",
                    color = colors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                if (history.isNotEmpty()) {
                    IconButton(
                        onClick = onClearAll,
                        modifier = Modifier.background(colors.buttonAccessoryBg, shape = CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Clear All History",
                            tint = Color(0xFFEF5350)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(48.dp))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ManageSearch,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No calculations recorded yet.",
                            color = colors.textMuted,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("history_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(history) { item ->
                        HistoryItemRow(
                            item = item,
                            onClick = { onSelectItem(item) },
                            onDelete = { onDeleteItem(item.id) },
                            colors = colors
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryItemRow(
    item: CalculationHistory,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    colors: CalculatorColors
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("history_item_${item.id}"),
        colors = CardDefaults.cardColors(containerColor = colors.background),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.expression,
                    color = colors.textMuted,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = item.result,
                    color = colors.textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete item",
                    tint = colors.textMuted
                )
            }
        }
    }
}

// Visual layout contracts
interface CalculatorColors {
    val background: Color
    val panelBg: Color
    val textPrimary: Color
    val textMuted: Color
    val textAccent: Color
    val divider: Color
    val buttonDigitBg: Color
    val buttonDigitText: Color
    val buttonOperatorBg: Color
    val buttonOperatorText: Color
    val buttonUtilityBg: Color
    val buttonUtilityText: Color
    val buttonAccentBg: Color
    val buttonAccentText: Color
    val buttonAccessoryBg: Color
    val buttonAccessoryText: Color
}

class DarkCalculatorColors : CalculatorColors {
    override val background = Color(0xFF11141E)       // Elegant deep midnight blueprint
    override val panelBg = Color(0xFF1B1E2B)          // Cohesive slate-navy card bottom board
    override val textPrimary = Color(0xFFECEFF1)      // Ultra clean soft titanium text
    override val textMuted = Color(0xFF78909C)        // Contrast gray-blue
    override val textAccent = Color(0xFF81C784)       // Calming soft grass dynamic light
    override val divider = Color(0xFF2C3246)
    
    override val buttonDigitBg = Color(0xFF24293D)
    override val buttonDigitText = Color(0xFFFFFFFF)
    
    override val buttonOperatorBg = Color(0xFFFF9F0A) // iOS premium warm solar gold
    override val buttonOperatorText = Color(0xFFFFFFFF)
    
    override val buttonUtilityBg = Color(0xFF323A54)
    override val buttonUtilityText = Color(0xFFCFD8DC)
    
    override val buttonAccentBg = Color(0xFF4CAF50)   // Rich professional forest green
    override val buttonAccentText = Color(0xFFFFFFFF)
    
    override val buttonAccessoryBg = Color(0xFF242B40)
    override val buttonAccessoryText = Color(0xFF90CAF9)
}

class LightCalculatorColors : CalculatorColors {
    override val background = Color(0xFFF6F8FE)       // Bright sleek cold ice ivory
    override val panelBg = Color(0xFFEBEEF5)          // Clear elevated contrast board base
    override val textPrimary = Color(0xFF1A1C22)      // Solid jet black typography
    override val textMuted = Color(0xFF6B7280)        // Neutral dark gray
    override val textAccent = Color(0xFF2E7D32)       // Rich forest green indicator
    override val divider = Color(0xFFD4DAE6)
    
    override val buttonDigitBg = Color(0xFFFFFFFF)
    override val buttonDigitText = Color(0xFF1A1C22)
    
    override val buttonOperatorBg = Color(0xFFFF9500) // Deep crisp premium pumpkin orange
    override val buttonOperatorText = Color(0xFFFFFFFF)
    
    override val buttonUtilityBg = Color(0xFFE2E7F3)
    override val buttonUtilityText = Color(0xFF4B5563)
    
    override val buttonAccentBg = Color(0xFF43A047)
    override val buttonAccentText = Color(0xFFFFFFFF)
    
    override val buttonAccessoryBg = Color(0xFFE4E9F2)
    override val buttonAccessoryText = Color(0xFF0F5CC0)
}

// ==========================================
// SECRET SECURE VAULT COMPONENT SCREENS
// ==========================================

@Composable
fun VaultOverlay(
    uiState: com.example.viewmodel.CalculatorUiState,
    vaultFiles: List<VaultFile>,
    onAction: (CalculatorAction) -> Unit,
    colors: CalculatorColors
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F121C)) // Carbon charcoal steel theme for private vault feel
    ) {
        when (uiState.vaultSetupStep) {
            1, 2, 3 -> {
                VaultPasscodeScreen(
                    step = uiState.vaultSetupStep,
                    inputPin = uiState.vaultInputPin,
                    errorMessage = uiState.pinErrorMessage,
                    onDigitClick = { onAction(CalculatorAction.VaultPasscodeDigit(it)) },
                    onBackspaceClick = { onAction(CalculatorAction.VaultPasscodeBackspace) },
                    onCloseVault = { onAction(CalculatorAction.CloseVault) }
                )
            }
            4 -> {
                VaultDashboardScreen(
                    vaultFiles = vaultFiles,
                    onAction = onAction,
                    onCloseVault = { onAction(CalculatorAction.CloseVault) }
                )
            }
        }
    }
}

@Composable
fun VaultPasscodeScreen(
    step: Int,
    inputPin: String,
    errorMessage: String?,
    onDigitClick: (String) -> Unit,
    onBackspaceClick: () -> Unit,
    onCloseVault: () -> Unit
) {
    val titleText = when (step) {
        1 -> "Create Vault PIN"
        2 -> "Confirm Vault PIN"
        else -> "Vault Encrypted"
    }
    
    val subtitleText = when (step) {
        1 -> "First-use setup: Choose a secure 4-digit PIN"
        2 -> "Enter your 4-digit password key once more"
        else -> "Input your 4-digit security PIN to unlock"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // High Top Back Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onCloseVault) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Exit Vault Setup",
                    tint = Color.White
                )
            }
        }

        // Header Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (step == 3) Icons.Rounded.Lock else Icons.Rounded.EnhancedEncryption,
                contentDescription = null,
                tint = Color(0xFFFF9F0A),
                modifier = Modifier.size(68.dp)
            )
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Text(
                text = titleText,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = subtitleText,
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            
            Spacer(modifier = Modifier.height(36.dp))
            
            // Password PIN Indicator Dots (max 4)
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until 4) {
                    val filled = i < inputPin.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(
                                color = if (filled) Color(0xFFFF9F0A) else Color.Transparent,
                                shape = CircleShape
                            )
                            .border(2.dp, Color(0xFFFF9F0A), CircleShape)
                    )
                }
            }
            
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = errorMessage,
                    color = Color(0xFFEF5350),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }
        }

        // Elegant Touch Numeric Keypad
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 40.dp)
        ) {
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9")
            )
            
            for (row in rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    modifier = Modifier.fillMaxWidth(0.85f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    for (digit in row) {
                        PinKeypadButton(
                            text = digit,
                            onClick = { onDigitClick(digit) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                modifier = Modifier.fillMaxWidth(0.85f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.weight(1f))
                
                PinKeypadButton(
                    text = "0",
                    onClick = { onDigitClick("0") },
                    modifier = Modifier.weight(1f)
                )
                
                IconButton(
                    onClick = onBackspaceClick,
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Backspace,
                        contentDescription = "Delete character",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PinKeypadButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(60.dp)
            .background(Color(0xFF242B40), shape = CircleShape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboardScreen(
    vaultFiles: List<VaultFile>,
    onAction: (CalculatorAction) -> Unit,
    onCloseVault: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Images, 1 = Videos
    var activePreviewFile by remember { mutableStateOf<VaultFile?>(null) }
    
    val photos = vaultFiles.filter { !it.isVideo }
    val videos = vaultFiles.filter { it.isVideo }
    
    val photoLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        onAction(CalculatorAction.SetPickerActive(false))
        if (uri != null) {
            onAction(CalculatorAction.SecureMediaPicked(uri, false))
        }
    }
    
    val videoLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        onAction(CalculatorAction.SetPickerActive(false))
        if (uri != null) {
            onAction(CalculatorAction.SecureMediaPicked(uri, true))
        }
    }

    Scaffold(
        containerColor = Color(0xFF0F121C),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = Color(0xFF81C784),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            text = "Secret Vault",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onCloseVault) {
                        Icon(
                            imageVector = Icons.Rounded.ArrowBack,
                            contentDescription = "Exit Vault",
                            tint = Color.White
                        )
                    }
                },
                actions = {
                    Text(
                        text = "${vaultFiles.size} Secured",
                        color = Color(0xFF81C784),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF161A26))
            )
        },
        floatingActionButton = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.End
            ) {
                ExtendedFloatingActionButton(
                    text = { Text("Hide Photo", color = Color.White) },
                    icon = { Icon(Icons.Rounded.AddAPhoto, contentDescription = null, tint = Color.White) },
                    onClick = {
                        onAction(CalculatorAction.SetPickerActive(true))
                        photoLauncher.launch("image/*")
                    },
                    containerColor = Color(0xFFFF9F0A),
                    modifier = Modifier.testTag("hide_photo_fab")
                )
                ExtendedFloatingActionButton(
                    text = { Text("Hide Video", color = Color.White) },
                    icon = { Icon(Icons.Rounded.VideoCall, contentDescription = null, tint = Color.White) },
                    onClick = {
                        onAction(CalculatorAction.SetPickerActive(true))
                        videoLauncher.launch("video/*")
                    },
                    containerColor = Color(0xFF2196F3),
                    modifier = Modifier.testTag("hide_video_fab")
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Header Selection Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161A26))
            ) {
                TabButton(
                    text = "Photos (${photos.size})",
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    modifier = Modifier.weight(1f)
                )
                TabButton(
                    text = "Videos (${videos.size})",
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    modifier = Modifier.weight(1f)
                )
            }

            val currentItems = if (selectedTab == 0) photos else videos

            if (currentItems.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (selectedTab == 0) Icons.Rounded.PhotoLibrary else Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(80.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (selectedTab == 0) "No Hidden Photos" else "No Hidden Videos",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Import private files from device storage. They will be copied into `.hiddenfold` encrypted folders and can be completely deleted from publicly indexed media paths.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            } else {
                androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                    columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(3),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(currentItems.size) { index ->
                        val file = currentItems[index]
                        MediaGridCard(
                            file = file,
                            onClick = { activePreviewFile = file },
                            onRestore = { onAction(CalculatorAction.UnhideVaultFile(file)) },
                            onDelete = { onAction(CalculatorAction.DeleteVaultFile(file)) }
                        )
                    }
                }
            }
        }
        
        activePreviewFile?.let { file ->
            FullscreenPreviewOverlay(
                file = file,
                onClose = { activePreviewFile = null },
                onRestore = {
                    onAction(CalculatorAction.UnhideVaultFile(file))
                    activePreviewFile = null
                },
                onDelete = {
                    onAction(CalculatorAction.DeleteVaultFile(file))
                    activePreviewFile = null
                }
            )
        }
    }
}

@Composable
fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val barColor = if (selected) Color(0xFFFF9F0A) else Color.Transparent
    val textColor = if (selected) Color.White else Color.Gray

    Column(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 15.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .height(3.dp)
                .background(barColor, shape = RoundedCornerShape(1.5.dp))
        )
    }
}

@Composable
fun MediaGridCard(
    file: VaultFile,
    onClick: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var showDropdownMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF161A26))
            .clickable(onClick = onClick)
    ) {
        if (file.isVideo) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayCircleFilled,
                    contentDescription = "Video file player link",
                    tint = Color(0xFF2196F3),
                    modifier = Modifier.size(36.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xAA000000))
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = file.originalName ?: file.fileName,
                        color = Color.White,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            AsyncImage(
                model = File(file.filePath),
                contentDescription = "Private Image Frame",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(2.dp)
        ) {
            IconButton(
                onClick = { showDropdownMenu = true },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "Private File Actions",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }

            DropdownMenu(
                expanded = showDropdownMenu,
                onDismissRequest = { showDropdownMenu = false },
                modifier = Modifier.background(Color(0xFF1E2235))
            ) {
                DropdownMenuItem(
                    text = { Text("Restore (Unhide)", color = Color.White) },
                    onClick = {
                        onRestore()
                        showDropdownMenu = false
                    },
                    leadingIcon = { Icon(Icons.Rounded.Unarchive, contentDescription = null, tint = Color(0xFF81C784)) }
                )
                DropdownMenuItem(
                    text = { Text("Delete Permanently", color = Color(0xFFEF5350)) },
                    onClick = {
                        onDelete()
                        showDropdownMenu = false
                    },
                    leadingIcon = { Icon(Icons.Rounded.DeleteForever, contentDescription = null, tint = Color(0xFFEF5350)) }
                )
            }
        }
    }
}

@Composable
fun FullscreenPreviewOverlay(
    file: VaultFile,
    onClose: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFA090A10))
            .clickable(onClick = onClose)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close View",
                        tint = Color.White
                    )
                }
                
                Text(
                    text = file.originalName ?: file.fileName,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.width(48.dp))
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (file.isVideo) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            contentDescription = "Play Video link",
                            tint = Color(0xFFE0E0E0),
                            modifier = Modifier.size(96.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Secure Video Format",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Size: ${formatFileSize(file.size)}",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    AsyncImage(
                        model = File(file.filePath),
                        contentDescription = "Hidden Vault Photo content",
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Unarchive,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Unhide (Restore)", color = Color.White)
                }

                Button(
                    onClick = onDelete,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteForever,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Delete Forever", color = Color.White)
                }
            }
        }
    }
}

private fun formatFileSize(size: Long): String {
    if (size <= 0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB")
    var currentSize = size.toDouble()
    var unitIndex = 0
    while (currentSize >= 1024 && unitIndex < units.size - 1) {
        currentSize /= 1024
        unitIndex++
    }
    return String.format("%.2f %s", currentSize, units[unitIndex])
}
