package com.enil.logez.core.common.di

import com.enil.logez.core.common.AndroidAppLogger
import com.enil.logez.core.common.AppLogger
import com.enil.logez.core.common.Clock
import com.enil.logez.core.common.ElapsedRealtimeClock
import com.enil.logez.core.common.SystemClock
import com.enil.logez.core.common.SystemElapsedRealtimeClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {
    @Binds
    @Singleton
    abstract fun bindClock(impl: SystemClock): Clock

    @Binds
    @Singleton
    abstract fun bindElapsedRealtimeClock(impl: SystemElapsedRealtimeClock): ElapsedRealtimeClock

    @Binds
    @Singleton
    abstract fun bindAppLogger(impl: AndroidAppLogger): AppLogger

    companion object {
        /** §9.2 — [com.enil.logez.feature.workout.session.WorkoutSessionController]'s fire-and-forget
         * persistence writes need a process-lifetime scope; injected (not self-created) so tests can
         * substitute a deterministic one instead of racing against `Dispatchers.Default`. */
        @Provides
        @Singleton
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
