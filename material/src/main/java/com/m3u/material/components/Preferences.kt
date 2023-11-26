package com.m3u.material.components

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalAbsoluteTonalElevation
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun Preference(
    title: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: String? = null,
    onClick: () -> Unit = {},
    trailing: @Composable () -> Unit = {}
) {
    var focus by remember { mutableStateOf(false) }

    TooltipBox(
        state = rememberTooltipState(),
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = {
            if (!content.isNullOrEmpty()) {
                PlainTooltip {
                    Text(
                        text = content.capitalize(Locale.current)
                    )
                }
            }
        }
    ) {
        ElevatedCard(
            shape = RectangleShape,
            enabled = enabled,
            onClick = onClick,
            elevation = CardDefaults.elevatedCardElevation(4.dp)
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                supportingContent = {
                    if (content != null) {
                        Text(
                            text = content.capitalize(Locale.current),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .let {
                                    if (focus) it.basicMarquee()
                                    else it
                                }
                        )
                    }
                },
                trailingContent = trailing,
                tonalElevation = LocalAbsoluteTonalElevation.current,
                modifier = modifier
                    .fillMaxWidth()
                    .onFocusChanged {
                        focus = it.hasFocus
                    }
                    .focusable()
            )
        }
    }
}

@Composable
fun CheckBoxPreference(
    title: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: String? = null,
    enabled: Boolean = true,
) {
    val combined = Modifier
        .toggleable(
            value = checked,
            onValueChange = { onChanged(it) },
            role = Role.Checkbox,
            enabled = enabled
        )
        .then(modifier)
    Preference(
        title = title,
        content = content,
        enabled = enabled,
        onClick = {
            if (enabled) {
                onChanged(!checked)
            }
        },
        modifier = combined,
        trailing = {
            Checkbox(
                enabled = enabled,
                checked = checked,
                onCheckedChange = null
            )
        }
    )
}

@Composable
fun SwitchPreference(
    title: String,
    checked: Boolean,
    onChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: String? = null,
    enabled: Boolean = true,
) {
    val combined = Modifier
        .toggleable(
            value = checked,
            onValueChange = { onChanged(it) },
            role = Role.Checkbox
        )
        .then(modifier)
    Preference(
        title = title,
        content = content,
        enabled = enabled,
        onClick = {
            if (enabled) {
                onChanged(!checked)
            }
        },
        modifier = combined,
        trailing = {
            Switch(
                enabled = enabled,
                checked = checked,
                onCheckedChange = null
            )
        }
    )
}

@Composable
fun IconPreference(
    title: String,
    imageVector: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: String? = null,
    enabled: Boolean = true,
) {
    Preference(
        title = title,
        content = content,
        enabled = enabled,
        onClick = onClick,
        modifier = modifier,
        trailing = {
            Icon(
                imageVector = imageVector,
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.65f)
            )
        }
    )
}

@Composable
fun TextPreference(
    title: String,
    trailing: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: String? = null,
    enabled: Boolean = true,
) {
    Preference(
        title = title,
        content = content,
        enabled = enabled,
        onClick = {
            if (enabled) onClick()
        },
        modifier = modifier,
        trailing = {
            Text(
                text = trailing.uppercase(),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    )
}
