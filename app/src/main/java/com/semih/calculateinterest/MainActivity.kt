package com.semih.calculateinterest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dao = AppDatabase.Companion
            .getDatabase(applicationContext)
            .cardDao()

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val cardViewModel: CardViewModel = viewModel(
                        factory = CardViewModelFactory(dao)
                    )

                    CreditCardTrackerApp(
                        viewModel = cardViewModel
                    )
                }
            }
        }
    }
}

@Composable
fun CreditCardTrackerApp(
    viewModel: CardViewModel
) {
    var screen by rememberSaveable { mutableStateOf("list") }
    var selectedCardId by rememberSaveable { mutableStateOf<Long?>(null) }

    val cards by viewModel.cards.collectAsState()

    val selectedCard = selectedCardId?.let { id ->
        cards.firstOrNull { card -> card.id == id }
    }

    BackHandler(enabled = screen != "list") {
        when (screen) {
            "edit" -> screen = "detail"
            else -> {
                screen = "list"
                selectedCardId = null
            }
        }
    }

    when (screen) {
        "list" -> {
            CardListScreen(
                cards = cards,
                viewModel = viewModel,
                onAddClick = {
                    screen = "add"
                },
                onCardClick = { card ->
                    selectedCardId = card.id
                    screen = "detail"
                }
            )
        }

        "add" -> {
            CardFormScreen(
                title = "Yeni Kart Ekle",
                initialCard = null,
                onBack = {
                    screen = "list"
                },
                onSave = { card ->
                    viewModel.addCard(card)
                    screen = "list"
                }
            )
        }

        "detail" -> {
            if (selectedCard == null) {
                screen = "list"
            } else {
                CardDetailScreen(
                    card = selectedCard,
                    viewModel = viewModel,
                    onBack = {
                        screen = "list"
                        selectedCardId = null
                    },
                    onEdit = {
                        screen = "edit"
                    },
                    onDeleted = {
                        screen = "list"
                        selectedCardId = null
                    }
                )
            }
        }

        "edit" -> {
            if (selectedCard == null) {
                screen = "list"
            } else {
                CardFormScreen(
                    title = "Kartı Düzenle",
                    initialCard = selectedCard,
                    onBack = {
                        screen = "detail"
                    },
                    onSave = { updatedCard ->
                        viewModel.updateCard(updatedCard)
                        screen = "detail"
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardListScreen(
    cards: List<CardEntity>,
    viewModel: CardViewModel,
    onAddClick: () -> Unit,
    onCardClick: (CardEntity) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Kart Faiz Takip")
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddClick
            ) {
                Text("+")
            }
        }
    ) { padding ->
        if (cards.isEmpty()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .padding(20.dp)
                    .fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Henüz kart eklenmedi.",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onAddClick
                ) {
                    Text("İlk Kartı Ekle")
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = cards,
                    key = { card -> card.id }
                ) { card ->
                    val paymentFlow = remember(card.id) {
                        viewModel.paymentsFor(card.id)
                    }

                    val payments by paymentFlow.collectAsState(
                        initial = emptyList()
                    )

                    CardSummaryItem(
                        card = card,
                        payments = payments,
                        onClick = {
                            onCardClick(card)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun CardSummaryItem(
    card: CardEntity,
    payments: List<PaymentEntity>,
    onClick: () -> Unit
) {
    val totalPaid = payments.sumOf { payment ->
        payment.amount
    }

    val remainingDebt = max(
        a = 0.0,
        b = card.totalDebt - totalPaid
    )

    val automaticLateDays = calculateLateDaysFromDueDay(
        dueDay = card.dueDay
    )

    val estimatedInterest = calculateEstimatedInterest(
        remainingDebt = remainingDebt,
        monthlyInterestRate = card.monthlyInterestRate,
        days = automaticLateDays
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = card.cardName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (card.bankName.isNotBlank()) {
                Text(
                    text = card.bankName
                )
            }

            Text("Ekstre borcu: ${formatMoney(card.totalDebt)}")
            Text("Yapılan ödeme: ${formatMoney(totalPaid)}")
            Text("Kalan borç: ${formatMoney(remainingDebt)}")
            Text("Aylık faiz oranı: %${formatRate(card.monthlyInterestRate)}")
            Text("Tahmini gecikme günü: $automaticLateDays")
            Text("Bugüne göre tahmini faiz: ${formatMoney(estimatedInterest)}")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardFormScreen(
    title: String,
    initialCard: CardEntity?,
    onBack: () -> Unit,
    onSave: (CardEntity) -> Unit
) {
    var cardName by rememberSaveable(initialCard?.id) {
        mutableStateOf(initialCard?.cardName ?: "")
    }

    var bankName by rememberSaveable(initialCard?.id) {
        mutableStateOf(initialCard?.bankName ?: "")
    }

    var totalDebtText by rememberSaveable(initialCard?.id) {
        mutableStateOf(initialCard?.totalDebt?.let { plainNumber(it) } ?: "")
    }

    var interestRateText by rememberSaveable(initialCard?.id) {
        mutableStateOf(initialCard?.monthlyInterestRate?.let { plainNumber(it) } ?: "")
    }

    var statementDayText by rememberSaveable(initialCard?.id) {
        mutableStateOf(initialCard?.statementDay?.toString() ?: "")
    }

    var dueDayText by rememberSaveable(initialCard?.id) {
        mutableStateOf(initialCard?.dueDay?.toString() ?: "")
    }

    var note by rememberSaveable(initialCard?.id) {
        mutableStateOf(initialCard?.note ?: "")
    }

    var errorMessage by rememberSaveable {
        mutableStateOf<String?>(null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(title)
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack
                    ) {
                        Text("Geri")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = cardName,
                    onValueChange = {
                        cardName = it
                    },
                    label = {
                        Text("Kart adı")
                    },
                    placeholder = {
                        Text("Örn: Garanti Bonus")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = bankName,
                    onValueChange = {
                        bankName = it
                    },
                    label = {
                        Text("Banka adı")
                    },
                    placeholder = {
                        Text("Örn: Garanti")
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = totalDebtText,
                    onValueChange = {
                        totalDebtText = it
                    },
                    label = {
                        Text("Mevcut ekstre borcu")
                    },
                    placeholder = {
                        Text("Örn: 25000")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = interestRateText,
                    onValueChange = {
                        interestRateText = it
                    },
                    label = {
                        Text("Aylık faiz oranı (%)")
                    },
                    placeholder = {
                        Text("Örn: 4,25")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = statementDayText,
                    onValueChange = { value ->
                        statementDayText = value
                            .filter { char -> char.isDigit() }
                            .take(2)
                    },
                    label = {
                        Text("Hesap kesim günü")
                    },
                    placeholder = {
                        Text("Örn: 10")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = dueDayText,
                    onValueChange = { value ->
                        dueDayText = value
                            .filter { char -> char.isDigit() }
                            .take(2)
                    },
                    label = {
                        Text("Son ödeme günü")
                    },
                    placeholder = {
                        Text("Örn: 20")
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                    },
                    label = {
                        Text("Not")
                    },
                    placeholder = {
                        Text("İsteğe bağlı")
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }

            item {
                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item {
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val debt = parseNumber(totalDebtText)
                        val interestRate = parseNumber(interestRateText)
                        val statementDay = statementDayText.toIntOrNull()
                        val dueDay = dueDayText.toIntOrNull()

                        when {
                            cardName.trim().isBlank() -> {
                                errorMessage = "Kart adı boş olamaz."
                            }

                            debt == null || debt < 0.0 -> {
                                errorMessage = "Geçerli bir borç tutarı gir."
                            }

                            interestRate == null || interestRate < 0.0 -> {
                                errorMessage = "Geçerli bir faiz oranı gir."
                            }

                            statementDay == null || statementDay !in 1..31 -> {
                                errorMessage = "Hesap kesim günü 1-31 arasında olmalı."
                            }

                            dueDay == null || dueDay !in 1..31 -> {
                                errorMessage = "Son ödeme günü 1-31 arasında olmalı."
                            }

                            else -> {
                                val now = System.currentTimeMillis()

                                val card = CardEntity(
                                    id = initialCard?.id ?: 0,
                                    cardName = cardName.trim(),
                                    bankName = bankName.trim(),
                                    totalDebt = debt,
                                    monthlyInterestRate = interestRate,
                                    statementDay = statementDay,
                                    dueDay = dueDay,
                                    note = note.trim(),
                                    createdAt = initialCard?.createdAt ?: now,
                                    updatedAt = now
                                )

                                errorMessage = null
                                onSave(card)
                            }
                        }
                    }
                ) {
                    Text("Kaydet")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailScreen(
    card: CardEntity,
    viewModel: CardViewModel,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    val paymentFlow = remember(card.id) {
        viewModel.paymentsFor(card.id)
    }

    val payments by paymentFlow.collectAsState(
        initial = emptyList()
    )

    val totalPaid = payments.sumOf { payment ->
        payment.amount
    }

    val remainingDebt = max(
        a = 0.0,
        b = card.totalDebt - totalPaid
    )

    val automaticLateDays = calculateLateDaysFromDueDay(
        dueDay = card.dueDay
    )

    var interestDaysText by rememberSaveable(card.id) {
        mutableStateOf("")
    }

    val interestDays = interestDaysText.toIntOrNull()
        ?: automaticLateDays

    val estimatedInterest = calculateEstimatedInterest(
        remainingDebt = remainingDebt,
        monthlyInterestRate = card.monthlyInterestRate,
        days = interestDays
    )

    val totalWithInterest = remainingDebt + estimatedInterest

    var paymentAmountText by rememberSaveable(card.id) {
        mutableStateOf("")
    }

    var paymentNote by rememberSaveable(card.id) {
        mutableStateOf("")
    }

    var paymentError by rememberSaveable(card.id) {
        mutableStateOf<String?>(null)
    }

    var showDeleteCardDialog by rememberSaveable(card.id) {
        mutableStateOf(false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(card.cardName)
                },
                navigationIcon = {
                    TextButton(
                        onClick = onBack
                    ) {
                        Text("Geri")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onEdit
                    ) {
                        Text("Düzenle")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Borç Özeti",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        if (card.bankName.isNotBlank()) {
                            Text("Banka: ${card.bankName}")
                        }

                        Text("Ekstre borcu: ${formatMoney(card.totalDebt)}")
                        Text("Yapılan toplam ödeme: ${formatMoney(totalPaid)}")
                        Text("Kalan borç: ${formatMoney(remainingDebt)}")
                        Text("Hesap kesim günü: Her ayın ${card.statementDay}. günü")
                        Text("Son ödeme günü: Her ayın ${card.dueDay}. günü")
                        Text("Aylık faiz oranı: %${formatRate(card.monthlyInterestRate)}")

                        if (card.note.isNotBlank()) {
                            Text("Not: ${card.note}")
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Faiz Hesabı",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text("Bugüne göre otomatik gecikme günü: $automaticLateDays")

                        OutlinedTextField(
                            value = interestDaysText,
                            onValueChange = { value ->
                                interestDaysText = value
                                    .filter { char -> char.isDigit() }
                                    .take(4)
                            },
                            label = {
                                Text("Faiz hesaplanacak gün")
                            },
                            placeholder = {
                                Text("Boş bırakılırsa otomatik: $automaticLateDays")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Hesaplanan gün: $interestDays")
                        Text("Tahmini yansıyacak faiz: ${formatMoney(estimatedInterest)}")
                        Text("Faiz dahil tahmini toplam: ${formatMoney(totalWithInterest)}")

                        Text(
                            text = "Not: Bu hesaplama yaklaşık bir tahmindir. Bankaların uyguladığı vergi, gecikme faizi ve asgari ödeme kuralları farklı olabilir.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 4.dp
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Ödeme Ekle",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = paymentAmountText,
                            onValueChange = {
                                paymentAmountText = it
                            },
                            label = {
                                Text("Ödeme tutarı")
                            },
                            placeholder = {
                                Text("Örn: 5000")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Decimal
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = paymentNote,
                            onValueChange = {
                                paymentNote = it
                            },
                            label = {
                                Text("Ödeme notu")
                            },
                            placeholder = {
                                Text("Örn: Asgari ödeme")
                            },
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (paymentError != null) {
                            Text(
                                text = paymentError ?: "",
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                val amount = parseNumber(paymentAmountText)

                                if (amount == null || amount <= 0.0) {
                                    paymentError = "Geçerli bir ödeme tutarı gir."
                                } else {
                                    viewModel.addPayment(
                                        cardId = card.id,
                                        amount = amount,
                                        note = paymentNote
                                    )

                                    paymentAmountText = ""
                                    paymentNote = ""
                                    paymentError = null
                                }
                            }
                        ) {
                            Text("Ödemeyi Kaydet")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Ödeme Geçmişi",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (payments.isEmpty()) {
                item {
                    Text("Bu karta ait ödeme kaydı yok.")
                }
            } else {
                items(
                    items = payments,
                    key = { payment -> payment.id }
                ) { payment ->
                    PaymentRow(
                        payment = payment,
                        onDelete = {
                            viewModel.deletePayment(payment)
                        }
                    )
                }
            }

            item {
                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        showDeleteCardDialog = true
                    }
                ) {
                    Text("Kartı Sil")
                }
            }
        }
    }

    if (showDeleteCardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteCardDialog = false
            },
            title = {
                Text("Kart silinsin mi?")
            },
            text = {
                Text("Bu kart ve bu karta ait tüm ödeme kayıtları silinecek.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteCardDialog = false
                        viewModel.deleteCard(card)
                        onDeleted()
                    }
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteCardDialog = false
                    }
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

@Composable
fun PaymentRow(
    payment: PaymentEntity,
    onDelete: () -> Unit
) {
    var showDeleteDialog by rememberSaveable(payment.id) {
        mutableStateOf(false)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatMoney(payment.amount),
                    fontWeight = FontWeight.Bold
                )

                TextButton(
                    onClick = {
                        showDeleteDialog = true
                    }
                ) {
                    Text("Sil")
                }
            }

            Text("Tarih: ${formatDate(payment.paymentDateMillis)}")

            if (payment.note.isNotBlank()) {
                Text("Not: ${payment.note}")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text("Ödeme silinsin mi?")
            },
            text = {
                Text("Bu ödeme silinirse kalan borç yeniden hesaplanır.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Sil")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text("Vazgeç")
                }
            }
        )
    }
}

fun calculateEstimatedInterest(
    remainingDebt: Double,
    monthlyInterestRate: Double,
    days: Int
): Double {
    if (remainingDebt <= 0.0 || monthlyInterestRate <= 0.0 || days <= 0) {
        return 0.0
    }

    val dailyRate = (monthlyInterestRate / 100.0) / 30.0

    return remainingDebt * dailyRate * days
}

fun calculateLateDaysFromDueDay(
    dueDay: Int
): Int {
    val today = Calendar.getInstance()

    today.set(Calendar.HOUR_OF_DAY, 0)
    today.set(Calendar.MINUTE, 0)
    today.set(Calendar.SECOND, 0)
    today.set(Calendar.MILLISECOND, 0)

    val dueDate = Calendar.getInstance()

    dueDate.set(Calendar.HOUR_OF_DAY, 0)
    dueDate.set(Calendar.MINUTE, 0)
    dueDate.set(Calendar.SECOND, 0)
    dueDate.set(Calendar.MILLISECOND, 0)

    val maxDayOfCurrentMonth = dueDate.getActualMaximum(
        Calendar.DAY_OF_MONTH
    )

    val safeDueDay = dueDay.coerceIn(
        minimumValue = 1,
        maximumValue = maxDayOfCurrentMonth
    )

    dueDate.set(
        Calendar.DAY_OF_MONTH,
        safeDueDay
    )

    if (!today.after(dueDate)) {
        return 0
    }

    val millisecondsPerDay = 24L * 60L * 60L * 1000L

    return ((today.timeInMillis - dueDate.timeInMillis) / millisecondsPerDay).toInt()
}

fun parseNumber(
    input: String
): Double? {
    val clean = input
        .trim()
        .replace(" ", "")

    if (clean.isBlank()) {
        return null
    }

    val commaCount = clean.count { char ->
        char == ','
    }

    val dotCount = clean.count { char ->
        char == '.'
    }

    if (commaCount > 0 && dotCount > 0) {
        val lastComma = clean.lastIndexOf(',')
        val lastDot = clean.lastIndexOf('.')

        return if (lastComma > lastDot) {
            clean
                .replace(".", "")
                .replace(",", ".")
                .toDoubleOrNull()
        } else {
            clean
                .replace(",", "")
                .toDoubleOrNull()
        }
    }

    if (commaCount == 1 && dotCount == 0) {
        return clean
            .replace(",", ".")
            .toDoubleOrNull()
    }

    if (dotCount == 1 && commaCount == 0) {
        val dotIndex = clean.lastIndexOf('.')
        val digitsAfterDot = clean.length - dotIndex - 1

        return if (digitsAfterDot == 3 && dotIndex > 0) {
            clean
                .replace(".", "")
                .toDoubleOrNull()
        } else {
            clean.toDoubleOrNull()
        }
    }

    return clean.toDoubleOrNull()
}

fun plainNumber(
    value: Double
): String {
    return if (value % 1.0 == 0.0) {
        value.toLong().toString()
    } else {
        value
            .toString()
            .replace(".", ",")
    }
}

fun formatMoney(
    value: Double
): String {
    return NumberFormat
        .getCurrencyInstance(Locale("tr", "TR"))
        .format(value)
}

fun formatRate(
    value: Double
): String {
    return String.format(
        Locale("tr", "TR"),
        "%.2f",
        value
    )
}

fun formatDate(
    millis: Long
): String {
    val formatter = SimpleDateFormat(
        "dd.MM.yyyy HH:mm",
        Locale("tr", "TR")
    )

    return formatter.format(
        Date(millis)
    )
}