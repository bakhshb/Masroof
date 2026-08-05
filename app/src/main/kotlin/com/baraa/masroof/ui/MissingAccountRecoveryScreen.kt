package com.baraa.masroof.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.baraa.masroof.ui.theme.PrimaryButton
import com.baraa.masroof.ui.theme.SecondaryButton
import com.baraa.masroof.ui.theme.Spacing

/**
 * Recovery screen shown when the persisted state is inconsistent:
 * onboardingCompleted=true but no account row exists in Room.
 *
 * Triggered only when:
 *   - [com.baraa.masroof.ui.onboarding.OnboardingRepository.isCompleted] is true
 *   - AND [com.baraa.masroof.data.repository.FinancialAccountRepository.observeAll] is empty
 *
 * The user receives two options:
 *   - "إنشاء حساب" — open the account creation flow.
 *   - "استعادة الإعداد" — clear onboardingCompleted so the user can
 *     walk through the onboarding flow again.
 *
 * We DO NOT erase other data (transactions, categories, etc).
 */
@Composable
fun MissingAccountRecoveryScreen(
    onCreateAccount: () -> Unit,
    onRestoreOnboarding: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(Spacing.x4),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("لم يتم العثور على الحساب الذي تم إنشاؤه أثناء الإعداد.", style = MaterialTheme.typography.titleLarge)
            Text(
                "ربما تم حذف هذا الحساب من قبل. يمكنك إنشاء حساب جديد أو استعادة خطوات الإعداد من البداية.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.x2), modifier = Modifier.padding(top = 24.dp)) {
                PrimaryButton(label = "إنشاء حساب", onClick = onCreateAccount)
                SecondaryButton(label = "استعادة الإعداد", onClick = onRestoreOnboarding)
            }
        }
    }
}
