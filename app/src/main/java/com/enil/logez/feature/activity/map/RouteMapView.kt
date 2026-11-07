package com.enil.logez.feature.activity.map

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.enil.logez.R
import com.enil.logez.core.designsystem.Spacing
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

/**
 * OpenFreeMap's hosted "Dark" style (Owner decision, 2026-09-25). It replaced MapTiler's
 * `streets-v2-dark`, whose free plan forbids commercial use; a paid listing would have needed
 * MapTiler's paid tier. OpenFreeMap allows commercial use with no API key and no request limits,
 * so nothing secret ships in the APK and a fresh clone renders maps with no setup.
 *
 * Checked on 2026-09-25 by fetching this exact URL: one style JSON whose tile source, glyphs and
 * sprite all live on tiles.openfreemap.org over HTTPS, with a native dark palette (background
 * rgb(12,12,12), close to the app's Neutral950), not a runtime colour inversion. OpenFreeMap
 * offers no SLA, which the retry state below already handles.
 */
private const val MAP_STYLE_URL = "https://tiles.openfreemap.org/styles/dark"

/** The credit OpenFreeMap, OpenMapTiles and OpenStreetMap each require, part by part with its link. */
private const val OPENFREEMAP_URL = "https://openfreemap.org"
private const val OPENMAPTILES_URL = "https://www.openmaptiles.org/"
private const val OSM_COPYRIGHT_URL = "https://www.openstreetmap.org/copyright"

/** Fallback camera center used only until the first real route point arrives. No longer tied to any
 * bundled data region now that tiles are fetched live for any location -- kept as a stable, familiar
 * default rather than because the map is limited to it. */
private val METRO_MANILA_CENTER = LatLng(14.5995, 120.9842)

/**
 * The zoom the map actually settles at. It's reached via a `FINAL_ZOOM - 1.0` -> `FINAL_ZOOM` step
 * (see the setStyle callback below) -- that exact two-step sequence is what's confirmed working
 * on-device; landing on this value directly on the first frame is not equivalent, nor is starting
 * one level *above* this and stepping down to it (untested, not assumed equivalent by symmetry).
 */
private const val FINAL_ZOOM = 12.0

/** M21c: street-level zoom used while actively following a live GPS fix. */
private const val FOLLOW_ZOOM = 16.0

/** Screen-pixel padding around a fitted route's bounding box -- untuned, revisit on-device if a route ever renders edge-to-edge. */
private const val ROUTE_BOUNDS_PADDING_PX = 96

private const val ROUTE_SOURCE_ID = "route-source"
private const val ROUTE_LAYER_ID = "route-layer"
private const val ENDPOINTS_SOURCE_ID = "route-endpoints-source"
private const val START_LAYER_ID = "route-start-layer"
private const val FINISH_LAYER_ID = "route-finish-layer"
private const val ENDPOINT_KIND = "kind"
private const val KIND_START = "start"
private const val KIND_FINISH = "finish"

/** Start and finish dots (2026-09-26): the first and last recorded point, finish only once there are two. */
private fun endpointsFeatures(points: List<Pair<Double, Double>>): FeatureCollection {
    val features = mutableListOf<Feature>()
    points.firstOrNull()?.let { (lat, lng) ->
        features += Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply { addStringProperty(ENDPOINT_KIND, KIND_START) }
    }
    if (points.size >= 2) {
        val (lat, lng) = points.last()
        features += Feature.fromGeometry(Point.fromLngLat(lng, lat)).apply { addStringProperty(ENDPOINT_KIND, KIND_FINISH) }
    }
    return FeatureCollection.fromFeatures(features)
}

private fun routeFeature(points: List<Pair<Double, Double>>): Feature =
    Feature.fromGeometry(LineString.fromLngLats(points.map { (lat, lng) -> Point.fromLngLat(lng, lat) }))

