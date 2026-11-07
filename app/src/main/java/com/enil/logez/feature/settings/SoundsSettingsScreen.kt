package com.enil.logez.feature.settings

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.enil.logez.R
import com.enil.logez.core.designsystem.ScreenTitle
import com.enil.logez.core.designsystem.logEzTopAppBarColors
import com.enil.logez.feature.workout.audio.WorkoutAudioPlayer
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * The audio player is a platform side-effect component that must never enter a ViewModel (spine
 * testing rule — see [WorkoutAudioPlayer]'s doc), so the preview taps reach the singleton through
 * an entry point from the UI layer instead. Never released here: the same instance serves the
 * live workout session service.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface SettingsAudioEntryPoint {
    fun workoutAudioPlayer(): WorkoutAudioPlayer
}

/** Only the timer-tone picker is still a dialog — the three volumes are inline sliders now. */
private enum class SoundsDialog { TIMER_SOUND }

/** The audio layer ships exactly five timer tones (SoundPoolWorkoutAudioPlayer ids 1..5). */
private val TIMER_SOUND_IDS = (1..5).toList()

/** Owner, 2026-09-03: a volume slider dragged to silent still needs an audible preview when
 * picking a timer tone, or the pick would read as broken rather than as "silenced on purpose". */
private const val FALLBACK_PREVIEW_VOLUME = 0.66f

/**
 * M16 Sounds sub-page: timer tone selection plus the three volume rows. Selecting a tone plays a
 * preview through the same player the live session uses; each volume slider fires its own preview
 * on release, so what you hear here is exactly what the logger will play.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundsSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    var openDialog by remember { mutableStateOf<SoundsDialog?>(null) }

    val context = LocalContext.current
    val audioPlayer = remember {
        EntryPointAccessors.fromApplication(context.applicationContext, SettingsAudioEntryPoint::class.java)
            .workoutAudioPlayer()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = logEzTopAppBarColors(),
                title = { ScreenTitle(stringResource(R.string.settings_sounds_row)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item(key = "timer_sound") {
                SettingsValueRow(
                    title = stringResource(R.string.settings_timer_sound),
                    subtitle = stringResource(R.string.settings_timer_sound_subtitle),
                    value = stringResource(R.string.settings_timer_sound_option, settings.timerSound),
                    onClick = { openDialog = SoundsDialog.TIMER_SOUND },
                )
            }
            item(key = "timer_volume") {
                SettingsSliderRow(
                    title = stringResource(R.string.settings_timer_volume),
                    value = settings.timerVolume,
                    onValueChangeFinished = { volume ->
                        viewModel.setTimerVolume(volume)
                        audioPlayer.playTimerSound(settings.timerSound, if (volume <= 0f) FALLBACK_PREVIEW_VOLUME else volume)
                    },
                )
            }
            item(key = "set_complete_volume") {
                SettingsSliderRow(
                    title = stringResource(R.string.settings_set_complete_volume),
                    value = settings.setCompleteVolume,
                    onValueChangeFinished = { volume ->
                        viewModel.setSetCompleteVolume(volume)
                        audioPlayer.playSetCompleteSound(if (volume <= 0f) FALLBACK_PREVIEW_VOLUME else volume)
                    },
                )
            }
            item(key = "pr_volume") {
                SettingsSliderRow(
                    title = stringResource(R.string.settings_pr_volume),
                    value = settings.prVolume,
                    onValueChangeFinished = { volume ->
                        viewModel.setPrVolume(volume)
                        audioPlayer.playPrFanfare(if (volume <= 0f) FALLBACK_PREVIEW_VOLUME else volume)
                    },
                )
            }
        }
    }

    when (openDialog) {
        SoundsDialog.TIMER_SOUND -> SettingsRadioDialog(
            title = stringResource(R.string.settings_timer_sound),
            options = TIMER_SOUND_IDS,
            selected = settings.timerSound,
            optionLabel = { stringResource(R.string.settings_timer_sound_option, it) },
            onSelect = { soundId ->
                viewModel.setTimerSound(soundId)
                // Preview at the configured timer volume; if that's silenced, preview at the
                // fallback so the pick is still audible — true silence would read as broken.
                val previewVolume = if (settings.timerVolume <= 0f) FALLBACK_PREVIEW_VOLUME else settings.timerVolume
                audioPlayer.playTimerSound(soundId, previewVolume)
            },
            onDismiss = { openDialog = null },
        )
        null -> Unit
    }
}
