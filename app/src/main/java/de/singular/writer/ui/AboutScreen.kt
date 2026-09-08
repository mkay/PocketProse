// SPDX-License-Identifier: GPL-3.0-only

package de.singular.writer.ui

import android.os.Build
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import de.singular.writer.BuildConfig
import de.singular.writer.R

// Not private: [SupportDialog] points at the same two places, and one pair of constants is the
// only way its buttons and this page cannot drift apart.
internal const val REPO_URL = "https://github.com/mkay/PocketProse"
private const val ISSUES_URL = "$REPO_URL/issues"
internal const val KOFI_URL = "https://ko-fi.com/s1ngular"

/**
 * About: what the app is, where it lives, and how to report a bug or chip in.
 *
 * A tab inside [SettingsScreen] rather than a screen of its own, so it brings no bar and no back
 * handler: the settings around it own both, and closing them closes this too.
 *
 * Links open through [LocalUriHandler] — a plain intent to whatever browser is installed — rather
 * than through a Custom Tab as in the other three apps. That would be a second dependency for a
 * page visited twice, and this app has no `INTERNET` permission to lend it: every URL here is
 * handled by another process, which is what keeps the manifest's "talks to nothing" claim true and
 * checkable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AboutScreen(modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    val clipboard = LocalClipboardManager.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    // versionCode rides along in the copied string: it is what pins a bug report to an exact build
    // when a version was re-released, where the name alone can lie. Read from BuildConfig so it
    // cannot drift from the gradle constants.
    val version = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
    // Read up here rather than inside the long-press: a composable cannot be called from a callback.
    val versionClip = stringResource(R.string.about_version_clipboard, version)
    val copiedToast = stringResource(R.string.about_version_copied)

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        // The wordmark rather than the launcher icon. That icon is transparent artwork whose ink
        // runs from near-black to near-white and relies on its own dark tile for contrast; dropped
        // on this page it would half disappear on either theme. The wordmark is drawn in white and
        // tinted at the call site, so it follows the ink like everything else here — and it is the
        // app's name, which is what this line is for.
        Icon(
            painter = painterResource(R.drawable.wordmark),
            contentDescription = stringResource(R.string.app_name),
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.height(28.dp),
        )
        Spacer(Modifier.height(10.dp))
        // The tagline sits with the wordmark rather than in the About section below, because it
        // belongs to the name: the two are read as one line, and the section under them is a
        // paragraph in the first person. Same string the repository is described by.
        Text(
            stringResource(R.string.app_tagline),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        // Long-press to copy. Android 13 and up pops its own clipboard confirmation, so only older
        // versions get a toast.
        Text(
            stringResource(R.string.about_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(ControlShape)
                .combinedClickable(
                    onClick = {},
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        clipboard.setText(AnnotatedString(versionClip))
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                            Toast.makeText(context, copiedToast, Toast.LENGTH_SHORT).show()
                        }
                    },
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )

        Spacer(Modifier.height(28.dp))
        AboutSection(R.string.about_section_about) {
            AboutBody(R.string.about_body_about)
        }
        AboutSection(R.string.about_section_website) {
            AboutBody(R.string.about_body_website)
            AboutLink(REPO_URL) { uriHandler.openUri(REPO_URL) }
        }
        AboutSection(R.string.about_section_bugs) {
            AboutBody(R.string.about_body_bugs)
            AboutLink(ISSUES_URL) { uriHandler.openUri(ISSUES_URL) }
        }
        AboutSection(R.string.about_section_support) {
            AboutBody(R.string.about_body_support)
            AboutLink(KOFI_URL) { uriHandler.openUri(KOFI_URL) }
        }
        // GPL §5 requires a derivative to preserve legal notices, so a licence stated *in the app*
        // rather than only in the repo is worth more than it looks: a clone that stripped this page
        // has done so deliberately, and the before-and-after is a screenshot.
        AboutSection(R.string.about_section_license) {
            AboutBody(R.string.about_copyright)
            AboutBody(R.string.about_license)
            AboutLink(REPO_URL) { uriHandler.openUri(REPO_URL) }
        }
    }
}

@Composable
private fun AboutSection(@StringRes title: Int, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text(
            stringResource(title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun AboutBody(@StringRes text: Int) {
    Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
}

/** A tappable URL. Kept full-length rather than hidden behind link text so it stays readable. */
@Composable
private fun AboutLink(url: String, onClick: () -> Unit) {
    Text(
        url,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(ControlShape)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
    )
}
