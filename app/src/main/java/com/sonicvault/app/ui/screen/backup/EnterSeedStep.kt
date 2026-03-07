package com.sonicvault.app.ui.screen.backup

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sonicvault.app.ui.component.SeedInputCard

/**
 * Single purpose: enter seed phrase. Uses SeedInputCard with word count, validity, show/hide.
 * Rams: understandable, unobtrusive.
 *
 * @param scrollable When true, wraps content in verticalScroll. Set false when parent already
 *     scrolls (e.g. SplitSeedScreen) to avoid nested-scroll crash (infinity height constraints).
 * @param compactForFixedLayout When true, limits text field to 3 lines (matches transmit via sound).
 *     Prevents Cover Audio from being cut off; Rams: as little design as possible.
 */
@Composable
fun EnterSeedStep(
    seedPhrase: String,
    onSeedPhraseChange: (String) -> Unit,
    showPhrase: Boolean,
    onShowPhraseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    scrollable: Boolean = true,
    compactForFixedLayout: Boolean = false
) {
    val contentModifier = if (scrollable) modifier.verticalScroll(rememberScrollState()) else modifier
    SeedInputCard(
        seedPhrase = seedPhrase,
        onSeedPhraseChange = onSeedPhraseChange,
        showPhrase = showPhrase,
        onShowPhraseChange = onShowPhraseChange,
        modifier = contentModifier,
        compactForFixedLayout = compactForFixedLayout
    )
}
