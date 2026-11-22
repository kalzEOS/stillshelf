package com.stillshelf.app.absstats

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collect

internal data class AbsLocalControls(val key: String? = null, val account: String = "Your logged-in account",
    val recording: Boolean = true, val count: Int = 0, val busy: Boolean = false, val message: String? = null,
    val preview: AbsLocalBackupPreview? = null)

@HiltViewModel
internal class AbsLocalHistoryViewModel @Inject constructor(private val repository: AbsStatisticsRepository,
    @ApplicationContext private val context: Context) : ViewModel() {
    private val mutable = MutableStateFlow(AbsLocalControls())
    val state = mutable.asStateFlow()
    private var operation: Job? = null
    init { viewModelScope.launch {
        repository.scopes().map { it.first }.distinctUntilChanged().collect {
            operation?.cancelAndJoin()
            mutable.value = AbsLocalControls()
            reload()
        }
    } }
    fun reload() = work {
        val (account, label) = repository.localToolsAccount()
        mutable.value = AbsLocalControls(account.key, label, repository.localAccounts.recording(account.key),
            repository.localRecords(account.key).size, busy = true)
    }
    private fun work(block: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.value = mutable.value.copy(busy = true, message = null)
        operation = viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutable.value = mutable.value.copy(message = "Could not complete this action. Check your connection or backup file and try again.") }
            finally { mutable.value = mutable.value.copy(busy = false) }
        }
    }
    fun recording(enabled: Boolean) = work {
        val key = mutable.value.key ?: error("No verified account")
        repository.localToolsAccount(key)
        repository.localAccounts.setRecording(key, enabled)
        mutable.value = mutable.value.copy(recording = enabled)
    }
    fun export(uri: Uri, expectedKey: String) = work {
        repository.localToolsAccount(expectedKey)
        val records = repository.localRecords(expectedKey)
        context.contentResolver.openOutputStream(uri, "wt")!!.use {
            AbsLocalBackup.write(it, repository.localAccounts.owner(expectedKey), records)
        }
        mutable.value = mutable.value.copy(message = "Local history backup saved.")
    }
    fun preview(uri: Uri, expectedKey: String) = work {
        repository.localToolsAccount(expectedKey)
        val preview = context.contentResolver.openInputStream(uri)!!.use { AbsLocalBackup.preview(it) }
        mutable.value = mutable.value.copy(preview = preview)
    }
    fun cancelImport() { mutable.value = mutable.value.copy(preview = null) }
    fun confirmImport() = work {
        val preview = mutable.value.preview ?: error("No backup selected")
        val key = mutable.value.key ?: error("No account")
        val (account, _) = repository.localToolsAccount(key)
        repository.localJournal.importVerified(preview, repository.localAccounts.owner(key), account.serverId)
        mutable.value = mutable.value.copy(preview = null, count = repository.localRecords(key).size,
            message = "Backup imported. Refresh statistics to see the restored history.")
    }
    fun clear() = work {
        val key = mutable.value.key ?: error("No account")
        repository.clearLocal(key)
        mutable.value = mutable.value.copy(count = 0, message = "Local history and pending submissions cleared. Server history was kept.")
    }
}