private fun routeBounds(points: List<Pair<Double, Double>>): LatLngBounds {
    val builder = LatLngBounds.Builder()
    points.forEach { (lat, lng) -> builder.include(LatLng(lat, lng)) }
    return builder.build()
}

/**
 * A route with (near-)zero real movement -- a workout where GPS never produced a usable fix (the
 * `adb emu geo fix` AVD limitation makes this the common on-device-testing case, not just a
 * theoretical edge case) -- has a degenerate bounding box. Confirmed on-device: fitting the camera
 * to that near-zero box via `newLatLngBounds` zooms in far past this app's z10-14 tileset's actual
 * data, rendering a flat, blank-looking tile with no visible roads/labels. Below this span, fall
 * back to a fixed reasonable zoom centered on the route instead of bounds-fitting.
 */
private const val MIN_BOUNDS_SPAN_DEGREES = 0.002

private fun isDegenerateRoute(points: List<Pair<Double, Double>>): Boolean {
    val lats = points.map { it.first }
    val lngs = points.map { it.second }
    val latSpan = (lats.max() - lats.min())
    val lngSpan = (lngs.max() - lngs.min())
    return latSpan < MIN_BOUNDS_SPAN_DEGREES && lngSpan < MIN_BOUNDS_SPAN_DEGREES
}

private fun routeCentroid(points: List<Pair<Double, Double>>): LatLng =
    LatLng(points.sumOf { it.first } / points.size, points.sumOf { it.second } / points.size)

/**
 * M21b, switched to live online tiles M21 (2026-09-12), from MapTiler to OpenFreeMap 2026-09-25.
 * A classic `MapView` wrapped in `AndroidView`, not `maplibre-compose` (that wrapper needs Kotlin
 * 2.4.10, ahead of this project's 2.3.0 pin — decisions.md/gradle/libs.versions.toml). The style is
 * fetched live ([MAP_STYLE_URL] above) rather than loaded from a bundled `.mbtiles` + local
 * style/glyphs/sprites -- this is LogEZ's only network access (Owner directive, decisions.md
 * 2026-09-12), narrowly scoped to map tile/style/glyph/sprite requests only. GPS tracking, workout
 * data, and Health Connect reads all stay fully local, unaffected. Replacing the old
 * Metro-Manila-only bundled map means the map now renders anywhere in the world, not just one city.
 *
 * The persistent, always-visible attribution text is deliberate, not decorative: MapLibre's own
 * default tap-to-reveal "(i)" control does not, on its own, satisfy OpenStreetMap's requirement
 * that attribution be visible without requiring a tap (M21b research, a real open MapLibre issue).
 * Each part of the credit is also a link to its source, as the OSM attribution guidelines ask.
 *
 * M21c: [routePoints] draws the GPS track as a line layer once 2+ points exist. [followLatest]
 * chooses how the camera reacts to it -- `true` (live tracking) keeps centering on the newest
 * point at street level as fixes arrive; `false` (a finished workout's recap) fits the camera to
 * the whole route's bounding box once. The route source/layer are always created empty during the
 * initial style load and populated afterward by a separate effect (never during the load callback
 * itself) so the already-proven-working first-paint zoom sequence below is never touched by
 * route-specific logic -- see that sequence's own comment for why it's this fragile.
 *
 * M21: while [followLatest] is true, a naive "re-center on every new fix" fights the user the
 * instant they try to pinch-zoom or drag -- the very next GPS fix (every few seconds) snaps the
 * camera straight back, which reads as "the map won't let me zoom or move it at all" (Owner
 * report, 2026-09-11), not as a momentary jump. [MapLibreMap.addOnCameraMoveStartedListener] can
 * tell a real touch gesture (`REASON_API_GESTURE`) apart from this composable's own `easeCamera`
 * calls (`REASON_API_ANIMATION`); [userPanned] latches true on the former and suppresses
 * auto-follow until the user taps the "recenter" button, exactly the pattern every real GPS-track
 * app (Strava, Nike Run Club, Google Maps' own blue-dot follow mode) uses for this same conflict.
 *
 * Walk/run summary (2026-09-26):
 * - [interactive] false turns every gesture off. A map inside a scrolling page otherwise takes the
 *   drag for itself and the page stops scrolling (the conflict ActivityTrackingScreen documents).
 *   [onMapClick] then makes a tap open something else, the summary's full-screen map.
 * - [showEndpoints] draws a start dot and a finish dot; recaps show them, live tracking doesn't.
 * - [fitPadding] replaces the default bounds padding per side, so a route can clear overlays such
 *   as the summary's top scrim and bottom fade. [attributionPadding] lifts the credit line clear
 *   of them too, keeping it visible as OpenStreetMap requires.
 */
