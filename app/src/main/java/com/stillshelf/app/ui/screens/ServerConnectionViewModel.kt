package com.stillshelf.app.ui.screens

import androidx.lifecycle.ViewModel
import com.stillshelf.app.data.repo.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

@HiltViewModel
class ServerConnectionViewModel @Inject constructor(
    sessionRepository: SessionRepository
) : ViewModel() {
    val messages: Flow<String> = sessionRepository.observeServerConnectionMessages()
}
