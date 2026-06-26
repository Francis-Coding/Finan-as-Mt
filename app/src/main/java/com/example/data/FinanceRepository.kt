package com.example.data

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter

class FinanceRepository(private val context: Context, private val db: AppDatabase) {
    private val walletDao = db.walletDao()
    private val categoryDao = db.categoryDao()
    private val transactionDao = db.transactionDao()
    private val dailyClosureDao = db.dailyClosureDao()
    private val financialGoalDao = db.financialGoalDao()
    private val loanDao = db.loanDao()

    val wallets: Flow<List<Wallet>> = walletDao.getAllWallets()
    val categories: Flow<List<Category>> = categoryDao.getAllCategories()
    val transactions: Flow<List<Transaction>> = transactionDao.getAllTransactions()
    val deletedTransactions: Flow<List<Transaction>> = transactionDao.getDeletedTransactions()
    val closures: Flow<List<DailyClosure>> = dailyClosureDao.getAllClosures()
    val goals: Flow<List<FinancialGoal>> = financialGoalDao.getAllGoals()
    val loans: Flow<List<Loan>> = loanDao.getAllLoans()

    /**
     * Seed default wallets and categories if database is empty.
     */
    suspend fun checkAndSeedDatabase() {
        // Seed Wallets
        val walletsList = walletDao.getWalletsList()
        if (walletsList.isEmpty()) {
            walletDao.insertWallet(Wallet(name = "M-Pesa", initialBalance = 1500.0, currentBalance = 1500.0, icon = "M", color = "#4CAF50"))
            walletDao.insertWallet(Wallet(name = "Carteira Física", initialBalance = 500.0, currentBalance = 500.0, icon = "C", color = "#FF9800"))
            walletDao.insertWallet(Wallet(name = "Conta Bancária", initialBalance = 10000.0, currentBalance = 10000.0, icon = "B", color = "#2196F3"))
            walletDao.insertWallet(Wallet(name = "e-Mola", initialBalance = 0.0, currentBalance = 0.0, icon = "E", color = "#9C27B0"))
        }

        // Seed Categories
        val categoriesList = categoryDao.getAllCategories().first()
        if (categoriesList.isEmpty()) {
            val defaults = listOf(
                Category(name = "Alimentação", icon = "A", color = "#E57373", type = "Despesa", monthlyLimit = 3000.0),
                Category(name = "Transporte", icon = "T", color = "#64B5F6", type = "Despesa", monthlyLimit = 1500.0),
                Category(name = "Educação", icon = "E", color = "#9575CD", type = "Despesa", monthlyLimit = 5000.0),
                Category(name = "Saúde", icon = "S", color = "#81C784", type = "Despesa", monthlyLimit = 1000.0),
                Category(name = "Lazer", icon = "L", color = "#FFD54F", type = "Despesa", monthlyLimit = 2000.0),
                Category(name = "Salário", icon = "S", color = "#4DB6AC", type = "Injecao"),
                Category(name = "Vendas", icon = "V", color = "#AED581", type = "Injecao"),
                Category(name = "Empréstimos", icon = "E", color = "#FF8A65", type = "Injecao"),
                Category(name = "Outros", icon = "O", color = "#A1887F", type = "Despesa"),
                Category(name = "Ajuste", icon = "A", color = "#90A4AE", type = "Despesa")
            )
            for (category in defaults) {
                categoryDao.insertCategory(category)
            }
        }
    }

    // --- TRANSACTION ACTIONS WITH ATOMIC BALANCE UPDATES ---

