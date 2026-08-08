package com.baraa.masroof.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.baraa.masroof.data.db.MessagePatternStatus
import com.baraa.masroof.transaction.MoneyFlowDirection
import com.baraa.masroof.transaction.TransactionType
import com.baraa.masroof.transaction.TransactionTypeTaxonomy

object TransactionTypeVisuals {
    fun icon(type: TransactionType): ImageVector = when (type) {
        TransactionType.PURCHASE -> Icons.Filled.ShoppingCart
        TransactionType.ONLINE_PURCHASE -> Icons.Filled.Language
        TransactionType.TRANSFER_OUT -> Icons.AutoMirrored.Filled.CallMade
        TransactionType.TRANSFER_IN -> Icons.AutoMirrored.Filled.CallReceived
        TransactionType.INTERNAL_TRANSFER -> Icons.Filled.SwapHoriz
        TransactionType.SALARY -> Icons.Filled.Payments
        TransactionType.REFUND -> Icons.Filled.Replay
        TransactionType.CASH_WITHDRAWAL -> Icons.Filled.AccountBalanceWallet
        TransactionType.BILL_PAYMENT -> Icons.Filled.Receipt
        TransactionType.CARD_PAYMENT -> Icons.Filled.CreditCard
        TransactionType.FEE, TransactionType.OTHER_FINANCIAL -> Icons.Filled.RequestQuote
        TransactionType.NON_FINANCIAL -> Icons.Filled.Info
    }

    fun label(type: TransactionType): String = TransactionTypeTaxonomy.labelAr(type)

    fun directionIcon(direction: MoneyFlowDirection): ImageVector = when (direction) {
        MoneyFlowDirection.INFLOW -> Icons.AutoMirrored.Filled.CallReceived
        MoneyFlowDirection.OUTFLOW -> Icons.AutoMirrored.Filled.CallMade
        MoneyFlowDirection.TRANSFER -> Icons.Filled.SwapHoriz
        MoneyFlowDirection.NONE -> Icons.Filled.Info
    }

    fun directionLabel(direction: MoneyFlowDirection): String =
        TransactionTypeTaxonomy.directionLabelAr(direction)

    fun statusLabel(status: MessagePatternStatus): String =
        com.baraa.masroof.data.repository.TemplateStatusLabels.statusAr(status)
}
