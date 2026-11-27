package com.enil.logez.core.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * In-memory stand-in for the app's preferences DataStore. The real file-backed store replaces its
 * file with a rename on every write, which fails on Windows after the first write, so only the
 * one test that needs real disk persistence uses the file-backed store.
 */
private class InMemoryPreferencesDataStore : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class ShareTemplateRepositoryImplTest {
    @get:Rule val temp = TemporaryFolder()

    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repo: ShareTemplateRepositoryImpl

    @Before
    fun setUp() {
        dataStore = InMemoryPreferencesDataStore()
        repo = ShareTemplateRepositoryImpl(dataStore)
    }

    private val custom =
        ShareCustomization(
            background = ShareBackground.LIME,
            textTone = ShareTextTone.DARK,
            metrics = setOf(ShareMetric.VOLUME, ShareMetric.SETS),
            details = setOf(ShareDetail.DATE, ShareDetail.START_TIME),
            numberMetric = ShareMetric.TIME,
        )

    private fun read(kind: WorkoutKind) = runBlocking { repo.choice(kind).first() }

    private fun putRaw(block: (MutablePreferences) -> Unit) = runBlocking { dataStore.edit(block) }

    @Test
    fun `nothing stored gives the default for both kinds`() {
        WorkoutKind.entries.forEach {
            assertEquals(ShareTemplateRegistry.defaultChoice(it), read(it))
        }
    }

    @Test
    fun `saved template and customization are read back`() {
        val choice = ShareTemplateChoice(ShareTemplate.GLASS, custom)
        runBlocking { repo.save(WorkoutKind.STRENGTH, choice) }
        assertEquals(choice, read(WorkoutKind.STRENGTH))
    }

    @Test
    fun `choice survives the store being closed and reopened`() {
        val file = File(temp.root, "share.preferences_pb")
        val choice = ShareTemplateChoice(ShareTemplate.RAIL, custom)

        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = ShareTemplateRepositoryImpl(PreferenceDataStoreFactory.create(scope = firstScope) { file })
        runBlocking { first.save(WorkoutKind.STRENGTH, choice) }
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val second = ShareTemplateRepositoryImpl(PreferenceDataStoreFactory.create(scope = secondScope) { file })
            assertEquals(choice, runBlocking { second.choice(WorkoutKind.STRENGTH).first() })
        } finally {
            secondScope.cancel()
        }
    }

    @Test
    fun `each kind keeps its own choice`() {
        runBlocking { repo.save(WorkoutKind.STRENGTH, ShareTemplateChoice(ShareTemplate.BODY_MAP, custom)) }
        assertEquals(ShareTemplate.STACK, read(WorkoutKind.GPS_TRACKED).template)

        runBlocking {
            repo.save(
                WorkoutKind.GPS_TRACKED,
                ShareTemplateChoice(
                    ShareTemplate.ROUTE,
                    ShareTemplateRegistry.defaultChoice(WorkoutKind.GPS_TRACKED).customization,
                ),
            )
        }
        assertEquals(ShareTemplate.BODY_MAP, read(WorkoutKind.STRENGTH).template)
        assertEquals(ShareTemplate.ROUTE, read(WorkoutKind.GPS_TRACKED).template)
    }

    @Test
    fun `an unknown stored template falls back to the default but keeps the rest`() {
        runBlocking { repo.save(WorkoutKind.STRENGTH, ShareTemplateChoice(ShareTemplate.GLASS, custom)) }
        putRaw { it[stringPreferencesKey("share.strength.template")] = "NOT_A_TEMPLATE" }

        val result = read(WorkoutKind.STRENGTH)
        assertEquals(ShareTemplate.STACK, result.template)
        assertEquals(custom, result.customization)
    }

    @Test
    fun `a template the kind does not support falls back to the default`() {
        putRaw { it[stringPreferencesKey("share.strength.template")] = ShareTemplate.ROUTE.name }
        assertEquals(ShareTemplate.STACK, read(WorkoutKind.STRENGTH).template)

        putRaw { it[stringPreferencesKey("share.gps_tracked.template")] = ShareTemplate.BODY_MAP.name }
        assertEquals(ShareTemplate.STACK, read(WorkoutKind.GPS_TRACKED).template)
    }

    @Test
    fun `unknown background and text tone fall back to the defaults`() {
        putRaw {
            it[stringPreferencesKey("share.strength.background")] = "NEON"
            it[stringPreferencesKey("share.strength.textTone")] = "PURPLE"
        }
        val defaults = ShareTemplateRegistry.defaultChoice(WorkoutKind.STRENGTH).customization
        val result = read(WorkoutKind.STRENGTH).customization
        assertEquals(defaults.background, result.background)
        assertEquals(defaults.textTone, result.textTone)
    }

    @Test
    fun `unknown chip names are dropped and a fully unknown set falls back to the default`() {
        putRaw { it[stringSetPreferencesKey("share.strength.metrics")] = setOf("VOLUME", "GONE", "PACE") }
        assertEquals(setOf(ShareMetric.VOLUME), read(WorkoutKind.STRENGTH).customization.metrics)

        putRaw { it[stringSetPreferencesKey("share.strength.metrics")] = setOf("GONE", "ALSO_GONE") }
        assertEquals(
            ShareMetric.forKind(WorkoutKind.STRENGTH),
            read(WorkoutKind.STRENGTH).customization.metrics,
        )
    }

    @Test
    fun `an empty chip set the user chose is kept not replaced by the default`() {
        putRaw { it[stringSetPreferencesKey("share.strength.details")] = emptySet() }
        assertEquals(emptySet<ShareDetail>(), read(WorkoutKind.STRENGTH).customization.details)
    }

    @Test
    fun `a number metric the kind does not log falls back to the default`() {
        putRaw { it[stringPreferencesKey("share.strength.numberMetric")] = ShareMetric.PACE.name }
        assertEquals(ShareMetric.VOLUME, read(WorkoutKind.STRENGTH).customization.numberMetric)
    }

    @Test
    fun `reset restores the default for that kind only`() {
        runBlocking {
            repo.save(WorkoutKind.STRENGTH, ShareTemplateChoice(ShareTemplate.GLASS, custom))
            repo.save(
                WorkoutKind.GPS_TRACKED,
                ShareTemplateChoice(
                    ShareTemplate.STRIP,
                    ShareTemplateRegistry.defaultChoice(WorkoutKind.GPS_TRACKED).customization,
                ),
            )
            repo.reset(WorkoutKind.STRENGTH)
        }
        assertEquals(ShareTemplateRegistry.defaultChoice(WorkoutKind.STRENGTH), read(WorkoutKind.STRENGTH))
        assertEquals(ShareTemplate.STRIP, read(WorkoutKind.GPS_TRACKED).template)
    }
}
