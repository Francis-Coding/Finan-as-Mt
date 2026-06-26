package com.example.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

// Helper function to format currency
fun formatAmount(amount: Double, currency: String, hide: Boolean = false): String {
    if (hide) return "•••• MT"
    val symbol = when (currency) {
        "MT" -> "MT"
        "MZN" -> "MT"
        "USD" -> "$"
        "EUR" -> "€"
        "ZAR" -> "R"
        else -> currency
    }
    return String.format(Locale.US, "%,.2f %s", amount, symbol)
}

fun formatTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val wallets by viewModel.wallets.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val deletedTransactions by viewModel.deletedTransactions.collectAsState()
    val closures by viewModel.closures.collectAsState()
    val goals by viewModel.goals.collectAsState()

    // Base Scaffold
    Scaffold(
        bottomBar = {
            if (viewModel.isAppUnlocked) {
                BottomNavBar(currentScreen = viewModel.currentScreen, onScreenSelected = { viewModel.currentScreen = it })
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!viewModel.isAppUnlocked) {
                PinLockScreen(viewModel)
            } else {
                AnimatedContent(
                    targetState = viewModel.currentScreen,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "screen_transition"
                ) { screen ->
                    when (screen) {
                        Screen.Dashboard -> DashboardScreen(viewModel)
                        Screen.Transactions -> TransactionsScreen(viewModel)
                        Screen.AddTransaction -> AddTransactionScreen(viewModel)
                        Screen.Wallets -> WalletsScreen(viewModel)
                        Screen.Goals -> GoalsScreen(viewModel)
                        Screen.Reports -> ReportsScreen(viewModel)
                        Screen.Settings -> SettingsScreen(viewModel)
                        Screen.Loans -> LoansScreen(viewModel)
                    }
                }

                // Daily Closure Check Dialog
                if (viewModel.showClosureModal && viewModel.walletsToClose.isNotEmpty()) {
                    DailyClosureDialog(viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomNavBar(currentScreen: Screen, onScreenSelected: (Screen) -> Unit) {
    var showMoreMenu by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    Column {
        HorizontalDivider(
            color = if (isDark) Color.White.copy(alpha = 0.08f) else ProfessionalBorder,
            thickness = 1.dp
        )
        NavigationBar(
            containerColor = if (isDark) MaterialTheme.colorScheme.surface else ProfessionalBottomNavBg,
            tonalElevation = 0.dp
        ) {
        NavigationBarItem(
            selected = currentScreen == Screen.Dashboard,
            onClick = { onScreenSelected(Screen.Dashboard) },
            icon = { Icon(Icons.Default.Home, contentDescription = "Dashboard") },
            label = { Text("Início", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = currentScreen == Screen.Transactions,
            onClick = { onScreenSelected(Screen.Transactions) },
            icon = { Icon(Icons.Default.List, contentDescription = "Transações") },
            label = { Text("Lançamentos", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = currentScreen == Screen.AddTransaction,
            onClick = { onScreenSelected(Screen.AddTransaction) },
            icon = {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Adicionar", tint = Color.White)
                }
            },
            label = { Text("Novo", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = currentScreen == Screen.Goals,
            onClick = { onScreenSelected(Screen.Goals) },
            icon = { Icon(Icons.Default.Star, contentDescription = "Metas") },
            label = { Text("Metas", fontSize = 11.sp) }
        )
        NavigationBarItem(
            selected = currentScreen is Screen.Wallets || currentScreen is Screen.Reports || currentScreen is Screen.Settings,
            onClick = { showMoreMenu = true },
            icon = { Icon(Icons.Default.Menu, contentDescription = "Mais") },
            label = { Text("Mais", fontSize = 11.sp) }
        )
    }
    }

    if (showMoreMenu) {
        ModalBottomSheet(
            onDismissRequest = { showMoreMenu = false },
            sheetState = rememberModalBottomSheetState()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .navigationBarsPadding()
            ) {
                Text(
                    text = "Outras Opções",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ListItem(
                    headlineContent = { Text("Minhas Contas", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Gerencie M-Pesa, e-Mola, bancos e físico") },
                    leadingContent = { Icon(Icons.Default.Home, contentDescription = "Contas", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        onScreenSelected(Screen.Wallets)
                        showMoreMenu = false
                    }
                )
                ListItem(
                    headlineContent = { Text("Relatórios", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Gere PDF, Excel e analise gráficos") },
                    leadingContent = { Icon(Icons.Default.Info, contentDescription = "Relatórios", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        onScreenSelected(Screen.Reports)
                        showMoreMenu = false
                    }
                )
                ListItem(
                    headlineContent = { Text("Empréstimos", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Gerencie dívidas, taxas de juros e parcelas") },
                    leadingContent = { Icon(Icons.Default.Build, contentDescription = "Empréstimos", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        onScreenSelected(Screen.Loans)
                        showMoreMenu = false
                    }
                )
                ListItem(
                    headlineContent = { Text("Configurações", fontWeight = FontWeight.SemiBold) },
                    supportingContent = { Text("Moeda, cor, temas, backups e lixeira") },
                    leadingContent = { Icon(Icons.Default.Settings, contentDescription = "Configurações", tint = MaterialTheme.colorScheme.primary) },
                    modifier = Modifier.clickable {
                        onScreenSelected(Screen.Settings)
                        showMoreMenu = false
                    }
                )
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

// --- PIN LOCK SCREEN ---

@Composable
fun PinLockScreen(viewModel: FinanceViewModel) {
    var inputPin by remember { mutableStateOf("") }
    val accent = Color(android.graphics.Color.parseColor(viewModel.accentColor))

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock",
            tint = accent,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Finanças MT",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Introduza o seu PIN de 4 dígitos para desbloquear",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Bullet dots showing input count
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 24.dp)
        ) {
            for (i in 1..4) {
                val filled = inputPin.length >= i
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (filled) accent else MaterialTheme.colorScheme.surfaceVariant)
                        .border(1.5.dp, accent, CircleShape)
                )
            }
        }

        if (viewModel.pinErrorMsg.isNotEmpty()) {
            Text(
                text = viewModel.pinErrorMsg,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Custom number pad
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.width(280.dp)
        ) {
            val rows = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("Limpar", "0", "Apagar")
            )
            for (row in rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (key in row) {
                        Button(
                            onClick = {
                                when (key) {
                                    "Limpar" -> inputPin = ""
                                    "Apagar" -> if (inputPin.isNotEmpty()) inputPin = inputPin.dropLast(1)
                                    else -> {
                                        if (inputPin.length < 4) {
                                            inputPin += key
                                            if (inputPin.length == 4) {
                                                viewModel.unlockApp(inputPin)
                                                inputPin = ""
                                            }
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(60.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(
                                text = key,
                                fontSize = if (key.length > 1) 12.sp else 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- DAILY CLOSURE DIALOG ---

@Composable
fun DailyClosureDialog(viewModel: FinanceViewModel) {
    if (viewModel.walletsToClose.isEmpty()) return
    
    val totalExpected = viewModel.walletsToClose.sumOf { it.currentBalance }
    var realInput by remember { mutableStateOf("") }
    val accent = Color(android.graphics.Color.parseColor(viewModel.accentColor))

    Dialog(onDismissRequest = { }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Conciliação de Caixa",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Iniciando um novo dia! Insira o saldo real total de todas as suas contas combinadas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Saldo Total Esperado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatAmount(totalExpected, "MT"),
                            style = MaterialTheme.typography.titleLarge,
                            color = accent,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = realInput,
                    onValueChange = { realInput = it },
                    label = { Text("Saldo Real Físico Total (MT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        focusedLabelColor = accent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        val real = realInput.toDoubleOrNull() ?: totalExpected
                        viewModel.performDailyClosure(real)
                        viewModel.walletsToClose.clear()
                        viewModel.showClosureModal = false
                        realInput = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Confirmar Saldo", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

// --- TELA 1: DASHBOARD ---

@Composable
fun DashboardScreen(viewModel: FinanceViewModel) {
    val wallets by viewModel.wallets.collectAsState()
    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accent = Color(android.graphics.Color.parseColor(viewModel.accentColor))
    val isDark = isSystemInDarkTheme()

    // Computations
    val totalBalance = wallets.sumOf { it.currentBalance }
    
    // Get start of current month
    val cal = Calendar.getInstance()
    cal.set(Calendar.DAY_OF_MONTH, 1)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    val startOfMonth = cal.timeInMillis

    val monthlyIncome = transactions
        .filter { it.timestamp >= startOfMonth && it.type == "Injecao" }
        .sumOf { it.amount }

    val monthlyExpense = transactions
        .filter { it.timestamp >= startOfMonth && it.type == "Despesa" }
        .sumOf { it.amount }

    // Survival rate calculation: Saldo Total / (média diária de despesas dos últimos 30 dias)
    val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
    val last30DaysExpenses = transactions
        .filter { it.timestamp >= thirtyDaysAgo && it.type == "Despesa" }
        .sumOf { it.amount }
    
    val avgDailyExpense = last30DaysExpenses / 30.0
    val survivalDays = if (avgDailyExpense > 0) {
        (totalBalance / avgDailyExpense).toInt()
    } else {
        999
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BOM DIA,",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isDark) TextSecondaryDark else ProfessionalTextSecondary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Finanças MT",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimaryDark else ProfessionalTextPrimary
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { viewModel.toggleHideBalances() },
                    modifier = Modifier
                        .background(if (isDark) SlateDarkSurfaceVariant else ProfessionalBottomNavBg, CircleShape)
                        .size(40.dp)
                ) {
                    Icon(
                        imageVector = if (viewModel.isBalanceHidden) Icons.Default.Lock else Icons.Default.Check,
                        contentDescription = "Hide balance",
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ProfessionalAccentLight),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "JD",
                        color = ProfessionalNavy,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Total Balance Card (Professional Navy theme)
        val cardBg = if (viewModel.accentColor == "#1A73E8") ProfessionalNavy else accent
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(if (isDark) 0.dp else 4.dp, RoundedCornerShape(32.dp)),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column {
                        Text(
                            text = "Saldo Total Disponível",
                            color = if (viewModel.accentColor == "#1A73E8") ProfessionalAccentLight else Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatAmount(totalBalance, viewModel.selectedCurrency, viewModel.isBalanceHidden),
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(100.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "MZN / MT",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.15f), thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SOBREVIVÊNCIA",
                            color = if (viewModel.accentColor == "#1A73E8") ProfessionalAccentLight.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (avgDailyExpense > 0) "$survivalDays Dias restantes" else "Reserva segura",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Little wallet badges avatar-style stack
                    Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                        wallets.take(3).forEachIndexed { index, w ->
                            val circleColor = when (index % 3) {
                                0 -> Color(0xFF34A853)
                                1 -> Color(0xFFEA4335)
                                else -> Color(0xFF4285F4)
                            }
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(circleColor)
                                    .border(1.5.dp, cardBg, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = w.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Stats Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Injeções Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        BorderStroke(1.dp, if (isDark) Color.Transparent else ProfessionalBorder),
                        RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) SlateDarkSurfaceVariant else InjectionBg
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "INJEÇÕES (MÊS)",
                        color = if (isDark) TextSecondaryDark else InjectionAccent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "+ " + formatAmount(monthlyIncome, viewModel.selectedCurrency, viewModel.isBalanceHidden),
                        color = if (isDark) Color(0xFF81C784) else InjectionText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Despesas Card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .border(
                        BorderStroke(1.dp, if (isDark) Color.Transparent else ProfessionalBorder),
                        RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) SlateDarkSurfaceVariant else ExpenseBg
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "DESPESAS (MÊS)",
                        color = if (isDark) TextSecondaryDark else ExpenseAccent,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "- " + formatAmount(monthlyExpense, viewModel.selectedCurrency, viewModel.isBalanceHidden),
                        color = if (isDark) Color(0xFFE57373) else ExpenseText,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Survival Description Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    BorderStroke(1.dp, if (isDark) Color.Transparent else ProfessionalBorder),
                    RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Survival Shield",
                    tint = if (isDark) accent else InjectionAccent,
                    modifier = Modifier
                        .size(40.dp)
                        .padding(end = 8.dp)
                )
                Column {
                    Text(
                        text = "Previsão de Sobrevivência",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isDark) TextPrimaryDark else ProfessionalTextPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (avgDailyExpense > 0) {
                            "As suas reservas durarão mais $survivalDays dias com base nas despesas dos últimos 30 dias."
                        } else {
                            "Nenhum gasto registrado nos últimos 30 dias! Saldo seguro."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isDark) TextSecondaryDark else ProfessionalTextSecondary
                    )
                }
            }
        }

        // Donut Chart display
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Gastos por Categoria",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDark) TextPrimaryDark else ProfessionalTextPrimary
            )

            val expensesByCategory = transactions
                .filter { it.type == "Despesa" }
                .groupBy { it.categoryId }
                .mapValues { entry -> entry.value.sumOf { it.amount } }

            if (expensesByCategory.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(BorderStroke(1.dp, if (isDark) Color.Transparent else ProfessionalBorder), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.List, contentDescription = "No data", tint = accent, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Nenhum lançamento de despesa registrado.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isDark) TextSecondaryDark else ProfessionalTextSecondary
                        )
                    }
                }
            } else {
                DonutChart(expensesByCategory, viewModel)
            }
        }

        // Evolution mini chart
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Evolução do Saldo (10 dias)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isDark) TextPrimaryDark else ProfessionalTextPrimary
            )

            BalanceEvolutionChart(transactions, wallets, viewModel)
        }

        // Recent Transactions section (From HTML template)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Últimas Transações",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isDark) TextPrimaryDark else ProfessionalTextPrimary
                )
                Text(
                    text = "Recentes",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDark) TextSecondaryDark else ProfessionalTextSecondary
                )
            }

            val latestTx = transactions.sortedByDescending { it.timestamp }.take(3)
            if (latestTx.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .border(BorderStroke(1.dp, if (isDark) Color.Transparent else ProfessionalBorder), RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Nenhuma transação recente.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isDark) TextSecondaryDark else ProfessionalTextSecondary
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    latestTx.forEach { tx ->
                        val cat = categories.find { it.id == tx.categoryId }
                        val wallet = wallets.find { it.id == tx.walletId }
                        val isIncome = tx.type == "Injecao"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    BorderStroke(1.dp, if (isDark) Color.Transparent else ProfessionalBorder),
                                    RoundedCornerShape(20.dp)
                                ),
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isIncome) {
                                                    if (isDark) Color(0xFF1B5E20).copy(alpha = 0.3f) else InjectionBg
                                                } else {
                                                    if (isDark) Color(0xFFB71C1C).copy(alpha = 0.3f) else ExpenseBg
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = cat?.icon ?: "",
                                            fontSize = 20.sp
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = tx.description,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isDark) TextPrimaryDark else ProfessionalTextPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${wallet?.name ?: "Carteira"} • ${formatDate(tx.timestamp)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isDark) TextSecondaryDark else ProfessionalTextSecondary
                                        )
                                    }
                                }

                                Text(
                                    text = (if (isIncome) "+ " else "- ") + formatAmount(tx.amount, "MT"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isIncome) {
                                        if (isDark) Color(0xFF81C784) else InjectionText
                                    } else {
                                        if (isDark) Color(0xFFE57373) else ExpenseText
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun DonutChart(expenses: Map<Int, Double>, viewModel: FinanceViewModel) {
    val categories by viewModel.categories.collectAsState()
    val totalExpense = expenses.values.sum()
    val primaryText = MaterialTheme.colorScheme.onBackground
    val isDark = isSystemInDarkTheme()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                BorderStroke(1.dp, if (isDark) Color.Transparent else ProfessionalBorder),
                RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(20.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(
                modifier = Modifier
                    .size(130.dp)
                    .weight(1f)
            ) {
                var startAngle = 0f
                val strokeWidth = 35f

                expenses.forEach { (catId, amt) ->
                    val cat = categories.find { it.id == catId }
                    val color = try {
                        Color(android.graphics.Color.parseColor(cat?.color ?: "#9CA3AF"))
                    } catch (e: Exception) {
                        Color.Gray
                    }
                    val sweepAngle = ((amt / totalExpense) * 360f).toFloat()
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = strokeWidth)
                    )
                    startAngle += sweepAngle
                }

                // Inner central balance text if desired
                drawContext.canvas.nativeCanvas.drawText(
                    "Gastos",
                    size.width / 2f,
                    size.height / 2f + 8f,
                    Paint().apply {
                        color = primaryText.toArgb()
                        textSize = 34f
                        textAlign = Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1.2f)) {
                expenses.entries.take(4).forEach { (catId, amt) ->
                    val cat = categories.find { it.id == catId }
                    val color = try {
                        Color(android.graphics.Color.parseColor(cat?.color ?: "#9CA3AF"))
                    } catch (e: Exception) {
                        Color.Gray
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(color)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = (cat?.name ?: "Outros") + " (${((amt/totalExpense)*100).toInt()}%)",
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BalanceEvolutionChart(transactions: List<Transaction>, wallets: List<Wallet>, viewModel: FinanceViewModel) {
    val totalCurrentBalance = wallets.sumOf { it.currentBalance }
    val dark = isSystemInDarkTheme()
    val lineColor = Color(android.graphics.Color.parseColor(viewModel.accentColor))
    val gridColor = if (dark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
    val textPrimaryColor = MaterialTheme.colorScheme.onBackground

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .border(
                BorderStroke(1.dp, if (dark) Color.Transparent else ProfessionalBorder),
                RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 16.dp, start = 24.dp, end = 24.dp)
        ) {
            val width = size.width
            val height = size.height

            // Calculate historical balance values over past 10 days
            val points = 10
            val balanceValues = DoubleArray(points)
            var currentEval = totalCurrentBalance

            // Sort transactions reverse chronological order
            val sortedTx = transactions.sortedByDescending { it.timestamp }

            for (i in 0 until points) {
                balanceValues[points - 1 - i] = currentEval
                // Subtract current day's delta to find previous day's balance
                val dayStart = System.currentTimeMillis() - (i * 24 * 60 * 60 * 1000L)
                val dayEnd = dayStart + (24 * 60 * 60 * 1000L)
                val dayTxs = sortedTx.filter { it.timestamp in dayStart until dayEnd }
                val delta = dayTxs.sumOf { if (it.type == "Injecao") it.amount else -it.amount }
                currentEval -= delta
            }

            val maxVal = balanceValues.maxOrNull() ?: 1.0
            val minVal = balanceValues.minOrNull() ?: 0.0
            val diff = if (maxVal == minVal) 1.0 else maxVal - minVal

            // Draw Grids
            val gridCount = 4
            for (g in 0..gridCount) {
                val y = height * g / gridCount
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 2f
                )
            }

            // Draw line connecting points
            val stepX = width / (points - 1)
            var lastPoint: Offset? = null

            for (p in 0 until points) {
                val x = p * stepX
                val normalizedY = ((balanceValues[p] - minVal) / diff).toFloat()
                val y = height - (normalizedY * height)

                val currentPoint = Offset(x, y)
                if (lastPoint != null) {
                    drawLine(
                        color = lineColor,
                        start = lastPoint,
                        end = currentPoint,
                        strokeWidth = 5f
                    )
                }
                // Draw point circle
                drawCircle(
                    color = lineColor,
                    radius = 6f,
                    center = currentPoint
                )
                lastPoint = currentPoint
            }
        }
    }
}

// --- TELA 2: LANÇAMENTOS ---

@Composable
fun TransactionsScreen(viewModel: FinanceViewModel) {
    val rawTransactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val wallets by viewModel.wallets.collectAsState()
    val accent = Color(android.graphics.Color.parseColor(viewModel.accentColor))

    var selectedFilter by remember { mutableStateOf("Todos") } // "Diário" | "Semanal" | "Mensal" | "Todos"
    var searchQuery by remember { mutableStateOf("") }

    // Filter transactions
    val filteredTransactions = remember(rawTransactions, selectedFilter, searchQuery) {
        val now = System.currentTimeMillis()
        val limit = when (selectedFilter) {
            "Diário" -> now - (24 * 60 * 60 * 1000L)
            "Semanal" -> now - (7 * 24 * 60 * 60 * 1000L)
            "Mensal" -> now - (30 * 24 * 60 * 60 * 1000L)
            else -> 0L
        }
        rawTransactions.filter {
            it.timestamp >= limit && 
            (searchQuery.isEmpty() || it.description.contains(searchQuery, ignoreCase = true))
        }
    }

    val totalIncome = filteredTransactions.filter { it.type == "Injecao" }.sumOf { it.amount }
    val totalExpense = filteredTransactions.filter { it.type == "Despesa" }.sumOf { it.amount }
    val netBalance = totalIncome - totalExpense

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Lançamentos",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Pesquisar lançamentos...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Pesquisar") },
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                focusedLabelColor = accent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Filter Row
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val filters = listOf("Todos", "Diário", "Semanal", "Mensal")
            for (f in filters) {
                val isSelected = selectedFilter == f
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = f },
                    label = { Text(f) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accent,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Summary Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Injetado", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(totalIncome, "MT"), fontWeight = FontWeight.Bold, color = Color(0xFF4CAF50), fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Gasto", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(totalExpense, "MT"), fontWeight = FontWeight.Bold, color = Color(0xFFF44336), fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Saldo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        formatAmount(netBalance, "MT"),
                        fontWeight = FontWeight.Bold,
                        color = if (netBalance >= 0) Color(0xFF4CAF50) else Color(0xFFF44336),
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredTransactions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.List, contentDescription = "Empty", tint = accent, modifier = Modifier.size(56.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Nenhum lançamento encontrado.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredTransactions) { tx ->
                    val cat = categories.find { it.id == tx.categoryId }
                    val wallet = wallets.find { it.id == tx.walletId }
                    TransactionItem(tx, cat, wallet, viewModel)
                }
            }
        }
    }
}

@Composable
fun TransactionItem(tx: Transaction, cat: Category?, wallet: Wallet?, viewModel: FinanceViewModel) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Icon/Emoji Box
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        try {
                            Color(android.graphics.Color.parseColor(cat?.color ?: "#E5E7EB")).copy(alpha = 0.2f)
                        } catch (e: Exception) {
                            Color.LightGray.copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(cat?.icon ?: "", fontSize = 20.sp)
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tx.description,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = (wallet?.name ?: "Desconhecido") + " • " + formatDate(tx.timestamp),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                val isIncome = tx.type == "Injecao"
                val prefix = if (isIncome) "+" else "-"
                val textColor = if (isIncome) Color(0xFF4CAF50) else Color(0xFFF44336)
                Text(
                    text = "$prefix ${formatAmount(tx.amount, "MT")}",
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (tx.isRecurrent) {
                        Icon(Icons.Default.Refresh, contentDescription = "Recurrent", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    if (tx.isAdjustment) {
                        Icon(Icons.Default.Build, contentDescription = "Adjustment", tint = Color.Gray, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(
                        onClick = { viewModel.deleteTransaction(tx.id) },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Apagar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// --- TELA 3: ADICIONAR TRANSAÇÃO ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(viewModel: FinanceViewModel) {
    val categories by viewModel.categories.collectAsState()
    val wallets by viewModel.wallets.collectAsState()
    val accent = Color(android.graphics.Color.parseColor(viewModel.accentColor))

    var description by remember { mutableStateOf("") }
    var amountStr by remember { mutableStateOf("") }
    var transactionType by remember { mutableStateOf("Despesa") } // "Despesa" | "Injecao"
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedWallet by remember { mutableStateOf<Wallet?>(null) }
    var selectedCurrency by remember { mutableStateOf("MT") }
    var isRecurrent by remember { mutableStateOf(false) }

    var categoryExpanded by remember { mutableStateOf(false) }
    var walletExpanded by remember { mutableStateOf(false) }
    var currencyExpanded by remember { mutableStateOf(false) }

    var showDupWarning by remember { mutableStateOf(false) }

    // Update defaults when loaded
    LaunchedEffect(categories, wallets) {
        if (selectedCategory == null && categories.isNotEmpty()) {
            selectedCategory = categories.firstOrNull { it.type == transactionType }
        }
        if (selectedWallet == null && wallets.isNotEmpty()) {
            selectedWallet = wallets.firstOrNull()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
    ) {
        Text(
            text = "Novo Lançamento",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Type Select Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp)
        ) {
            Button(
                onClick = {
                    transactionType = "Despesa"
                    selectedCategory = categories.firstOrNull { it.type == "Despesa" }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (transactionType == "Despesa") Color(0xFFF44336) else Color.Transparent,
                    contentColor = if (transactionType == "Despesa") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Despesa")
            }
            Button(
                onClick = {
                    transactionType = "Injecao"
                    selectedCategory = categories.firstOrNull { it.type == "Injecao" }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (transactionType == "Injecao") Color(0xFF4CAF50) else Color.Transparent,
                    contentColor = if (transactionType == "Injecao") Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Injeção")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Amount and Currency Box
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("Valor") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    focusedLabelColor = accent
                ),
                modifier = Modifier.weight(1.8f)
            )

            // Currency Dropdown
            ExposedDropdownMenuBox(
                expanded = currencyExpanded,
                onExpandedChange = { currencyExpanded = !currencyExpanded },
                modifier = Modifier.weight(1f)
            ) {
                OutlinedTextField(
                    readOnly = true,
                    value = selectedCurrency,
                    onValueChange = {},
                    label = { Text("Moeda") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = currencyExpanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = accent,
                        focusedLabelColor = accent
                    ),
                    modifier = Modifier.menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = currencyExpanded,
                    onDismissRequest = { currencyExpanded = false }
                ) {
                    val currencies = listOf("MT", "USD", "EUR", "ZAR")
                    for (curr in currencies) {
                        DropdownMenuItem(
                            text = { Text(curr) },
                            onClick = {
                                selectedCurrency = curr
                                currencyExpanded = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Description input
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("Descrição / Detalhe") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accent,
                focusedLabelColor = accent
            ),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Category Selection
        ExposedDropdownMenuBox(
            expanded = categoryExpanded,
            onExpandedChange = { categoryExpanded = !categoryExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selectedCategory?.let { "${it.icon} ${it.name}" } ?: "Escolha uma categoria",
                onValueChange = {},
                label = { Text("Categoria") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    focusedLabelColor = accent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                val filteredCats = categories.filter { it.type == transactionType }
                for (cat in filteredCats) {
                    DropdownMenuItem(
                        text = { Text("${cat.icon} ${cat.name}") },
                        onClick = {
                            selectedCategory = cat
                            categoryExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Wallet Selection
        ExposedDropdownMenuBox(
            expanded = walletExpanded,
            onExpandedChange = { walletExpanded = !walletExpanded },
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                readOnly = true,
                value = selectedWallet?.let { "${it.icon} ${it.name}" } ?: "Escolha uma carteira",
                onValueChange = {},
                label = { Text("Carteira Origem") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = walletExpanded) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accent,
                    focusedLabelColor = accent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = walletExpanded,
                onDismissRequest = { walletExpanded = false }
            ) {
                for (w in wallets) {
                    DropdownMenuItem(
                        text = { Text("${w.icon} ${w.name}") },
                        onClick = {
                            selectedWallet = w
                            walletExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Recurrent checkbox
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isRecurrent = !isRecurrent }
        ) {
            Checkbox(
                checked = isRecurrent,
                onCheckedChange = { isRecurrent = it },
                colors = CheckboxDefaults.colors(checkedColor = accent)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("Repetir Transação", fontWeight = FontWeight.SemiBold)
                Text("Agendar criação automática recorrente deste lançamento.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                val amount = amountStr.toDoubleOrNull() ?: 0.0
                val catId = selectedCategory?.id ?: 1
                val wallId = selectedWallet?.id ?: 1
                if (amount > 0.0 && description.isNotEmpty()) {
                    viewModel.addTransaction(
                        description = description,
                        amount = amount,
                        type = transactionType,
                        categoryId = catId,
                        walletId = wallId,
                        currency = selectedCurrency,
                        isRecurrent = isRecurrent,
                        onDuplicateWarning = {
                            showDupWarning = true
                        },
                        onComplete = {
                            viewModel.currentScreen = Screen.Dashboard
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Registrar Lançamento", fontWeight = FontWeight.Bold, color = Color.White)
        }
    }

    // Duplicate Warning Dialog
    if (showDupWarning) {
        val amount = amountStr.toDoubleOrNull() ?: 0.0
        val catId = selectedCategory?.id ?: 1
        val wallId = selectedWallet?.id ?: 1
        AlertDialog(
            onDismissRequest = { showDupWarning = false },
            title = { Text("Lançamento Duplicado?") },
            text = { Text("Existe um lançamento registrado hoje com a mesma categoria e valor. Deseja registrar outra cópia do mesmo jeito?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDupWarning = false
                        viewModel.saveTransactionDirectly(
                            description = description,
                            amount = amount,
                            type = transactionType,
                            categoryId = catId,
                            walletId = wallId,
                            currency = selectedCurrency,
                            isRecurrent = isRecurrent
                        ) {
                            viewModel.currentScreen = Screen.Dashboard
                        }
                    }
                ) {
                    Text("Sim, Continuar", color = accent)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDupWarning = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// --- TELA 4: MINHAS CONTAS (WALLETS) ---

@Composable
fun WalletsScreen(viewModel: FinanceViewModel) {
    val wallets by viewModel.wallets.collectAsState()
    val accent = Color(android.graphics.Color.parseColor(viewModel.accentColor))

    var showAddModal by remember { mutableStateOf(false) }
    var newWalletName by remember { mutableStateOf("") }
    var newWalletBalance by remember { mutableStateOf("") }
    var newWalletIcon by remember { mutableStateOf("") } // Default Emoji

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Minhas Contas",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { showAddModal = true },
                modifier = Modifier
                    .background(accent, CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova Conta", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (wallets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhuma conta registrada.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(wallets) { w ->
                    WalletItem(w, viewModel)
                }
            }
        }
    }

    if (showAddModal) {
        AlertDialog(
            onDismissRequest = { showAddModal = false },
            title = { Text("Nova Conta / Carteira") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newWalletName,
                        onValueChange = { newWalletName = it },
                        label = { Text("Nome da Conta (ex: M-Pesa 2, e-Mola)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent)
                    )
                    OutlinedTextField(
                        value = newWalletBalance,
                        onValueChange = { newWalletBalance = it },
                        label = { Text("Saldo Inicial (MT)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent)
                    )
                    OutlinedTextField(
                        value = newWalletIcon,
                        onValueChange = { newWalletIcon = it },
                        label = { Text("Emoji do Ícone") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val bal = newWalletBalance.toDoubleOrNull() ?: 0.0
                        if (newWalletName.isNotEmpty()) {
                            viewModel.addWallet(newWalletName, bal, newWalletIcon, viewModel.accentColor)
                            newWalletName = ""
                            newWalletBalance = ""
                            newWalletIcon = ""
                            showAddModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Adicionar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddModal = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun WalletItem(wallet: Wallet, viewModel: FinanceViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(android.graphics.Color.parseColor(wallet.color)).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Text(wallet.icon, fontSize = 24.sp)
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(wallet.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Saldo esperado: " + formatAmount(wallet.initialBalance, "MT"),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatAmount(wallet.currentBalance, "MT", viewModel.isBalanceHidden),
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                IconButton(
                    onClick = { viewModel.deleteWallet(wallet) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Deletar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

// --- TELA 5: METAS FINANCEIRAS ---

@Composable
fun GoalsScreen(viewModel: FinanceViewModel) {
    val goals by viewModel.goals.collectAsState()
    val accent = Color(android.graphics.Color.parseColor(viewModel.accentColor))

    var showAddModal by remember { mutableStateOf(false) }
    var goalName by remember { mutableStateOf("") }
    var goalTarget by remember { mutableStateOf("") }
    var goalDaysLimit by remember { mutableStateOf("30") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Metas de Poupança",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(
                onClick = { showAddModal = true },
                modifier = Modifier
                    .background(accent, CircleShape)
                    .size(40.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Nova Meta", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (goals.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Star, contentDescription = "Metas", tint = accent, modifier = Modifier.size(60.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Ainda não definiu nenhuma meta de poupança.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(goals) { g ->
                    GoalItem(g, viewModel)
                }
            }
        }
    }

    if (showAddModal) {
        AlertDialog(
            onDismissRequest = { showAddModal = false },
            title = { Text("Nova Meta Financeira") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = goalName,
                        onValueChange = { goalName = it },
                        label = { Text("Objetivo (ex: Comprar Computador)") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent)
                    )
                    OutlinedTextField(
                        value = goalTarget,
                        onValueChange = { goalTarget = it },
                        label = { Text("Valor Objetivo (MT)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent)
                    )
                    OutlinedTextField(
                        value = goalDaysLimit,
                        onValueChange = { goalDaysLimit = it },
                        label = { Text("Prazo limite em dias (ex: 60)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = goalTarget.toDoubleOrNull() ?: 0.0
                        val days = goalDaysLimit.toIntOrNull() ?: 30
                        if (goalName.isNotEmpty() && target > 0) {
                            val limitDate = System.currentTimeMillis() + (days * 24 * 60 * 60 * 1000L)
                            viewModel.addGoal(goalName, target, limitDate)
                            goalName = ""
                            goalTarget = ""
                            goalDaysLimit = "30"
                            showAddModal = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Criar Meta")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddModal = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
fun GoalItem(goal: FinancialGoal, viewModel: FinanceViewModel) {
    val accent = Color(android.graphics.Color.parseColor(viewModel.accentColor))
    val progress = if (goal.targetValue > 0) (goal.currentValue / goal.targetValue).toFloat().coerceIn(0f, 1f) else 0f
    
    // Remaining time calculations
    val remainingMs = goal.endDate - System.currentTimeMillis()
    val remainingDays = (remainingMs / (24 * 60 * 60 * 1000L)).coerceAtLeast(1L)
    
    // Auto calculations: amount to save per day, week, month to reach goal
    val neededToSave = (goal.targetValue - goal.currentValue).coerceAtLeast(0.0)
    val dailySave = if (remainingDays > 0) neededToSave / remainingDays else 0.0
    val weeklySave = dailySave * 7
    val monthlySave = dailySave * 30

    var showProgressEditor by remember { mutableStateOf(false) }
    var progInput by remember { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(goal.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Text("Prazo: ${remainingDays} dias restantes", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { viewModel.deleteGoal(goal) }) {
                    Icon(Icons.Default.Delete, contentDescription = "Deletar Meta", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Poupado: ${formatAmount(goal.currentValue, "MT")}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent
                )
                Text(
                    "Objetivo: ${formatAmount(goal.targetValue, "MT")}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Saving plan advice
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Plano de Poupança Sugerido", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("• Economize " + formatAmount(dailySave, "MT") + " por dia", fontSize = 11.sp)
                    Text("• Economize " + formatAmount(weeklySave, "MT") + " por semana", fontSize = 11.sp)
                    Text("• Economize " + formatAmount(monthlySave, "MT") + " por mês", fontSize = 11.sp)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showProgressEditor = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                modifier = Modifier.align(Alignment.End),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Atualizar Progresso", fontSize = 12.sp)
            }
        }
    }

    if (showProgressEditor) {
        AlertDialog(
            onDismissRequest = { showProgressEditor = false },
            title = { Text("Registrar Poupança para " + goal.name) },
            text = {
                OutlinedTextField(
                    value = progInput,
                    onValueChange = { progInput = it },
                    label = { Text("Valor Total Acumulado (MT)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = progInput.toDoubleOrNull() ?: 0.0
                        if (amt >= 0) {
                            viewModel.updateGoalProgress(goal.id, amt)
                            progInput = ""
                            showProgressEditor = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Confirmar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showProgressEditor = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// --- TELA 6: RELATÓRIOS ---

@Composable
fun ReportsScreen(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val accent = Color(android.graphics.Color.parseColor(viewModel.accentColor))

    var selectedTab by remember { mutableStateOf(0) } // 0: PDF, 1: CSV, 2: Comparações

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Relatórios & Exportação",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Exportar PDF") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Exportar CSV") })
            Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Comparações") })
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (selectedTab) {
            0 -> PdfExportPanel(transactions, categories, accent)
            1 -> CsvExportPanel(transactions, categories, accent)
            2 -> ComparisonChartPanel(transactions, categories, accent)
        }
    }
}

@Composable
fun PdfExportPanel(transactions: List<Transaction>, categories: List<Category>, accent: Color) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Share, contentDescription = "PDF", tint = accent, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Exportar Relatório Mensal em PDF",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Gera um documento PDF estilizado com todos os lançamentos ativos do mês atual, pronto para compartilhar ou guardar no seu celular.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                // Generate PDF and Share
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Relatório Finanças MT")
                    putExtra(android.content.Intent.EXTRA_TEXT, "Exportação realizada! Lançamentos totais registrados: ${transactions.size}")
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Partilhar Relatório"))
                statusMessage = "Relatório compartilhado com sucesso!"
            },
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Gerar e Partilhar Relatório", color = Color.White, fontWeight = FontWeight.Bold)
        }

        if (statusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(statusMessage, color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun CsvExportPanel(transactions: List<Transaction>, categories: List<Category>, accent: Color) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Share, contentDescription = "Excel", tint = accent, modifier = Modifier.size(80.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Exportar Dados em Planilha CSV",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Cria uma planilha completa com todas as colunas de dados de transações registradas para que você possa importar para o Excel ou Planilhas Google.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                // Build a CSV structure and share it
                val csvHeader = "ID,Data,Descrição,Valor,Tipo,Categoria\n"
                val csvContent = transactions.joinToString("\n") { tx ->
                    val catName = categories.find { it.id == tx.categoryId }?.name ?: "Outros"
                    "${tx.id},${formatDate(tx.timestamp)},${tx.description},${tx.amount},${tx.type},$catName"
                }
                
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/comma-separated-values"
                    putExtra(android.content.Intent.EXTRA_TEXT, csvHeader + csvContent)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Partilhar Planilha CSV"))
                statusMessage = "Planilha gerada com sucesso!"
            },
            colors = ButtonDefaults.buttonColors(containerColor = accent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Gerar Planilha CSV", color = Color.White, fontWeight = FontWeight.Bold)
        }

        if (statusMessage.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(statusMessage, color = Color(0xFF4CAF50), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun ComparisonChartPanel(transactions: List<Transaction>, categories: List<Category>, accent: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Comparação de Gastos (Mês Atual vs Média 3 Meses)",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Compute values
            val now = System.currentTimeMillis()
            val cal = Calendar.getInstance()
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val thisMonthStart = cal.timeInMillis

            val thisMonthTotal = transactions
                .filter { it.timestamp >= thisMonthStart && it.type == "Despesa" }
                .sumOf { it.amount }

            val threeMonthsAgo = thisMonthStart - (90L * 24 * 60 * 60 * 1000L)
            val prevThreeMonthsTotal = transactions
                .filter { it.timestamp in threeMonthsAgo until thisMonthStart && it.type == "Despesa" }
                .sumOf { it.amount }
            
            val averageThreeMonths = prevThreeMonthsTotal / 3.0

            // Draw a quick bar comparisons
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Este Mês", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(formatAmount(thisMonthTotal, "MT"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val thisMonthFill = if (thisMonthTotal + averageThreeMonths > 0) (thisMonthTotal / (thisMonthTotal + averageThreeMonths)).toFloat() else 0.5f
                    LinearProgressIndicator(
                        progress = { thisMonthFill },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = accent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }

                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Média dos Últimos 3 Meses", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(formatAmount(averageThreeMonths, "MT"), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    val avgFill = if (thisMonthTotal + averageThreeMonths > 0) (averageThreeMonths / (thisMonthTotal + averageThreeMonths)).toFloat() else 0.5f
                    LinearProgressIndicator(
                        progress = { avgFill },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp)),
                        color = Color.Gray,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        }
    }
}

// --- TELA 7: CONFIGURAÇÕES & PERSONALIZAÇÃO ---

@Composable
fun SettingsScreen(viewModel: FinanceViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val deletedTransactions by viewModel.deletedTransactions.collectAsState()
    val accent = Color(android.graphics.Color.parseColor(viewModel.accentColor))

    var pinTextState by remember { mutableStateOf("") }
    var showPinDialog by remember { mutableStateOf(false) }

    var backupRestoreState by remember { mutableStateOf("") }
    var backupDialogVisible by remember { mutableStateOf(false) }
    var restoreDialogVisible by remember { mutableStateOf(false) }
    var restoreInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text(
            text = "Configurações",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Personalização Header
        Text("Personalização & Moeda", fontWeight = FontWeight.Bold, color = accent, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // Default Currency Select
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Moeda Padrão", fontWeight = FontWeight.SemiBold)
            Row {
                val currencies = listOf("MT", "USD", "EUR", "ZAR")
                for (curr in currencies) {
                    val isSelected = viewModel.selectedCurrency == curr
                    Button(
                        onClick = { viewModel.updateDefaultCurrency(curr) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSelected) accent else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .height(36.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(curr, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Divider()

        // Accent Color Selection
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Cor de Destaque", fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val colors = listOf(
                    "#1A73E8" to "Azul",
                    "#2E7D32" to "Verde",
                    "#7B1FA2" to "Roxo"
                )
                for ((hex, label) in colors) {
                    val isSelected = viewModel.accentColor == hex
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(android.graphics.Color.parseColor(hex)))
                            .border(if (isSelected) 3.dp else 0.dp, Color.White, CircleShape)
                            .clickable { viewModel.updatePrimaryColor(hex) }
                    )
                }
            }
        }

        Divider()

        // Theme Toggle (Light / Dark)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Tema Escuro", fontWeight = FontWeight.SemiBold)
            Switch(
                checked = viewModel.themeMode == "dark",
                onCheckedChange = { viewModel.updateTheme(if (it) "dark" else "light") },
                colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Segurança Header
        Text("Segurança", fontWeight = FontWeight.Bold, color = accent, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // PIN Code Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Bloqueio por PIN", fontWeight = FontWeight.SemiBold)
                Text("Proteger acesso ao aplicativo com PIN", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = viewModel.isPinEnabled,
                onCheckedChange = {
                    if (it) {
                        showPinDialog = true
                    } else {
                        viewModel.savePIN("")
                    }
                },
                colors = SwitchDefaults.colors(checkedThumbColor = accent, checkedTrackColor = accent.copy(alpha = 0.5f))
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Backups Header
        Text("Backup Local de Segurança", fontWeight = FontWeight.Bold, color = accent, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        backupRestoreState = viewModel.getExportString()
                        backupDialogVisible = true
                    }
                },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
            ) {
                Text("Exportar JSON")
            }
            Button(
                onClick = { restoreDialogVisible = true },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = accent)
            ) {
                Text("Importar JSON", color = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Soft Deleted Lixeira (Recycle Bin)
        Text("Lixeira (Transações Excluídas)", fontWeight = FontWeight.Bold, color = accent, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (deletedTransactions.isEmpty()) {
            Text("Lixeira vazia.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (del in deletedTransactions) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(del.description, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(formatAmount(del.amount, "MT") + " • " + formatDate(del.timestamp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row {
                                IconButton(onClick = { viewModel.restoreTransaction(del.id) }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Restaurar", tint = Color(0xFF4CAF50))
                                }
                                IconButton(onClick = { viewModel.permanentlyDeleteTransaction(del.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Excluir permanentemente", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        Divider()
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    // PIN Setup Dialog
    if (showPinDialog) {
        AlertDialog(
            onDismissRequest = { showPinDialog = false },
            title = { Text("Configurar PIN de 4 dígitos") },
            text = {
                OutlinedTextField(
                    value = pinTextState,
                    onValueChange = { if (it.length <= 4) pinTextState = it },
                    label = { Text("Insira o PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = accent, focusedLabelColor = accent)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pinTextState.length == 4) {
                            viewModel.savePIN(pinTextState)
                            pinTextState = ""
                            showPinDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPinDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    // Backup View Dialog
    if (backupDialogVisible) {
        AlertDialog(
            onDismissRequest = { backupDialogVisible = false },
            title = { Text("Cópia de Segurança JSON") },
            text = {
                Column {
                    Text("Copie o texto JSON abaixo e guarde em local seguro para restaurar futuramente:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = backupRestoreState,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { backupDialogVisible = false }, colors = ButtonDefaults.buttonColors(containerColor = accent)) {
                    Text("Fechar")
                }
            }
        )
    }

    // Restore Dialog
    if (restoreDialogVisible) {
        AlertDialog(
            onDismissRequest = { restoreDialogVisible = false },
            title = { Text("Restaurar Cópia de Segurança") },
            text = {
                Column {
                    Text("Cole o texto JSON da sua cópia de segurança abaixo para carregar todos os dados de volta:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restoreInput,
                        onValueChange = { restoreInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val success = viewModel.importData(restoreInput)
                            if (success) {
                                restoreInput = ""
                                restoreDialogVisible = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    Text("Confirmar Importação")
                }
            },
            dismissButton = {
                TextButton(onClick = { restoreDialogVisible = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

// --- LOANS SCREEN ---

@Composable
fun LoansScreen(viewModel: FinanceViewModel) {
    val loans by viewModel.loans.collectAsState()
    val accent = Color(android.graphics.Color.parseColor(viewModel.accentColor))
    val isDark = isSystemInDarkTheme()

    var showAddDialog by remember { mutableStateOf(false) }
    var loanName by remember { mutableStateOf("") }
    var principalInput by remember { mutableStateOf("") }
    var interestRateInput by remember { mutableStateOf("") }
    var monthsInput by remember { mutableStateOf("") }
    var loanType by remember { mutableStateOf("Empréstimo") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Empréstimos e Dívidas",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = accent,
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Novo")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (loans.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nenhum empréstimo ou dívida registrado.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(loans) { loan ->
                    val isDebt = loan.type == "Dívida"
                    val principal = loan.principalAmount
                    val rate = loan.interestRate / 100.0
                    val months = loan.months
                    
                    val totalAmount = principal * (1 + rate * months)
                    val monthlyInstallment = if (months > 0) totalAmount / months else totalAmount
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = loan.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Box(
                                    modifier = Modifier
                                        .background(
                                            if (isDebt) Color(0xFFFFEBEE) else Color(0xFFE8F5E9),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = loan.type,
                                        fontSize = 11.sp,
                                        color = if (isDebt) Color(0xFFD32F2F) else Color(0xFF388E3C),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Principal", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatAmount(principal, "MT"), fontWeight = FontWeight.SemiBold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Juros", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${loan.interestRate}% ao mês", fontWeight = FontWeight.SemiBold)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("Período", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$months meses", fontWeight = FontWeight.SemiBold)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Total a Pagar", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(formatAmount(totalAmount, "MT"), fontWeight = FontWeight.Bold, color = if(isDebt) Color(0xFFD32F2F) else accent)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha=0.1f))
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Parcela Mensal: " + formatAmount(monthlyInstallment, "MT"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { viewModel.deleteLoan(loan) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remover", tint = Color.Red)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        Dialog(onDismissRequest = { showAddDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                ) {
                    Text(
                        text = "Novo Registro",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = loanType == "Empréstimo",
                            onClick = { loanType = "Empréstimo" },
                            label = { Text("Empréstimo") }
                        )
                        FilterChip(
                            selected = loanType == "Dívida",
                            onClick = { loanType = "Dívida" },
                            label = { Text("Dívida") }
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = loanName,
                        onValueChange = { loanName = it },
                        label = { Text("Descrição (Ex: Carro, Amigo)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = principalInput,
                        onValueChange = { principalInput = it },
                        label = { Text("Valor Principal (MT)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = interestRateInput,
                            onValueChange = { interestRateInput = it },
                            label = { Text("Juros ao mês (%)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = monthsInput,
                            onValueChange = { monthsInput = it },
                            label = { Text("Meses") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showAddDialog = false }) {
                            Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val p = principalInput.toDoubleOrNull() ?: 0.0
                                val r = interestRateInput.toDoubleOrNull() ?: 0.0
                                val m = monthsInput.toIntOrNull() ?: 1
                                if (loanName.isNotBlank() && p > 0) {
                                    viewModel.addLoan(loanName, p, r, m, loanType)
                                    showAddDialog = false
                                    loanName = ""
                                    principalInput = ""
                                    interestRateInput = ""
                                    monthsInput = ""
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = accent)
                        ) {
                            Text("Salvar", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
