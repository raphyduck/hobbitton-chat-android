package com.garfiec.librechat.feature.settings.screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.garfiec.librechat.core.data.datastore.InlineArtifactPrefs
import com.garfiec.librechat.feature.settings.resources.*
import com.garfiec.librechat.feature.settings.resources.Res
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun ArtifactSettingsSection(
    prefs: InlineArtifactPrefs,
    onMermaidChange: (Boolean) -> Unit,
    onSvgChange: (Boolean) -> Unit,
    onHtmlChange: (Boolean) -> Unit,
    onReactChange: (Boolean) -> Unit,
    onMarkdownChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = stringResource(Res.string.artifacts_section_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))

            ArtifactToggleRow(
                title = stringResource(Res.string.inline_artifact_mermaid),
                description = stringResource(Res.string.inline_artifact_mermaid_desc),
                checked = prefs.mermaid,
                onChange = onMermaidChange,
            )
            Spacer(modifier = Modifier.height(12.dp))

            ArtifactToggleRow(
                title = stringResource(Res.string.inline_artifact_svg),
                description = stringResource(Res.string.inline_artifact_svg_desc),
                checked = prefs.svg,
                onChange = onSvgChange,
            )
            Spacer(modifier = Modifier.height(12.dp))

            ArtifactToggleRow(
                title = stringResource(Res.string.inline_artifact_markdown),
                description = stringResource(Res.string.inline_artifact_markdown_desc),
                checked = prefs.markdown,
                onChange = onMarkdownChange,
            )
            Spacer(modifier = Modifier.height(12.dp))

            ArtifactToggleRow(
                title = stringResource(Res.string.inline_artifact_html),
                description = stringResource(Res.string.inline_artifact_html_desc),
                checked = prefs.html,
                onChange = onHtmlChange,
            )
            Spacer(modifier = Modifier.height(12.dp))

            ArtifactToggleRow(
                title = stringResource(Res.string.inline_artifact_react),
                description = stringResource(Res.string.inline_artifact_react_desc),
                checked = prefs.react,
                onChange = onReactChange,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun ArtifactToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
        )
    }
}
