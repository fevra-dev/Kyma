package com.sonicvault.app.ui.screen.shamir

import android.net.Uri
import androidx.fragment.app.FragmentActivity
import com.sonicvault.app.domain.usecase.RecombineSeedUseCase
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Recombine flow state */
sealed class RecombineSeedState {
    data object Idle : RecombineSeedState()
    data object Combining : RecombineSeedState()
    data class Success(val seedPhrase: String) : RecombineSeedState()
    data class Error(val message: String) : RecombineSeedState()
}

class RecombineSeedViewModel(
    private val recombineSeedUseCase: RecombineSeedUseCase
) : ViewModel() {

    private val _state = MutableStateFlow<RecombineSeedState>(RecombineSeedState.Idle)
    val state: StateFlow<RecombineSeedState> = _state.asStateFlow()

    private val _selectedUris = MutableStateFlow<List<Uri>>(emptyList())
    val selectedUris: StateFlow<List<Uri>> = _selectedUris.asStateFlow()

    fun addUris(uris: List<Uri>) {
        _selectedUris.value = (_selectedUris.value + uris).distinct()
    }

    fun removeUri(uri: Uri) {
        _selectedUris.value = _selectedUris.value.filter { it != uri }
    }

    fun clearUris() {
        _selectedUris.value = emptyList()
    }

    fun startRecombine(activity: FragmentActivity) {
        val uris = _selectedUris.value
        if (uris.isEmpty()) {
            _state.value = RecombineSeedState.Error("Select at least one share file.")
            return
        }
        viewModelScope.launch {
            _state.value = RecombineSeedState.Combining
            val result = recombineSeedUseCase(stegoUris = uris, activity = activity)
            result.fold(
                onSuccess = { _state.value = RecombineSeedState.Success(it) },
                onFailure = { _state.value = RecombineSeedState.Error(it.message ?: "Recombine failed.") }
            )
        }
    }

    fun reset() {
        _state.value = RecombineSeedState.Idle
    }
}
