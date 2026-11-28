package com.enil.logez.feature.workout.finish.share

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.enil.logez.R
import com.enil.logez.core.designsystem.Spacing
import com.enil.logez.core.domain.model.ShareTemplate

internal const val SHARE_TEMPLATE_PICKER_TAG = "share_template_picker"

@Composable
internal fun ShareTemplatePicker(
    options: List<ShareTemplate>,
    selected: ShareTemplate,
    onSelect: (ShareTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth().testTag(SHARE_TEMPLATE_PICKER_TAG),
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        items(options, key = { it.name }) { template ->
            FilterChip(
                selected = template == selected,
                onClick = { onSelect(template) },
                label = { Text(stringResource(template.labelRes())) },
            )
        }
    }
}

@StringRes
internal fun ShareTemplate.labelRes(): Int =
    when (this) {
        ShareTemplate.STACK -> R.string.share_template_stack
        ShareTemplate.NUMBER -> R.string.share_template_number
        ShareTemplate.RECORDS -> R.string.share_template_records
        ShareTemplate.GLASS -> R.string.share_template_glass
        ShareTemplate.RAIL -> R.string.share_template_rail
        ShareTemplate.STRIP -> R.string.share_template_strip
        ShareTemplate.BODY_MAP -> R.string.share_template_body_map
        ShareTemplate.ROUTE -> R.string.share_template_route
        ShareTemplate.SPLITS -> R.string.share_template_splits
        ShareTemplate.SUMMARY -> R.string.share_template_summary
        ShareTemplate.MUSCLES -> R.string.share_template_muscles
        ShareTemplate.LOG -> R.string.share_template_log
        ShareTemplate.STREAK -> R.string.share_template_streak
    }
