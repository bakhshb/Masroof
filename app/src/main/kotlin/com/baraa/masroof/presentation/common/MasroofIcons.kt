package com.baraa.masroof.presentation.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalAtm
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RateReview
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.ui.graphics.vector.ImageVector
import com.baraa.masroof.domain.model.FinancialTransactionType
import com.baraa.masroof.domain.model.MessageFamily
import com.baraa.masroof.domain.model.ReviewKind

/**
 * Central icon mapping so dashboard, review, and onboarding stay visually consistent.
 */
object MasroofIcons {
    val appLogo: ImageVector = Icons.Filled.AccountBalanceWallet
    val periodHint: ImageVector = Icons.Filled.Info
    val netSpending: ImageVector = Icons.AutoMirrored.Filled.TrendingDown
    val income: ImageVector = Icons.AutoMirrored.Filled.TrendingUp
    val refunds: ImageVector = Icons.AutoMirrored.Filled.Undo
    val netCashFlow: ImageVector = Icons.Filled.SwapHoriz
    val moneyMovement: ImageVector = Icons.Filled.AccountBalance
    val recentTransactions: ImageVector = Icons.Filled.Receipt
    val reviewQueue: ImageVector = Icons.Filled.RateReview
    val rescan: ImageVector = Icons.Filled.Sync
    val retry: ImageVector = Icons.Filled.Refresh
    val periodPrevious: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft
    val periodNext: ImageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight
    val backToCurrent: ImageVector = Icons.Filled.DateRange

    val externalIn: ImageVector = Icons.AutoMirrored.Filled.CallReceived
    val externalOut: ImageVector = Icons.AutoMirrored.Filled.CallMade
    val cardPayment: ImageVector = Icons.Filled.CreditCard
    val cashWithdrawal: ImageVector = Icons.Filled.LocalAtm
    val selfTransfer: ImageVector = Icons.Filled.SwapHoriz

    val sms: ImageVector = Icons.Filled.Sms
    val calendar: ImageVector = Icons.Filled.CalendarMonth
    val merchant: ImageVector = Icons.Filled.Store
    val counterparty: ImageVector = Icons.Filled.Person
    val sender: ImageVector = Icons.Filled.Notifications
    val pairMatch: ImageVector = Icons.Filled.Link
    val success: ImageVector = Icons.Filled.CheckCircle
    val error: ImageVector = Icons.Filled.ErrorOutline
    val warning: ImageVector = Icons.Filled.WarningAmber
    val ownership: ImageVector = Icons.Filled.VerifiedUser
    val settings: ImageVector = Icons.Filled.Settings
    val shopping: ImageVector = Icons.Filled.ShoppingCart

    fun transactionType(type: FinancialTransactionType): ImageVector =
        when (type) {
            FinancialTransactionType.EXPENSE -> Icons.Filled.ShoppingCart
            FinancialTransactionType.INCOME -> Icons.AutoMirrored.Filled.TrendingUp
            FinancialTransactionType.SELF_TRANSFER -> Icons.Filled.SwapHoriz
            FinancialTransactionType.EXTERNAL_TRANSFER_IN -> Icons.AutoMirrored.Filled.CallReceived
            FinancialTransactionType.EXTERNAL_TRANSFER_OUT -> Icons.AutoMirrored.Filled.CallMade
            FinancialTransactionType.CREDIT_CARD_PAYMENT -> Icons.Filled.CreditCard
            FinancialTransactionType.REFUND -> Icons.AutoMirrored.Filled.Undo
            FinancialTransactionType.CASH_WITHDRAWAL -> Icons.Filled.LocalAtm
            FinancialTransactionType.FEE -> Icons.Filled.Receipt
            FinancialTransactionType.ADJUSTMENT -> Icons.Filled.SwapHoriz
            FinancialTransactionType.UNKNOWN -> Icons.Filled.Info
        }

    fun reviewKind(kind: ReviewKind): ImageVector =
        when (kind) {
            ReviewKind.NEEDS_REVIEW -> Icons.Filled.RateReview
            ReviewKind.PENDING_MATCH -> Icons.Filled.Link
        }

    fun messageFamily(family: MessageFamily?): ImageVector =
        when (family) {
            MessageFamily.PURCHASE -> Icons.Filled.ShoppingCart
            MessageFamily.TRANSFER_IN -> Icons.AutoMirrored.Filled.CallReceived
            MessageFamily.TRANSFER_OUT -> Icons.AutoMirrored.Filled.CallMade
            MessageFamily.BILL_PAYMENT,
            MessageFamily.CARD_PAYMENT,
            -> Icons.Filled.CreditCard
            MessageFamily.REFUND -> Icons.AutoMirrored.Filled.Undo
            MessageFamily.WITHDRAWAL -> Icons.Filled.LocalAtm
            MessageFamily.FEE -> Icons.Filled.Receipt
            else -> Icons.Filled.Info
        }
}
