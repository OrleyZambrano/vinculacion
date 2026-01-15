package com.example.vinculacion.ui.common

/**
 * Generic UI state holder for screens that load asynchronous content.
 */
sealed class UiState<out T> {
    /** Indicates that a request is in-flight. */
    data object Loading : UiState<Nothing>()

    /** Emitted when content is available. */
    data class Success<T>(val data: T) : UiState<T>()

    /** Emitted when no content is available. */
    data class Empty(val message: CharSequence? = null) : UiState<Nothing>()

    /** Emitted when the request failed. */
    data class Error(val throwable: Throwable? = null, val message: CharSequence? = null) : UiState<Nothing>()
}
