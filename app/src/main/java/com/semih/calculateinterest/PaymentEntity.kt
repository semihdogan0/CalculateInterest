package com.semih.calculateinterest

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payments")
data class PaymentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val cardId: Long,
    val amount: Double,
    val note: String,
    val paymentDateMillis: Long
)