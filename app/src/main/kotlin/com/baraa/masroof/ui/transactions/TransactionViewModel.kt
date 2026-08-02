package com.baraa.masroof.ui.transactions

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.baraa.masroof.MasroofApplication
import com.baraa.masroof.data.db.TransactionEntity
import com.baraa.masroof.data.repository.ImportPreview
import com.baraa.masroof.data.repository.ImportSummary
import com.baraa.masroof.data.repository.TransactionImportService
import com.baraa.masroof.data.repository.TransactionRepository
import com.baraa.masroof.sms.SmsMessage
import com.baraa.masroof.sms.SmsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State of the two-phase import flow. The [PreviewReady] variant carries the
 * full [TransactionImportService.PreviewResult] (counts + prepared entities)
 * so the confirm call has everything it needs.
 */
sealed interface ImportState {
    data object Idle : ImportState
    data object Scanning : ImportState
    data class PreviewReady(val result: TransactionImportService.PreviewResult) : ImportState
    data object Importing : ImportState
    data class Done(val summary: ImportSummary) : ImportState
    data class Error(val message: String) : ImportState
}

/**
 * Owns transaction-list state + the import / edit / delete flows.
 */
class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "TransactionViewModel"

    private val app: MasroofApplication = application as MasroofApplication
    private val repo: TransactionRepository = app.transactionRepository
    private val smsRepo: SmsRepository = app.smsRepository
    private val importService: TransactionImportService = app.importService

    /** Observable list of saved transactions, newest first. */
    val transactions: StateFlow<List<TransactionEntity>> = repo.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Observable count of saved transactions. */
    val transactionCount: StateFlow<Int> = repo.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    private val _importState = MutableStateFlow<ImportState>(ImportState.Idle)
    val importState: StateFlow<ImportState> = _importState.asStateFlow()

    /** Start a scan + parse pass and produce a [ImportState.PreviewReady] for the dialog. */
    fun startImport() {
        if (_importState.value is ImportState.Scanning || _importState.value is ImportState.Importing) {
            return
        }
        _importState.value = ImportState.Scanning
        viewModelScope.launch {
            try {
                val messages = withContext(Dispatchers.IO) { smsRepo.loadInbox() }
                val result = withContext(Dispatchers.Default) { importService.preview(messages) }
                _importState.value = ImportState.PreviewReady(result)
            } catch (t: Throwable) {
                Log.e(tag, "import preview failed", t)
                _importState.value = ImportState.Error(t.message ?: "unknown error")
            }
        }
    }

    /**
     * Commit a previously-previewed import. The caller passes the prepared
     * entities because the preview's counts were computed from them.
     */
    fun confirmImport(preview: TransactionImportService.PreviewResult) {
        if (_importState.value !is ImportState.PreviewReady) return
        _importState.value = ImportState.Importing
        viewModelScope.launch {
            try {
                val summary = withContext(Dispatchers.IO) {
                    importService.commit(preview)
                }
                Log.d(tag, "import complete inserted=${summary.inserted} dup=${summary.duplicatesSkipped}")
                _importState.value = ImportState.Done(summary)
            } catch (t: Throwable) {
                Log.e(tag, "import commit failed", t)
                _importState.value = ImportState.Error(t.message ?: "unknown error")
            }
        }
    }

    /** Cancel the preview and return to idle. */
    fun cancelImport() {
        _importState.value = ImportState.Idle
    }

    /** Dismiss a finished summary or error and return to idle. */
    fun dismissImportFeedback() {
        _importState.value = ImportState.Idle
    }

    /** Apply a user edit to one transaction. */
    fun updateTransaction(entity: TransactionEntity) {
        viewModelScope.launch {
            val updated = entity.copy(updatedAt = System.currentTimeMillis())
            withContext(Dispatchers.IO) { repo.update(updated) }
        }
    }

    /** Delete a single transaction. */
    fun deleteTransaction(entity: TransactionEntity) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.delete(entity) }
        }
    }

    /** Wipe the local database. Does not touch SMS on the device. */
    fun deleteAllTransactions() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repo.deleteAll() }
        }
    }
}
