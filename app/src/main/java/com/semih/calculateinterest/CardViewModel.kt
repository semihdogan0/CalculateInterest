package com.semih.calculateinterest

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CardViewModel(
    private val dao: CardDao
) : ViewModel() {

    val cards: StateFlow<List<CardEntity>> = dao.observeCards()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun paymentsFor(cardId: Long): Flow<List<PaymentEntity>> {
        return dao.observePayments(cardId)
    }

    fun addCard(card: CardEntity) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            dao.insertCard(
                card.copy(
                    createdAt = now,
                    updatedAt = now
                )
            )
        }
    }

    fun updateCard(card: CardEntity) {
        viewModelScope.launch {
            dao.updateCard(
                card.copy(
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    fun deleteCard(card: CardEntity) {
        viewModelScope.launch {
            dao.deleteCard(card)
        }
    }

    fun addPayment(cardId: Long, amount: Double, note: String) {
        viewModelScope.launch {
            dao.insertPayment(
                PaymentEntity(
                    cardId = cardId,
                    amount = amount,
                    note = note.trim(),
                    paymentDateMillis = System.currentTimeMillis()
                )
            )
            dao.touchCard(cardId, System.currentTimeMillis())
        }
    }

    fun deletePayment(payment: PaymentEntity) {
        viewModelScope.launch {
            dao.deletePayment(payment)
            dao.touchCard(payment.cardId, System.currentTimeMillis())
        }
    }
}

class CardViewModelFactory(
    private val dao: CardDao
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CardViewModel(dao) as T
    }
}