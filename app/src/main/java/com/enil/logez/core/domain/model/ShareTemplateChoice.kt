package com.enil.logez.core.domain.model

data class ShareCustomization(
    val background: ShareBackground,
    val textTone: ShareTextTone,
    val metrics: Set<ShareMetric>,
    val details: Set<ShareDetail>,
    val numberMetric: ShareMetric,
)

data class ShareTemplateChoice(
    val template: ShareTemplate,
    val customization: ShareCustomization,
)
