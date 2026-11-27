package com.enil.logez.core.domain.model

/** The one place that knows which templates exist, which kinds they serve, and what the defaults are. */
object ShareTemplateRegistry {
    val all: List<ShareTemplate> = ShareTemplate.entries

    val defaultTemplate: ShareTemplate = ShareTemplate.STACK

    fun forKind(kind: WorkoutKind): List<ShareTemplate> = all.filter { kind in it.kinds }

    fun supports(
        template: ShareTemplate,
        kind: WorkoutKind,
    ): Boolean = kind in template.kinds

    /**
     * Stack on Dark with Light text, every metric on, and only Start time off (the mockups' own
     * default). Dark and Lime are semantic names, not colours: the renderer resolves them from the
     * app's Material 3 colour scheme so the default follows the theme.
     */
    fun defaultChoice(kind: WorkoutKind): ShareTemplateChoice =
        ShareTemplateChoice(
            template = defaultTemplate,
            customization =
                ShareCustomization(
                    background = ShareBackground.DARK,
                    textTone = ShareTextTone.LIGHT,
                    metrics = ShareMetric.forKind(kind),
                    details = ShareDetail.forKind(kind) - ShareDetail.START_TIME,
                    numberMetric =
                        when (kind) {
                            WorkoutKind.STRENGTH -> ShareMetric.VOLUME
                            WorkoutKind.GPS_TRACKED -> ShareMetric.DISTANCE
                        },
                ),
        )
}
