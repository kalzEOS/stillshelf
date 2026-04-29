package com.stillshelf.app.core.diagnostics

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DiagnosticLoggingEntryPoint {
    fun diagnosticLogManager(): DiagnosticLogManager
}
