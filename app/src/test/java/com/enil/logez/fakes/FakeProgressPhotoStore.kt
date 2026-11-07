package com.enil.logez.fakes

import android.net.Uri
import com.enil.logez.core.data.media.ProgressPhotoStore

class FakeProgressPhotoStore(private val result: String? = "progress_photos/fake.jpg") : ProgressPhotoStore {
    var lastCopiedUri: Uri? = null
        private set

    override suspend fun copyToAppStorage(uri: Uri): String? {
        lastCopiedUri = uri
        return result
    }
}
