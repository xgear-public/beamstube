package com.awebo.ytext.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.awebo.ytext.data.AppLanguage
import com.awebo.ytext.data.MiscDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val dataStore: MiscDataStore) : ViewModel() {
    // Private MutableStateFlow to hold the mutable state
    private val _currentLanguage = MutableStateFlow(AppLanguage.ENGLISH) // Initialize with a default
    // Public immutable StateFlow to expose to the UI
    val currentLanguage: StateFlow<AppLanguage> = _currentLanguage.asStateFlow()

    init {
        // Assuming dataStore.getLanguage() is a synchronous call.
        // If dataStore.getLanguage() were a suspend function, you'd launch a coroutine:
        // viewModelScope.launch { _currentLanguage.value = dataStore.getLanguage() }
        // If dataStore.getLanguage() returned a Flow, you would collect it or use stateIn operator.
        _currentLanguage.value = dataStore.getLanguage()
    }

    fun setLanguage(language: AppLanguage) {
        _currentLanguage.value = language // Update the StateFlow
        viewModelScope.launch {
            dataStore.setLanguage(language) // Persist the change
        }
    }
}