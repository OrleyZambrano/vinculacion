package com.example.vinculacion.ui.common

import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView

/**
 * Small helper that keeps list, loader and empty placeholders in sync across screens.
 */
class ListContentStateHandler(
    private val recyclerView: RecyclerView,
    private val progressView: View,
    private val emptyView: TextView
) {

    fun showLoading() {
        progressView.isVisible = true
        recyclerView.isVisible = false
        emptyView.isVisible = false
    }

    fun showContent(hasItems: Boolean, hasModifiers: Boolean, defaultEmptyText: CharSequence, filteredEmptyText: CharSequence) {
        progressView.isVisible = false
        recyclerView.isVisible = hasItems
        emptyView.isVisible = !hasItems
        if (!hasItems) {
            emptyView.text = if (hasModifiers) filteredEmptyText else defaultEmptyText
        }
    }

    fun <T> showContent(items: List<T>, hasModifiers: Boolean, defaultEmptyText: CharSequence, filteredEmptyText: CharSequence) {
        showContent(items.isNotEmpty(), hasModifiers, defaultEmptyText, filteredEmptyText)
    }

    fun showEmpty(message: CharSequence) {
        progressView.isVisible = false
        recyclerView.isVisible = false
        emptyView.isVisible = true
        emptyView.text = message
    }

    fun showError(message: CharSequence) {
        progressView.isVisible = false
        recyclerView.isVisible = false
        emptyView.isVisible = true
        emptyView.text = message
    }
}
