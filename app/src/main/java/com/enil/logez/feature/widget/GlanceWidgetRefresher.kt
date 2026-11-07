package com.enil.logez.feature.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.domain.WidgetRefresher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GlanceWidgetRefresher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val logger: AppLogger,
) : WidgetRefresher {
    /** A no-op when the user has not placed the widget, so callers need no guard. */
    override suspend fun refresh() {
        runCatching { WeeklyProgressWidget().updateAll(context) }
            .onFailure { logger.e(TAG, "Widget refresh failed", it) }
    }

    private companion object {
        const val TAG = "GlanceWidgetRefresher"
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class WidgetModule {
    @Binds
    @Singleton
    abstract fun bindWidgetRefresher(impl: GlanceWidgetRefresher): WidgetRefresher
}
