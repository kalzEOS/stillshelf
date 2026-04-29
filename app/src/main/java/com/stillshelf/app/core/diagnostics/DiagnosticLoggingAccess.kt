package com.stillshelf.app.core.diagnostics

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.android.EntryPointAccessors

@Composable
fun rememberDiagnosticLogManager(): DiagnosticLogManager {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context,
            DiagnosticLoggingEntryPoint::class.java
        ).diagnosticLogManager()
    }
}
