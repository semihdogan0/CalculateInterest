package com.example.kredikartifaiz

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface `CardDao.kt` {

    @Query("SELECT * FROM cards ORDER BY updatedAt DESC")
    fun observeCards(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards WHERE id = :cardId LIMIT 1")
    fun observeCard(cardId: Long): Flow<CardEntity?>

    @Insert
    suspend fun insertCard(card: CardEntity): Long

    @Update
    suspend fun updateCard(card: CardEntity)

    @Delete
    suspend fun deleteCard(card: CardEntity)

    @Query("UPDATE cards SET updatedAt = :updatedAt WHERE id = :cardId")
    suspend fun touchCard(cardId: Long, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM payments WHERE cardId = :cardId ORDER BY paymentDateMillis DESC, id DESC")
    fun observePayments(cardId: Long): Flow<List<PaymentEntity>>

    @Insert
    suspend fun insertPayment(payment: PaymentEntity): Long

    @Delete
    suspend fun deletePayment(payment: PaymentEntity)
}