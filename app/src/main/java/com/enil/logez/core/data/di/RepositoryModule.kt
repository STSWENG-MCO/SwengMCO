package com.enil.logez.core.data.di

import com.enil.logez.core.data.media.ExerciseMediaStore
import com.enil.logez.core.data.media.ExerciseMediaStoreImpl
import com.enil.logez.core.data.media.ProgressPhotoStore
import com.enil.logez.core.data.media.ProgressPhotoStoreImpl
import com.enil.logez.core.data.repository.ActiveSessionRepositoryImpl
import com.enil.logez.core.data.repository.ActivityTrackRepositoryImpl
import com.enil.logez.core.data.repository.ExerciseRepositoryImpl
import com.enil.logez.core.data.repository.GoalRepositoryImpl
import com.enil.logez.core.data.repository.MeasurementRepositoryImpl
import com.enil.logez.core.data.repository.PersonalRecordsRepositoryImpl
import com.enil.logez.core.data.repository.RoomTransactionRunner
import com.enil.logez.core.data.repository.RoutineRepositoryImpl
import com.enil.logez.core.data.repository.SettingsRepositoryImpl
import com.enil.logez.core.data.repository.WellnessRepositoryImpl
import com.enil.logez.core.data.repository.WorkoutHeartRateSampleRepositoryImpl
import com.enil.logez.core.data.repository.WorkoutRepositoryImpl
import com.enil.logez.core.domain.repository.ActiveSessionRepository
import com.enil.logez.core.domain.repository.ActivityTrackRepository
import com.enil.logez.core.domain.repository.ExerciseRepository
import com.enil.logez.core.domain.repository.GoalRepository
import com.enil.logez.core.domain.repository.MeasurementRepository
import com.enil.logez.core.domain.repository.PersonalRecordsRepository
import com.enil.logez.core.domain.repository.RoutineRepository
import com.enil.logez.core.domain.repository.SettingsRepository
import com.enil.logez.core.domain.repository.TransactionRunner
import com.enil.logez.core.domain.repository.WellnessRepository
import com.enil.logez.core.domain.repository.WorkoutHeartRateSampleRepository
import com.enil.logez.core.domain.repository.WorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds
    @Singleton
    abstract fun bindExerciseRepository(impl: ExerciseRepositoryImpl): ExerciseRepository

    @Binds
    @Singleton
    abstract fun bindRoutineRepository(impl: RoutineRepositoryImpl): RoutineRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(impl: WorkoutRepositoryImpl): WorkoutRepository

    @Binds
    @Singleton
    abstract fun bindMeasurementRepository(impl: MeasurementRepositoryImpl): MeasurementRepository

    @Binds
    @Singleton
    abstract fun bindPersonalRecordsRepository(impl: PersonalRecordsRepositoryImpl): PersonalRecordsRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindExerciseMediaStore(impl: ExerciseMediaStoreImpl): ExerciseMediaStore

    @Binds
    @Singleton
    abstract fun bindProgressPhotoStore(impl: ProgressPhotoStoreImpl): ProgressPhotoStore

    @Binds
    abstract fun bindMediaFileCleaner(impl: com.enil.logez.core.data.media.MediaFileJanitor): com.enil.logez.core.data.media.MediaFileCleaner

    @Binds
    @Singleton
    abstract fun bindActiveSessionRepository(impl: ActiveSessionRepositoryImpl): ActiveSessionRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner

    @Binds
    @Singleton
    abstract fun bindGoalRepository(impl: GoalRepositoryImpl): GoalRepository

    @Binds
    @Singleton
    abstract fun bindActivityTrackRepository(impl: ActivityTrackRepositoryImpl): ActivityTrackRepository

    @Binds
    @Singleton
    abstract fun bindWellnessRepository(impl: WellnessRepositoryImpl): WellnessRepository

    @Binds
    @Singleton
    abstract fun bindWorkoutHeartRateSampleRepository(impl: WorkoutHeartRateSampleRepositoryImpl): WorkoutHeartRateSampleRepository
}
