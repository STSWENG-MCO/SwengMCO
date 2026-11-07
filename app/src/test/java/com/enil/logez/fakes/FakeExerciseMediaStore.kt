package com.enil.logez.fakes

import android.net.Uri
import com.enil.logez.core.data.media.ExerciseMediaStore

class FakeExerciseMediaStore(private val result: String? = "exercise_media/fake.jpg") : ExerciseMediaStore {
    override suspend fun copyToAppStorage(uri: Uri): String? = result
}
