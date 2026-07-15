package com.jovi.photoai.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jovi.photoai.ui.design.AppColors
import com.jovi.photoai.ui.design.AppDimensions

@Composable
fun ReferencePhotoCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    badge: String = "示例",
    contentDescription: String = "$title，$badge",
    onClick: (() -> Unit)? = null,
    image: @Composable BoxScope.() -> Unit
) {
    val interactionModifier = if (onClick == null) {
        Modifier
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    GlassSurface(
        modifier = modifier
            .then(interactionModifier)
            .semantics { this.contentDescription = contentDescription },
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .clip(RoundedCornerShape(
                        topStart = AppDimensions.RadiusLarge,
                        topEnd = AppDimensions.RadiusLarge
                    )),
                content = image
            )
            Column(
                modifier = Modifier.padding(AppDimensions.Space16),
                verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.width(AppDimensions.Space8))
                    GlassPill(text = badge)
                }
                Text(
                    text = subtitle,
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SceneCategoryChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val background = if (selected) AppColors.AccentBlueSoft else AppColors.SurfacePrimary
    val contentColor = if (selected) AppColors.AccentBlue else AppColors.TextSecondary
    androidx.compose.material3.Surface(
        modifier = modifier
            .heightIn(min = AppDimensions.MinTouchTarget)
            .semantics { this.selected = selected },
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(percent = 50),
        color = background,
        contentColor = contentColor,
        border = androidx.compose.foundation.BorderStroke(
            AppDimensions.GlassStroke,
            if (selected) AppColors.AccentBlue.copy(alpha = 0.24f) else AppColors.Divider
        )
    ) {
        Box(
            modifier = Modifier.padding(
                horizontal = AppDimensions.Space16,
                vertical = AppDimensions.Space12
            ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun AnalysisSection(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    accentColor: Color = AppColors.AccentBlue
) {
    GlassSurface(modifier = modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(AppDimensions.Space8)) {
            if (label != null) {
                Text(
                    text = label,
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Text(text = title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = body,
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
fun EmptyState(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    illustration: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier.padding(AppDimensions.Space24),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppDimensions.Space12)
    ) {
        illustration?.invoke()
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = message,
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyLarge
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(AppDimensions.Space4))
            PrimaryActionButton(
                text = actionLabel,
                onClick = onAction,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
