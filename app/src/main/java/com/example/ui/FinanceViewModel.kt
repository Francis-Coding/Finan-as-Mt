package com.example.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed class Screen {
    object Dashboard : Screen()
    object Transactions : Screen()
    object AddTransaction : Screen()
    object Wallets : Screen()
    object Reports : Screen()
    object Settings : Screen()
    object Goals : Screen()
    object Loans : Screen()
    object Categories : Screen()
}

class FinanceViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val repository = FinanceRepository(application, db)
    private val settingsManager = SettingsManager(application)
    private val currencyService = CurrencyService(application)

    // UI State and Screen Navigation
    var currentScreen by mutableStateOf<Screen>(Screen.Dashboard)
    var isAppUnlocked by mutableStateOf(!(settingsManager.pinEnabled || settingsManager.biometricEnabled))
    var pinErrorMsg by mutableStateOf("")
    var transactionToEdit by mutableStateOf<Transaction?>(null)

    // Data Flows from Room
    val wallets = repository.wallets.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val categories = repository.categories.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = repository.transactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val deletedTransactions = repository.deletedTransactions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val goals = repository.goals.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val closures = repository.closures.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val loans = repository.loans.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Settings States
    var selectedCurrency by mutableStateOf(settingsManager.defaultCurrency)
    var themeMode by mutableStateOf(settingsManager.theme)
    var accentColor by mutableStateOf(settingsManager.primaryColor)
    var isBalanceHidden by mutableStateOf(settingsManager.hideBalances)
    var isNotificationsEnabled by mutableStateOf(settingsManager.notificationsEnabled)
    var isPinEnabled by mutableStateOf(settingsManager.pinEnabled)
    var isBiometricEnabled by mutableStateOf(settingsManager.biometricEnabled)
    var pinCode by mutableStateOf(settingsManager.pinHash)

    // Current daily closure state
    var showClosureModal by mutableStateOf(false)
    var walletsToClose = mutableListOf<Wallet>()

    // Currency Conversion State
    var exchangeRates = mutableMapOf<String, Double>()

    init {
        viewModelScope.launch {
            // Seeding database
            repository.checkAndSeedDatabase()
            
            // Load initial exchange rates
            loadRates()

            // Check if we need to show Daily Closure dialog
            checkDailyClosureRequirement()
        }
    }

    private suspend fun loadRates() {
        for (curr in listOf("USD", "EUR", "ZAR", "MT")) {
            exchangeRates[curr] = currencyService.getRateToMzn(curr)
        }
    }

    private suspend fun checkDailyClosureRequirement() {
        val lastOpened = settingsManager.lastOpenedDate
        val now = System.currentTimeMillis()
        
        // Format to simple days to compare
        val lastCal = java.util.Calendar.getInstance().apply { timeInMillis = lastOpened }
        val nowCal = java.util.Calendar.getInstance().apply { timeInMillis = now }
        
        val isNewDay = lastOpened == 0L || 
                lastCal.get(java.util.Calendar.DAY_OF_YEAR) != nowCal.get(java.util.Calendar.DAY_OF_YEAR) ||
                lastCal.get(java.util.Calendar.YEAR) != nowCal.get(java.util.Calendar.YEAR)

        if (isNewDay) {
            settingsManager.lastOpenedDate = now
            val activeWallets = wallets.first()
            if (activeWallets.isNotEmpty()) {
                walletsToClose.clear()
                walletsToClose.addAll(activeWallets)
                showClosureModal = true
            }
        }
    }

    // --- TRANSACTION HANDLING ---

    fun addTransaction(
        description: String,
        amount: Double,
        type: String,
        categoryId: Int,
        walletId: Int,
        currency: String,
        timestamp: Long = System.currentTimeMillis(),
        isRecurrent: Boolean = false,
        recurrencePeriod: String = "Mensal",
        onDuplicateWarning: (suspend () -> Unit)? = null,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            // Currency conversion
            val rate = exchangeRates[currency] ?: 1.0
            val amountInMzn = amount * rate

            val isDup = repository.checkDuplicate(amountInMzn, categoryId, timestamp)
            val saveBlock = suspend {
                val tx = Transaction(
                    timestamp = timestamp,
                    categoryId = categoryId,
                    description = description,
                    amount = amountInMzn,
                    type = type,
                    walletId = walletId,
                    isRecurrent = isRecurrent,
                    recurrencePeriod = recurrencePeriod,
                    receiptUrl = if (currency != "MT") currency else null // we hijack receiptUrl to save currency label if any, for visual trace
                )
                repository.insertTransaction(tx)
                onComplete()
            }

            if (isDup && onDuplicateWarning != null) {
                onDuplicateWarning()
            } else {
                saveBlock()
            }
        }
    }

    fun saveTransactionDirectly(
        description: String,
        amount: Double,
        type: String,
        categoryId: Int,
        walletId: Int,
        currency: String,
        timestamp: Long = System.currentTimeMillis(),
        isRecurrent: Boolean = false,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            val rate = exchangeRates[currency] ?: 1.0
            val amountInMzn = amount * rate
            val tx = Transaction(
                timestamp = timestamp,
                categoryId = categoryId,
                description = description,
                amount = amountInMzn,
                type = type,
                walletId = walletId,
                isRecurrent = isRecurrent,
                receiptUrl = if (currency != "MT") currency else null
            )
            repository.insertTransaction(tx)
            onComplete()
        }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.updateTransaction(transaction)
        }
    }

    fun deleteTransaction(transactionId: Int) {
        viewModelScope.launch {
            repository.softDeleteTransaction(transactionId)
        }
    }

    fun restoreTransaction(transactionId: Int) {
        viewModelScope.launch {
            repository.restoreTransaction(transactionId)
        }
    }

    fun permanentlyDeleteTransaction(transactionId: Int) {
        viewModelScope.launch {
            repository.hardDeleteTransaction(transactionId)
        }
    }

    // --- DAILY CLOSURE HANDLING ---

    fun performDailyClosure(realTotal: Double) {
        viewModelScope.launch {
            val allWallets = wallets.value
            if (allWallets.isEmpty()) return@launch
            
            val expectedTotal = allWallets.sumOf { it.currentBalance }
            val diff = realTotal - expectedTotal
            
            // Assign the difference to the first available wallet (usually Carteira Física or main)
            val primaryWalletId = allWallets.first().id
            
            val closure = DailyClosure(
                timestamp = System.currentTimeMillis(),
                walletId = primaryWalletId,
                expectedBalance = expectedTotal,
                realBalance = realTotal,
                difference = diff
            )
            repository.saveDailyClosure(closure)
        }
    }

    // --- FINANCIAL GOAL HANDLING ---

    fun addGoal(name: String, targetValue: Double, endDate: Long, categoryId: Int? = null) {
        viewModelScope.launch {
            val goal = FinancialGoal(
                name = name,
                targetValue = targetValue,
                currentValue = 0.0,
                startDate = System.currentTimeMillis(),
                endDate = endDate,
                categoryId = categoryId
            )
            repository.insertGoal(goal)
        }
    }

    fun updateGoalProgress(goalId: Int, newAmount: Double) {
        viewModelScope.launch {
            val goal = goals.value.find { it.id == goalId } ?: return@launch
            repository.updateGoal(goal.copy(currentValue = newAmount))
        }
    }

    fun deleteGoal(goal: FinancialGoal) {
        viewModelScope.launch {
            repository.deleteGoal(goal)
        }
    }

    // --- LOAN HANDLING ---

    fun addLoan(name: String, principalAmount: Double, interestRate: Double, months: Int, type: String) {
        viewModelScope.launch {
            val loan = Loan(
                name = name,
                principalAmount = principalAmount,
                interestRate = interestRate,
                months = months,
                startDate = System.currentTimeMillis(),
                type = type
            )
            repository.insertLoan(loan)
        }
    }

    fun deleteLoan(loan: Loan) {
        viewModelScope.launch {
            repository.deleteLoan(loan)
        }
    }

    // --- WALLETS & CATEGORIES CRUD ---

    fun addWallet(name: String, balance: Double, icon: String, color: String) {
        viewModelScope.launch {
            val wallet = Wallet(
                name = name,
                initialBalance = balance,
                currentBalance = balance,
                icon = icon,
                color = color
            )
            repository.insertWallet(wallet)
        }
    }

    fun updateWallet(wallet: Wallet) {
        viewModelScope.launch {
            repository.updateWallet(wallet)
        }
    }

    fun deleteWallet(wallet: Wallet) {
        viewModelScope.launch {
            repository.deleteWallet(wallet)
        }
    }

    fun addCategory(name: String, icon: String, color: String, type: String, limit: Double) {
        viewModelScope.launch {
            val category = Category(
                name = name,
                icon = icon,
                color = color,
                type = type,
                monthlyLimit = limit
            )
            repository.insertCategory(category)
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            repository.updateCategory(category)
        }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            repository.deleteCategory(category)
        }
    }

    // --- SETTINGS MANIPULATION ---

    fun updateTheme(newTheme: String) {
        themeMode = newTheme
        settingsManager.theme = newTheme
    }

    fun updatePrimaryColor(colorHex: String) {
        accentColor = colorHex
        settingsManager.primaryColor = colorHex
    }

    fun updateDefaultCurrency(currency: String) {
        selectedCurrency = currency
        settingsManager.defaultCurrency = currency
        viewModelScope.launch {
            loadRates()
        }
    }

    fun toggleHideBalances() {
        val newVal = !isBalanceHidden
        isBalanceHidden = newVal
        settingsManager.hideBalances = newVal
    }

    fun toggleNotifications() {
        val newVal = !isNotificationsEnabled
        isNotificationsEnabled = newVal
        settingsManager.notificationsEnabled = newVal
    }

    fun savePIN(pin: String) {
        if (pin.length == 4) {
            pinCode = pin
            isPinEnabled = true
            settingsManager.pinHash = pin
            settingsManager.pinEnabled = true
            isAppUnlocked = true
        } else {
            pinCode = ""
            isPinEnabled = false
            settingsManager.pinHash = ""
            settingsManager.pinEnabled = false
            isBiometricEnabled = false
            settingsManager.biometricEnabled = false
            isAppUnlocked = true
        }
    }

    fun toggleBiometric(enabled: Boolean) {
        isBiometricEnabled = enabled
        settingsManager.biometricEnabled = enabled
    }

    fun unlockApp(pin: String): Boolean {
        return if (pin == pinCode) {
            isAppUnlocked = true
            pinErrorMsg = ""
            true
        } else {
            pinErrorMsg = "PIN Incorreto! Tente novamente."
            false
        }
    }

    // --- DATA BACKUP & RESTORE ---

    suspend fun getExportString(): String {
        return repository.exportDataAsJsonString()
    }

    suspend fun importData(jsonString: String): Boolean {
        val result = repository.importDataFromJsonString(jsonString)
        if (result) {
            // Refresh data from newly imported DB
            loadRates()
        }
        return result
    }
}
