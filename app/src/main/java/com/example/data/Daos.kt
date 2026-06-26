package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY id ASC")
    fun getAllWallets(): Flow<List<Wallet>>

    @Query("SELECT * FROM wallets ORDER BY id ASC")
    suspend fun getWalletsList(): List<Wallet>

    @Query("SELECT * FROM wallets WHERE id = :id")
    suspend fun getWalletById(id: Int): Wallet?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWallet(wallet: Wallet): Long

    @Update
    suspend fun updateWallet(wallet: Wallet)

    @Delete
    suspend fun deleteWallet(wallet: Wallet)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getCategoryById(id: Int): Category?

    @Query("SELECT * FROM categories WHERE name = :name LIMIT 1")
    suspend fun getCategoryByName(name: String): Category?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE isDeleted = 0 ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE walletId = :walletId AND isDeleted = 0 ORDER BY timestamp DESC")
    fun getTransactionsForWallet(walletId: Int): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Int): Transaction?

    @Query("SELECT * FROM transactions WHERE isDeleted = 1 ORDER BY timestamp DESC")
    fun getDeletedTransactions(): Flow<List<Transaction>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    // Check duplicate: same day, same category, same amount
    @Query("SELECT * FROM transactions WHERE amount = :amount AND categoryId = :categoryId AND timestamp >= :startOfDay AND timestamp <= :endOfDay AND isDeleted = 0 LIMIT 1")
    suspend fun findDuplicate(amount: Double, categoryId: Int, startOfDay: Long, endOfDay: Long): Transaction?
}

@Dao
interface DailyClosureDao {
    @Query("SELECT * FROM daily_closures ORDER BY timestamp DESC")
    fun getAllClosures(): Flow<List<DailyClosure>>

    @Query("SELECT * FROM daily_closures ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestClosure(): DailyClosure?

    @Query("SELECT * FROM daily_closures WHERE walletId = :walletId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestClosureForWallet(walletId: Int): DailyClosure?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClosure(closure: DailyClosure): Long
}

@Dao
interface FinancialGoalDao {
    @Query("SELECT * FROM financial_goals ORDER BY endDate ASC")
    fun getAllGoals(): Flow<List<FinancialGoal>>

    @Query("SELECT * FROM financial_goals WHERE id = :id")
    suspend fun getGoalById(id: Int): FinancialGoal?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGoal(goal: FinancialGoal): Long

    @Update
    suspend fun updateGoal(goal: FinancialGoal)

    @Delete
    suspend fun deleteGoal(goal: FinancialGoal)
}

@Dao
interface LoanDao {
    @Query("SELECT * FROM loans")
    fun getAllLoans(): Flow<List<Loan>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLoan(loan: Loan): Long

    @Update
    suspend fun updateLoan(loan: Loan)

    @Delete
    suspend fun deleteLoan(loan: Loan)
}
