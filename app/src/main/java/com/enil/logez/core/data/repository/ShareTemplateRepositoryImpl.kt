package com.enil.logez.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.enil.logez.core.domain.model.ShareBackground
import com.enil.logez.core.domain.model.ShareCustomization
import com.enil.logez.core.domain.model.ShareDetail
import com.enil.logez.core.domain.model.ShareMetric
import com.enil.logez.core.domain.model.ShareTemplate
import com.enil.logez.core.domain.model.ShareTemplateChoice
import com.enil.logez.core.domain.model.ShareTemplateRegistry
import com.enil.logez.core.domain.model.ShareTextTone
import com.enil.logez.core.domain.model.WorkoutKind
import com.enil.logez.core.domain.repository.ShareTemplateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Same DataStore file as [SettingsRepositoryImpl], its own keys, one set per [WorkoutKind]. */
class ShareTemplateRepositoryImpl
    @Inject
    constructor(
        private val dataStore: DataStore<Preferences>,
    ) : ShareTemplateRepository {
        private class Keys(
            kind: WorkoutKind,
        ) {
            private val prefix = "share.${kind.name.lowercase()}"
            val template = stringPreferencesKey("$prefix.template")
            val background = stringPreferencesKey("$prefix.background")
            val textTone = stringPreferencesKey("$prefix.textTone")
            val metrics = stringSetPreferencesKey("$prefix.metrics")
            val details = stringSetPreferencesKey("$prefix.details")
            val numberMetric = stringPreferencesKey("$prefix.numberMetric")
        }

        override fun choice(kind: WorkoutKind): Flow<ShareTemplateChoice> = dataStore.data.map { read(it, kind) }

        override suspend fun save(
            kind: WorkoutKind,
            choice: ShareTemplateChoice,
        ) {
            val keys = Keys(kind)
            dataStore.edit { prefs ->
                prefs[keys.template] = choice.template.name
                prefs[keys.background] = choice.customization.background.name
                prefs[keys.textTone] = choice.customization.textTone.name
                prefs[keys.metrics] =
                    choice.customization.metrics
                        .map { it.name }
                        .toSet()
                prefs[keys.details] =
                    choice.customization.details
                        .map { it.name }
                        .toSet()
                prefs[keys.numberMetric] = choice.customization.numberMetric.name
            }
        }

        override suspend fun reset(kind: WorkoutKind) {
            val keys = Keys(kind)
            dataStore.edit { prefs ->
                prefs.remove(keys.template)
                prefs.remove(keys.background)
                prefs.remove(keys.textTone)
                prefs.remove(keys.metrics)
                prefs.remove(keys.details)
                prefs.remove(keys.numberMetric)
            }
        }

        private fun read(
            prefs: Preferences,
            kind: WorkoutKind,
        ): ShareTemplateChoice {
            val keys = Keys(kind)
            val defaults = ShareTemplateRegistry.defaultChoice(kind)
            val d = defaults.customization
            return ShareTemplateChoice(
                template =
                    prefs[keys.template]
                        ?.toEnumOrNull<ShareTemplate>()
                        ?.takeIf { ShareTemplateRegistry.supports(it, kind) }
                        ?: defaults.template,
                customization =
                    ShareCustomization(
                        background = prefs[keys.background]?.toEnumOrNull<ShareBackground>() ?: d.background,
                        textTone = prefs[keys.textTone]?.toEnumOrNull<ShareTextTone>() ?: d.textTone,
                        metrics = prefs[keys.metrics].toEnumSetOrNull<ShareMetric> { kind in it.kinds } ?: d.metrics,
                        details = prefs[keys.details].toEnumSetOrNull<ShareDetail> { kind in it.kinds } ?: d.details,
                        numberMetric =
                            prefs[keys.numberMetric]
                                ?.toEnumOrNull<ShareMetric>()
                                ?.takeIf { kind in it.kinds }
                                ?: d.numberMetric,
                    ),
            )
        }

        private inline fun <reified E : Enum<E>> String.toEnumOrNull(): E? = runCatching { enumValueOf<E>(this) }.getOrNull()

        private inline fun <reified E : Enum<E>> Set<String>?.toEnumSetOrNull(allowed: (E) -> Boolean): Set<E>? {
            if (this == null) return null
            val parsed = mapNotNull { it.toEnumOrNull<E>() }.filter(allowed).toSet()
            return if (parsed.isEmpty() && isNotEmpty()) null else parsed
        }
    }