@Composable
fun RouteMapView(
    modifier: Modifier = Modifier,
    routePoints: List<Pair<Double, Double>> = emptyList(),
    followLatest: Boolean = false,
    interactive: Boolean = true,
    onMapClick: (() -> Unit)? = null,
    showEndpoints: Boolean = !followLatest,
    fitPadding: PaddingValues? = null,
    attributionPadding: PaddingValues = PaddingValues(0.dp),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapLibreReady by remember { mutableStateOf(false) }
    var maplibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var styleReady by remember { mutableStateOf(false) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var userPanned by remember { mutableStateOf(false) }
    val routeLineColor = MaterialTheme.colorScheme.primary.toArgb()
    val startColor = MaterialTheme.colorScheme.primary.toArgb()
    val finishColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val endpointStrokeColor = MaterialTheme.colorScheme.background.toArgb()
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val fitPaddingPx = fitPadding?.let { padding ->
        with(density) {
            intArrayOf(
                padding.calculateLeftPadding(layoutDirection).roundToPx(),
                padding.calculateTopPadding().roundToPx(),
                padding.calculateRightPadding(layoutDirection).roundToPx(),
                padding.calculateBottomPadding().roundToPx(),
            )
        }
    }

    // Extracted so both the initial load and the Retry button below can (re)run it. Recap screens
    // (WorkoutDetailScreen/WorkoutSummaryScreen) already know their full route at this point, so the
    // zoom-dance below targets its centroid instead of unconditionally fetching Metro Manila tiles
    // for a workout that may be anywhere in the world -- live tracking has no route yet at this stage
    // and still falls back to the fixed default, which is the one case with no better data available.
    fun loadStyle(map: MapLibreMap) {
        loadError = null
        val initialTarget = if (routePoints.isNotEmpty()) routeCentroid(routePoints) else METRO_MANILA_CENTER
        map.setStyle(Style.Builder().fromUri(MAP_STYLE_URL)) { style ->
            style.addSource(GeoJsonSource(ROUTE_SOURCE_ID))
            style.addLayer(
                LineLayer(ROUTE_LAYER_ID, ROUTE_SOURCE_ID).withProperties(
                    PropertyFactory.lineColor(routeLineColor),
                    PropertyFactory.lineWidth(4f),
                    PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                    PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                ),
            )
            if (showEndpoints) {
                style.addSource(GeoJsonSource(ENDPOINTS_SOURCE_ID))
                // Finish first, so a start dot on top of it stays visible on a loop that ends
                // where it began.
                style.addLayer(endpointLayer(FINISH_LAYER_ID, KIND_FINISH, finishColor, endpointStrokeColor))
                style.addLayer(endpointLayer(START_LAYER_ID, KIND_START, startColor, endpointStrokeColor))
            }

            // See the class doc comment for why this exact two-step zoom sequence, with a real
            // delay between steps, is required for line/symbol layers to render at all.
            map.cameraPosition = CameraPosition.Builder()
                .target(initialTarget)
                .zoom(FINAL_ZOOM - 1.0)
                .build()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                map.cameraPosition = CameraPosition.Builder()
                    .target(initialTarget)
                    .zoom(FINAL_ZOOM)
                    .build()
                styleReady = true
            }, 1500)
        }
    }

    LaunchedEffect(Unit) {
        MapLibre.getInstance(context)
        mapLibreReady = true
    }

    Box(modifier = modifier) {
        // MapView's constructor calls MapLibre.hasInstance() and throws if it's false -- it must
        // never be constructed (even via remember, which runs during composition) before the
        // LaunchedEffect above has actually completed MapLibre.getInstance(), which only happens on
        // a later recomposition. This early return is what enforces that ordering.
        if (!mapLibreReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Box
        }

        val mapView = remember { MapView(context) }
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_CREATE -> mapView.onCreate(null)
                    Lifecycle.Event.ON_START -> mapView.onStart()
                    Lifecycle.Event.ON_RESUME -> mapView.onResume()
                    Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                    Lifecycle.Event.ON_STOP -> mapView.onStop()
                    Lifecycle.Event.ON_DESTROY -> mapView.onDestroy()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
                // A map can leave composition while the screen stays resumed (the summary swapping
                // its hero map for the full-screen one), so it never receives ON_PAUSE/ON_STOP.
                // MapView only releases its connectivity receiver and file source in onStop, not
                // onDestroy, so step it down first or each swap leaks one activation (2026-09-26).
                val state = lifecycleOwner.lifecycle.currentState
                if (state.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
                if (state.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
                mapView.onDestroy()
            }
        }

        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize()) { mv ->
            mv.getMapAsync { map ->
                if (maplibreMap != null) return@getMapAsync
                maplibreMap = map
                // No OnDidFailLoadingMapListener existed before this milestone because the bundled
                // local style/mbtiles could never fail this way. A live network fetch genuinely can
                // (no connectivity, a tile-server outage) -- without this,
                // `styleReady` would simply never flip and the loading spinner below would spin
                // forever with no way out, including for a past workout's route that used to render
                // 100% offline.
                mv.addOnDidFailLoadingMapListener { error -> loadError = error }
                // MapLibre's own default logo mark and tap-to-reveal attribution "(i)" icon are pure
                // SDK branding/decoration, not an OSM compliance requirement -- our own always-visible
                // attribution Text below already satisfies that on its own. Disabling these removes
                // the actual visual "watermark" without touching the legally-required credit line.
                map.uiSettings.isLogoEnabled = false
                map.uiSettings.isAttributionEnabled = false
                // Explicit, not relying on the SDK's own defaults -- this is the exact set of
                // gestures the "can't zoom or move the map" report (2026-09-11) was about.
                map.uiSettings.isZoomGesturesEnabled = interactive
                map.uiSettings.isScrollGesturesEnabled = interactive
                map.uiSettings.isRotateGesturesEnabled = interactive
                map.uiSettings.isTiltGesturesEnabled = interactive
                map.uiSettings.isDoubleTapGesturesEnabled = interactive
                map.uiSettings.isQuickZoomGesturesEnabled = interactive
                // A real pinch/drag (not this composable's own easeCamera calls) latches
                // userPanned -- see the class doc comment for why this exists.
                map.addOnCameraMoveStartedListener { reason ->
                    if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) {
                        userPanned = true
                    }
                }
                loadStyle(map)
            }
        }

        // Above the map, below the retry state and the attribution links, so both stay tappable.
        // Touch only: hidden from TalkBack, whose users get the caller's labelled button instead.
        if (!interactive && onMapClick != null) {
            Box(modifier = Modifier.fillMaxSize().clearAndSetSemantics {}.clickable(onClick = onMapClick))
        }

        // Shown until the style has fetched over the network and the zoom-dance above has settled --
        // MapView itself renders a blank tile grid underneath while that's in flight. A failure
        // (no connectivity, a tile-server outage) shows a retry affordance instead of
        // spinning forever -- see loadStyle()'s and the failure listener's own comments above.
        if (loadError != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.map_load_error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { maplibreMap?.let(::loadStyle) },
                        modifier = Modifier.padding(top = Spacing.sm),
                    ) {
                        Text(stringResource(R.string.map_retry))
                    }
                }
            }
        } else if (!styleReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        val linkStyles = TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))
        val openFreeMap = stringResource(R.string.map_attribution_openfreemap)
        val openMapTiles = stringResource(R.string.map_attribution_openmaptiles)
        val openStreetMap = stringResource(R.string.map_attribution_osm)
        Text(
            buildAnnotatedString {
                withLink(LinkAnnotation.Url(OPENFREEMAP_URL, linkStyles)) { append(openFreeMap) }
                append(" ")
                withLink(LinkAnnotation.Url(OPENMAPTILES_URL, linkStyles)) { append(openMapTiles) }
                append(" ")
                withLink(LinkAnnotation.Url(OSM_COPYRIGHT_URL, linkStyles)) { append(openStreetMap) }
            },
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(attributionPadding)
                .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )

        // Only ever meaningful while actively following -- a static recap/history map has no
        // "latest fix" to snap back to, and isn't fought by a repeating auto-follow call in the
        // first place (its own bounds-fit only runs once, when routePoints first arrives).
        if (followLatest && userPanned) {
            Surface(
                onClick = {
                    userPanned = false
                    val (lat, lng) = routePoints.lastOrNull() ?: return@Surface
                    maplibreMap?.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), FOLLOW_ZOOM))
                },
                modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.sm).size(44.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Outlined.MyLocation,
                        contentDescription = stringResource(R.string.map_recenter),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    // Owns every route-specific update: the initial population once styleReady flips true, and
    // every subsequent growth during live tracking. Never touches the style-load callback above.
    LaunchedEffect(routePoints, styleReady) {
        if (!styleReady) return@LaunchedEffect
        val map = maplibreMap ?: return@LaunchedEffect
        val style = map.style ?: return@LaunchedEffect
        val source = style.getSourceAs<GeoJsonSource>(ROUTE_SOURCE_ID) ?: return@LaunchedEffect

        if (routePoints.size >= 2) source.setGeoJson(routeFeature(routePoints))
        if (showEndpoints && routePoints.isNotEmpty()) {
            style.getSourceAs<GeoJsonSource>(ENDPOINTS_SOURCE_ID)?.setGeoJson(endpointsFeatures(routePoints))
        }

        // userPanned: don't fight a gesture the user is mid-way through -- see the class doc
        // comment. The recenter button (above) is the only way back to auto-follow from here.
        if (userPanned) return@LaunchedEffect

        if (followLatest) {
            if (routePoints.isNotEmpty()) {
                val (lat, lng) = routePoints.last()
                map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), FOLLOW_ZOOM))
            }
        } else if (routePoints.size == 1 || (routePoints.size >= 2 && isDegenerateRoute(routePoints))) {
            // One usable fix, or a route too small to fit: centre on it at street level.
            map.easeCamera(CameraUpdateFactory.newLatLngZoom(routeCentroid(routePoints), FOLLOW_ZOOM))
        } else if (routePoints.size >= 2) {
            val bounds = routeBounds(routePoints)
            val update = fitPaddingPx?.let { (left, top, right, bottom) ->
                CameraUpdateFactory.newLatLngBounds(bounds, left, top, right, bottom)
            } ?: CameraUpdateFactory.newLatLngBounds(bounds, ROUTE_BOUNDS_PADDING_PX)
            map.easeCamera(update)
        }
    }
}

private fun endpointLayer(id: String, kind: String, fill: Int, stroke: Int): CircleLayer =
    CircleLayer(id, ENDPOINTS_SOURCE_ID)
        .withFilter(Expression.eq(Expression.get(ENDPOINT_KIND), Expression.literal(kind)))
        .withProperties(
            PropertyFactory.circleRadius(6.5f),
            PropertyFactory.circleColor(fill),
            PropertyFactory.circleStrokeColor(stroke),
            PropertyFactory.circleStrokeWidth(3f),
        )
