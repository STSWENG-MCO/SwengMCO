package com.enil.logez.core.data.media

/**
 * M20b step 4: the pure sizing math behind [ExerciseMediaStoreImpl]'s copy-time downscale. A
 * picked photo (routinely 12+ MP) is first read header-only (`inJustDecodeBounds = true`, which
 * allocates no pixel data at all) to learn its full width and height, then decoded for real at the
 * smallest power-of-two `inSampleSize` (`BitmapFactory.Options`' own contract) that brings its long
 * edge at or under [target] pixels, and re-encoded — so what's actually stored is a few hundred KB,
 * not tens of megabytes, while Coil's own decode-time downsampling (M20b's other half) still
 * applies on top of that at each call site's own smaller size.
 */
internal fun inSampleSizeFor(width: Int, height: Int, target: Int): Int {
    if (width <= 0 || height <= 0 || target <= 0) return 1
    var inSampleSize = 1
    val longEdge = maxOf(width, height)
    while (longEdge / inSampleSize > target) inSampleSize *= 2
    return inSampleSize
}
