package com.semih.calculateinterest

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class CardEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cardName: String,
    val bankName: String,
    val totalDebt: Double,
    val monthlyInterestRate: Double,
    val statementDay: Int,
    val dueDay: Int,
    val note: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)