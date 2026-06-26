package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallets")
data class Wallet(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val initialBalance: Double,
    val currentBalance: Double,
    val icon: String, // Emoji string, e.g., "💵", "📱"
    val color: String // Hex string, e.g., "#1A73E8"
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val icon: String, // Emoji, e.g., "🍔", "🎓"
    val color: String, // Hex string
    val type: String, // "Despesa" or "Injecao"
    val monthlyLimit: Double = 0.0 // 0.0 means no limit
)

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long,
    val categoryId: Int, // Refers to Category id
    val description: String,
    val amount: Double,
    val type: String, // "Despesa" or "Injecao"
    val walletId: Int, // Refers to Wallet id
    val receiptUrl: String? = null, // Path to image/file
    val isRecurrent: Boolean = false,
    val isAdjustment: Boolean = false,
    val isDeleted: Boolean = false, // soft delete support for Recycle Bin (Lixeira)
    val recurrencePeriod: String = "Mensal"
)

@Entity(tableName = "daily_closures")
data class DailyClosure(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timestamp: Long, // Start of the closed day
    val walletId: Int, // Refers to Wallet id
    val expectedBalance: Double,
    val realBalance: Double,
    val difference: Double
)

@Entity(tableName = "financial_goals")
data class FinancialGoal(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val targetValue: Double,
    val currentValue: Double,
    val startDate: Long,
    val endDate: Long,
    val categoryId: Int? = null // Optional category association
)

@Entity(tableName = "loans")
data class Loan(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val principalAmount: Double,
    val interestRate: Double, // in percentage, e.g. 10.0 for 10%
    val months: Int, // payment periods
    val startDate: Long,
    val type: String // "Emprestimo" (money given to you) or "Divida" (money you owe)
)
