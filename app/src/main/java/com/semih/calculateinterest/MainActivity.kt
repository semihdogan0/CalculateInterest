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
                title = "Add New Card",
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
                    title = "Edit Card",
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
                    Text("Credit Card Interest Tracker")
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
                    text = "No cards have been added yet.",
                    style = MaterialTheme.typography.titleMedium
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onAddClick
                ) {
                    Text("Add First Card")
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

            Text("Statement debt: ${formatMoney(card.totalDebt)}")
            Text("Total paid: ${formatMoney(totalPaid)}")
            Text("Remaining debt: ${formatMoney(remainingDebt)}")
            Text("Monthly interest rate: %${formatRate(card.monthlyInterestRate)}")
            Text("Estimated late days: $automaticLateDays")
            Text("Estimated interest as of today: ${formatMoney(estimatedInterest)}")
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
                        Text("Back")
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
                        Text("Card name")
                    },
                    placeholder = {
                        Text("Example: Garanti Bonus")
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
                        Text("Bank name")
                    },
                    placeholder = {
                        Text("Example: Garanti")
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
                        Text("Current statement debt")
                    },
                    placeholder = {
                        Text("Example: 25000")
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
                        Text("Monthly interest rate (%)")
                    },
                    placeholder = {
                        Text("Example: 4.25")
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
                        Text("Statement day")
                    },
                    placeholder = {
                        Text("Example: 10")
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
                        Text("Due day")
                    },
                    placeholder = {
                        Text("Example: 20")
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
                        Text("Note")
                    },
                    placeholder = {
                        Text("Optional")
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
                                errorMessage = "Card name cannot be empty."
                            }

                            debt == null || debt < 0.0 -> {
                                errorMessage = "Please enter a valid debt amount."
                            }

                            interestRate == null || interestRate < 0.0 -> {
                                errorMessage = "Please enter a valid interest rate."
                            }

                            statementDay == null || statementDay !in 1..31 -> {
                                errorMessage = "Statement day must be between 1 and 31."
                            }

                            dueDay == null || dueDay !in 1..31 -> {
                                errorMessage = "Due day must be between 1 and 31."
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
                    Text("Save")
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
                        Text("Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = onEdit
                    ) {
                        Text("Edit")
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
                            text = "Debt Summary",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        if (card.bankName.isNotBlank()) {
                            Text("Bank: ${card.bankName}")
                        }

                        Text("Statement debt: ${formatMoney(card.totalDebt)}")
                        Text("Total payments: ${formatMoney(totalPaid)}")
                        Text("Remaining debt: ${formatMoney(remainingDebt)}")
                        Text("Statement day: Day ${card.statementDay} of each month")
                        Text("Due day: Day ${card.dueDay} of each month")
                        Text("Monthly interest rate: %${formatRate(card.monthlyInterestRate)}")

                        if (card.note.isNotBlank()) {
                            Text("Note: ${card.note}")
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
                            text = "Interest Calculation",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        Text("Automatic late days as of today: $automaticLateDays")

                        OutlinedTextField(
                            value = interestDaysText,
                            onValueChange = { value ->
                                interestDaysText = value
                                    .filter { char -> char.isDigit() }
                                    .take(4)
                            },
                            label = {
                                Text("Days for interest calculation")
                            },
                            placeholder = {
                                Text("Leave empty to use automatic value: $automaticLateDays")
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Text("Calculated days: $interestDays")
                        Text("Estimated interest: ${formatMoney(estimatedInterest)}")
                        Text("Estimated total with interest: ${formatMoney(totalWithInterest)}")

                        Text(
                            text = "Note: This calculation is only an estimate. Banks may apply different tax, late interest, and minimum payment rules.",
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
                            text = "Add Payment",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = paymentAmountText,
                            onValueChange = {
                                paymentAmountText = it
                            },
                            label = {
                                Text("Payment amount")
                            },
                            placeholder = {
                                Text("Example: 5000")
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
                                Text("Payment note")
                            },
                            placeholder = {
                                Text("Example: Minimum payment")
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
                                    paymentError = "Please enter a valid payment amount."
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
                            Text("Save Payment")
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Payment History",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            if (payments.isEmpty()) {
                item {
                    Text("No payment records for this card.")
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
                    Text("Delete Card")
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
                Text("Delete card?")
            },
            text = {
                Text("This card and all related payment records will be deleted.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteCardDialog = false
                        viewModel.deleteCard(card)
                        onDeleted()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteCardDialog = false
                    }
                ) {
                    Text("Cancel")
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
                    Text("Delete")
                }
            }

            Text("Date: ${formatDate(payment.paymentDateMillis)}")

            if (payment.note.isNotBlank()) {
                Text("Note: ${payment.note}")
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
            },
            title = {
                Text("Delete payment?")
            },
            text = {
                Text("If this payment is deleted, the remaining debt will be recalculated.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                    }
                ) {
                    Text("Cancel")
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