    suspend fun insertTransaction(transaction: Transaction): Long = db.withTransaction {
        val wallet = walletDao.getWalletById(transaction.walletId)
        if (wallet != null) {
            val updatedBalance = if (transaction.type == "Despesa") {
                wallet.currentBalance - transaction.amount
            } else {
                wallet.currentBalance + transaction.amount
            }
            walletDao.updateWallet(wallet.copy(currentBalance = updatedBalance))
        }
        transactionDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(newTransaction: Transaction) = db.withTransaction {
        val oldTransaction = transactionDao.getTransactionById(newTransaction.id) ?: return@withTransaction
        
        // Reverse old transaction impact
        val oldWallet = walletDao.getWalletById(oldTransaction.walletId)
        if (oldWallet != null) {
            val reversedBalance = if (oldTransaction.type == "Despesa") {
                oldWallet.currentBalance + oldTransaction.amount
            } else {
                oldWallet.currentBalance - oldTransaction.amount
            }
            walletDao.updateWallet(oldWallet.copy(currentBalance = reversedBalance))
        }

        // Apply new transaction impact
        val newWallet = walletDao.getWalletById(newTransaction.walletId)
        if (newWallet != null) {
            // Need to reload the wallet if it's the same wallet to get correct intermediate balance
            val walletToUpdate = if (newWallet.id == oldWallet?.id) {
                walletDao.getWalletById(newWallet.id)!!
            } else {
                newWallet
            }
            val updatedBalance = if (newTransaction.type == "Despesa") {
                walletToUpdate.currentBalance - newTransaction.amount
            } else {
                walletToUpdate.currentBalance + newTransaction.amount
            }
            walletDao.updateWallet(walletToUpdate.copy(currentBalance = updatedBalance))
        }

        transactionDao.insertTransaction(newTransaction)
    }

    suspend fun softDeleteTransaction(transactionId: Int) = db.withTransaction {
        val transaction = transactionDao.getTransactionById(transactionId) ?: return@withTransaction
        if (!transaction.isDeleted) {
            // Reverse balance impact because it is being removed from active ledger
            val wallet = walletDao.getWalletById(transaction.walletId)
            if (wallet != null) {
                val reversedBalance = if (transaction.type == "Despesa") {
                    wallet.currentBalance + transaction.amount
                } else {
                    wallet.currentBalance - transaction.amount
                }
                walletDao.updateWallet(wallet.copy(currentBalance = reversedBalance))
            }
            transactionDao.insertTransaction(transaction.copy(isDeleted = true))
        }
    }

    suspend fun restoreTransaction(transactionId: Int) = db.withTransaction {
        val transaction = transactionDao.getTransactionById(transactionId) ?: return@withTransaction
        if (transaction.isDeleted) {
            // Re-apply balance impact
            val wallet = walletDao.getWalletById(transaction.walletId)
            if (wallet != null) {
                val updatedBalance = if (transaction.type == "Despesa") {
                    wallet.currentBalance - transaction.amount
                } else {
                    wallet.currentBalance + transaction.amount
                }
                walletDao.updateWallet(wallet.copy(currentBalance = updatedBalance))
            }
            transactionDao.insertTransaction(transaction.copy(isDeleted = false))
        }
    }

    suspend fun hardDeleteTransaction(transactionId: Int) = db.withTransaction {
        val transaction = transactionDao.getTransactionById(transactionId) ?: return@withTransaction
        transactionDao.deleteTransaction(transaction)
    }

    // --- DUPLICATE CHECK ---

    suspend fun checkDuplicate(amount: Double, categoryId: Int, timestamp: Long): Boolean {
        // Calculate start and end of that day
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = timestamp
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val end = cal.timeInMillis - 1
        
        return transactionDao.findDuplicate(amount, categoryId, start, end) != null
    }

    // --- CLOSURE ACTIONS ---

    suspend fun getLatestClosureForWallet(walletId: Int): DailyClosure? {
        return dailyClosureDao.getLatestClosureForWallet(walletId)
    }

    suspend fun saveDailyClosure(closure: DailyClosure) = db.withTransaction {
        dailyClosureDao.insertClosure(closure)
        
        // If difference is non-zero, create a reconciliation transaction
        if (closure.difference != 0.0) {
            // Find "Ajuste" category
            var adjustmentCategory = categoryDao.getCategoryByName("Ajuste")
            if (adjustmentCategory == null) {
                val catId = categoryDao.insertCategory(Category(name = "Ajuste", icon = "A", color = "#90A4AE", type = "Despesa"))
                adjustmentCategory = Category(id = catId.toInt(), name = "Ajuste", icon = "A", color = "#90A4AE", type = "Despesa")
            }
            
            val isSurplus = closure.difference > 0.0
            val amount = Math.abs(closure.difference)
            
            val reconTx = Transaction(
                timestamp = closure.timestamp,
                categoryId = adjustmentCategory.id,
                description = "Ajuste de fechamento diário",
                amount = amount,
                type = if (isSurplus) "Injecao" else "Despesa",
                walletId = closure.walletId,
                isAdjustment = true
            )
            // Save the transaction
            transactionDao.insertTransaction(reconTx)
        }

        // Force wallet balance to match real physical balance exactly
        val wallet = walletDao.getWalletById(closure.walletId)
        if (wallet != null) {
            walletDao.updateWallet(wallet.copy(currentBalance = closure.realBalance))
        }
    }

    // --- FINANCIAL GOALS ---

    suspend fun insertGoal(goal: FinancialGoal) = financialGoalDao.insertGoal(goal)
    suspend fun updateGoal(goal: FinancialGoal) = financialGoalDao.updateGoal(goal)
    suspend fun deleteGoal(goal: FinancialGoal) = financialGoalDao.deleteGoal(goal)

    // --- LOANS CRUD ---

    suspend fun insertLoan(loan: Loan) = loanDao.insertLoan(loan)
    suspend fun updateLoan(loan: Loan) = loanDao.updateLoan(loan)
    suspend fun deleteLoan(loan: Loan) = loanDao.deleteLoan(loan)

    // --- WALLETS & CATEGORIES CRUD ---

    suspend fun insertWallet(wallet: Wallet) = walletDao.insertWallet(wallet)
    suspend fun updateWallet(wallet: Wallet) = walletDao.updateWallet(wallet)
    suspend fun deleteWallet(wallet: Wallet) = walletDao.deleteWallet(wallet)

    suspend fun insertCategory(category: Category) = categoryDao.insertCategory(category)
    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)
    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)

    // --- IMPORT / EXPORT (JSON BACKUP) ---

    suspend fun exportDataAsJsonString(): String = db.withTransaction {
        val root = JSONObject()
        
        val walletsList = walletDao.getWalletsList()
        val walletsArray = JSONArray()
        for (w in walletsList) {
            val obj = JSONObject()
            obj.put("name", w.name)
            obj.put("initialBalance", w.initialBalance)
            obj.put("currentBalance", w.currentBalance)
            obj.put("icon", w.icon)
            obj.put("color", w.color)
            walletsArray.put(obj)
        }
        root.put("wallets", walletsArray)

        val categoriesList = categoryDao.getAllCategories().first()
        val categoriesArray = JSONArray()
        for (c in categoriesList) {
            val obj = JSONObject()
            obj.put("name", c.name)
            obj.put("icon", c.icon)
            obj.put("color", c.color)
            obj.put("type", c.type)
            obj.put("monthlyLimit", c.monthlyLimit)
            categoriesArray.put(obj)
        }
        root.put("categories", categoriesArray)

        val transactionsList = transactionDao.getAllTransactions().first()
        val transactionsArray = JSONArray()
        for (t in transactionsList) {
            val obj = JSONObject()
            obj.put("timestamp", t.timestamp)
            obj.put("categoryName", categoriesList.firstOrNull { it.id == t.categoryId }?.name ?: "Outros")
            obj.put("description", t.description)
            obj.put("amount", t.amount)
            obj.put("type", t.type)
            obj.put("walletName", walletsList.firstOrNull { it.id == t.walletId }?.name ?: "Carteira Física")
            obj.put("isRecurrent", t.isRecurrent)
            obj.put("isAdjustment", t.isAdjustment)
            transactionsArray.put(obj)
        }
        root.put("transactions", transactionsArray)

        val goalsList = financialGoalDao.getAllGoals().first()
        val goalsArray = JSONArray()
        for (g in goalsList) {
            val obj = JSONObject()
            obj.put("name", g.name)
            obj.put("targetValue", g.targetValue)
            obj.put("currentValue", g.currentValue)
            obj.put("startDate", g.startDate)
            obj.put("endDate", g.endDate)
            goalsArray.put(obj)
        }
        root.put("goals", goalsArray)

        root.toString(2)
    }

    suspend fun importDataFromJsonString(jsonString: String): Boolean = db.withTransaction {
        try {
            val root = JSONObject(jsonString)
            
            // Clear existing tables
            db.clearAllTables()

            // Re-seed with imported wallets and save names mapped to new IDs
            val walletsMap = mutableMapOf<String, Int>()
            if (root.has("wallets")) {
                val walletsArray = root.getJSONArray("wallets")
                for (i in 0 until walletsArray.length()) {
                    val obj = walletsArray.getJSONObject(i)
                    val w = Wallet(
                        name = obj.getString("name"),
                        initialBalance = obj.getDouble("initialBalance"),
                        currentBalance = obj.getDouble("currentBalance"),
                        icon = obj.getString("icon"),
                        color = obj.getString("color")
                    )
                    val newId = walletDao.insertWallet(w)
                    walletsMap[w.name] = newId.toInt()
                }
            }

            // Re-seed with imported categories
            val categoriesMap = mutableMapOf<String, Int>()
            if (root.has("categories")) {
                val categoriesArray = root.getJSONArray("categories")
                for (i in 0 until categoriesArray.length()) {
                    val obj = categoriesArray.getJSONObject(i)
                    val c = Category(
                        name = obj.getString("name"),
                        icon = obj.getString("icon"),
                        color = obj.getString("color"),
                        type = obj.getString("type"),
                        monthlyLimit = obj.optDouble("monthlyLimit", 0.0)
                    )
                    val newId = categoryDao.insertCategory(c)
                    categoriesMap[c.name] = newId.toInt()
                }
            }

            // Fallbacks in case mappings are missing
            var defaultWalletId = walletsMap.values.firstOrNull() ?: 1
            var defaultCategoryId = categoriesMap.values.firstOrNull() ?: 1

            // Re-seed with imported transactions
            if (root.has("transactions")) {
                val transactionsArray = root.getJSONArray("transactions")
                for (i in 0 until transactionsArray.length()) {
                    val obj = transactionsArray.getJSONObject(i)
                    val categoryName = obj.getString("categoryName")
                    val walletName = obj.getString("walletName")
                    
                    val transaction = Transaction(
                        timestamp = obj.getLong("timestamp"),
                        categoryId = categoriesMap[categoryName] ?: defaultCategoryId,
                        description = obj.getString("description"),
                        amount = obj.getDouble("amount"),
                        type = obj.getString("type"),
                        walletId = walletsMap[walletName] ?: defaultWalletId,
                        isRecurrent = obj.optBoolean("isRecurrent", false),
                        isAdjustment = obj.optBoolean("isAdjustment", false)
                    )
                    transactionDao.insertTransaction(transaction)
                }
            }

            // Re-seed with imported goals
            if (root.has("goals")) {
                val goalsArray = root.getJSONArray("goals")
                for (i in 0 until goalsArray.length()) {
                    val obj = goalsArray.getJSONObject(i)
                    val g = FinancialGoal(
                        name = obj.getString("name"),
                        targetValue = obj.getDouble("targetValue"),
                        currentValue = obj.getDouble("currentValue"),
                        startDate = obj.getLong("startDate"),
                        endDate = obj.getLong("endDate")
                    )
                    financialGoalDao.insertGoal(g)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
