package com.enil.logez.feature.privacy

import androidx.activity.SystemBarStyle
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.enil.logez.R
import com.enil.logez.core.designsystem.LogEzTheme
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.core.designsystem.Spacing
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import com.enil.logez.BuildConfig

/**
 * A deliberate, narrow exception to this app's single-Activity architecture (`MainActivity` hosts
 * every other screen via Compose Navigation, PHASE2_PLAN.md §2.1). Health Connect's own permission
 * screen needs to launch a real, independently-resolvable Activity component for its "why does
 * this app want this data" link -- on Android 13 and earlier via an intent-filter for
 * `androidx.health.ACTION_SHOW_PERMISSIONS_RATIONALE`, on Android 14+ via a `ViewPermissionUsageActivity`
 * alias handling `VIEW_PERMISSION_USAGE`/`HEALTH_PERMISSIONS` (both declared in AndroidManifest.xml,
 * per Android's official Health Connect integration guide) -- neither of which can target a
 * destination inside the app's own single Activity's nav graph.
 *
 * Without this, Health Connect's permission screen refuses to show the grant checkboxes at all --
 * confirmed on-device via logcat (`PermissionsActivity: App should support rationale intent,
 * finishing!`) -- which is exactly why tapping "Connect" on the Profile wellness card looked like a
 * dead button before this activity existed: the OS-owned permission screen opened and immediately
 * self-closed, with nothing this app's own code could catch or react to.
 *
 * Also reachable directly from Settings, as an ordinary privacy-policy screen -- the same content
 * either way. The text comes from [PrivacyPolicyContent], the same source the hosted web copy is
 * rendered from, so Health Connect's "same policy in the app and on the web" rule holds by
 * construction (2026-09-25).
 */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always light system-bar icons (2026-09-25): the UI is dark-only, but the default auto
        // style follows the system theme, so a phone in light mode drew dark status-bar icons on
        // the near-black app and they vanished.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LogEzTheme {
                PrivacyPolicyScreen(onBack = { finish() })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            // Deliberately NOT zeroed like the NavHost-hosted screens' TopAppBars: those live
            // inside LogEzApp's outer Scaffold, whose innerPadding already pushes the whole
            // NavHost below the status bar, so their TopAppBar's own inset would double it. This
            // Activity is its own top-level Scaffold with no such outer padding, so it needs
            // TopAppBar's default windowInsets to clear the status bar itself -- zeroing it here
            // (copied verbatim from those screens) drew "PRIVACY POLICY" under the status bar icons.
            TopAppBar(
                colors = logEzTopAppBarColors(),
                title = { ScreenTitle(stringResource(R.string.privacy_policy_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        val document = PrivacyPolicyContent.document(BuildConfig.CONTACT_EMAIL)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Text(
                stringResource(R.string.privacy_policy_effective, document.effectiveDate),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            document.sections.forEach { section ->
                Text(
                    section.heading,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = Spacing.md).semantics { heading() },
                )
                section.blocks.forEach { block ->
                    when (block) {
                        is PolicyBlock.Paragraph -> PolicyText(block.text)
                        is PolicyBlock.Bullets -> block.items.forEach { item ->
                            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                                Text("•", style = MaterialTheme.typography.bodyMedium)
                                PolicyText(item, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Body text with its https URLs and email address made tappable, like the hosted page's links. */
@Composable
private fun PolicyText(text: String, modifier: Modifier = Modifier) {
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = MaterialTheme.colorScheme.primary, textDecoration = TextDecoration.Underline),
    )
    val annotated = buildAnnotatedString {
        var last = 0
        PrivacyPolicyHtml.LINK.findAll(text).forEach { match ->
            append(text.substring(last, match.range.first))
            val url = if (match.value.startsWith("https://")) match.value else "mailto:${match.value}"
            withLink(LinkAnnotation.Url(url, linkStyles)) { append(match.value) }
            last = match.range.last + 1
        }
        append(text.substring(last))
    }
    Text(annotated, style = MaterialTheme.typography.bodyMedium, modifier = modifier)
}
