package com.hostchecker.pro.ui.components

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostchecker.pro.domain.export.Exporter
import com.hostchecker.pro.ui.theme.AccentCyan
import com.hostchecker.pro.ui.theme.Divider
import com.hostchecker.pro.ui.theme.Primary
import com.hostchecker.pro.ui.theme.Surface
import com.hostchecker.pro.ui.theme.SurfaceVariant
import com.hostchecker.pro.ui.theme.TextPrimary
import com.hostchecker.pro.ui.theme.TextSecondary

enum class ExportDestination {
    DOWNLOADS, SHARE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    sheetState: SheetState,
    hasSelected: Boolean,
    onDismiss: () -> Unit,
    onExport: (format: Exporter.Format, scope: Exporter.Scope, destination: ExportDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFormat by remember { mutableStateOf(Exporter.Format.CSV) }
    var selectedScope by remember {
        mutableStateOf(if (hasSelected) Exporter.Scope.SELECTED else Exporter.Scope.ALL)
    }
    var selectedDestination by remember { mutableStateOf(ExportDestination.SHARE) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Surface,
        contentColor = TextPrimary,
        modifier = modifier.testTag("export_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(
                text = "Export Results",
                style = MaterialTheme.typography.titleLarge,
                color = AccentCyan,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 1. FORMAT (FilterChip Row)
            Text(
                text = "FORMAT",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Exporter.Format.entries.forEach { format ->
                    FilterChip(
                        selected = selectedFormat == format,
                        onClick = { selectedFormat = format },
                        label = { Text(format.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary,
                            selectedLabelColor = TextPrimary,
                            containerColor = SurfaceVariant,
                            labelColor = TextSecondary
                        ),
                        border = BorderStroke(1.dp, if (selectedFormat == format) Primary else Divider),
                        modifier = Modifier.testTag("format_${format.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. SCOPE (RadioButtons)
            Text(
                text = "SCOPE",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                val scopes = listOf(
                    Exporter.Scope.ALL to "All scanned hosts",
                    Exporter.Scope.LIVE_ONLY to "Live hosts only (responded)",
                    Exporter.Scope.SELECTED to "Selected hosts only"
                )

                scopes.forEach { (scope, label) ->
                    val enabled = scope != Exporter.Scope.SELECTED || hasSelected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = selectedScope == scope,
                                onClick = { if (enabled) selectedScope = scope },
                                role = Role.RadioButton,
                                enabled = enabled
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedScope == scope,
                            onClick = null,
                            enabled = enabled,
                            colors = RadioButtonDefaults.colors(
                                selectedColor = Primary,
                                unselectedColor = TextSecondary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            color = if (enabled) TextPrimary else TextSecondary.copy(alpha = 0.5f),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. DESTINATION
            Text(
                text = "DESTINATION",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedDestination == ExportDestination.SHARE,
                    onClick = { selectedDestination = ExportDestination.SHARE },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = if (selectedDestination == ExportDestination.SHARE) TextPrimary else TextSecondary
                        )
                    },
                    label = { Text("Share") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary,
                        selectedLabelColor = TextPrimary,
                        containerColor = SurfaceVariant,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier.weight(1f)
                )

                FilterChip(
                    selected = selectedDestination == ExportDestination.DOWNLOADS,
                    onClick = { selectedDestination = ExportDestination.DOWNLOADS },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = if (selectedDestination == ExportDestination.DOWNLOADS) TextPrimary else TextSecondary
                        )
                    },
                    label = { Text("Save to Storage") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary,
                        selectedLabelColor = TextPrimary,
                        containerColor = SurfaceVariant,
                        labelColor = TextSecondary
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // EXPORT BUTTON
            Button(
                onClick = {
                    onExport(selectedFormat, selectedScope, selectedDestination)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("export_confirm_button")
            ) {
                Text(
                    text = "EXPORT",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
