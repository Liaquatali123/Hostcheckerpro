package com.hostchecker.pro.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostchecker.pro.domain.model.ScanResult
import com.hostchecker.pro.ui.theme.AccentCyan
import com.hostchecker.pro.ui.theme.Divider
import com.hostchecker.pro.ui.theme.Error
import com.hostchecker.pro.ui.theme.Primary
import com.hostchecker.pro.ui.theme.Status2xx
import com.hostchecker.pro.ui.theme.Status3xx
import com.hostchecker.pro.ui.theme.Status4xx
import com.hostchecker.pro.ui.theme.Status5xx
import com.hostchecker.pro.ui.theme.StatusFailed
import com.hostchecker.pro.ui.theme.Success
import com.hostchecker.pro.ui.theme.SurfaceVariant
import com.hostchecker.pro.ui.theme.TextPrimary
import com.hostchecker.pro.ui.theme.TextSecondary
import com.hostchecker.pro.ui.theme.Warning

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ResultCard(
    result: ScanResult,
    isSelected: Boolean = false,
    isSelectionMode: Boolean = false,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        result.failed -> StatusFailed
        result.code in 200..299 -> Status2xx
        result.code in 300..399 -> Status3xx
        result.code in 400..499 -> Status4xx
        result.code >= 500 -> Status5xx
        else -> StatusFailed
    }

    val border = when {
        isSelected -> BorderStroke(2.dp, Primary)
        !result.failed -> BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
        else -> BorderStroke(1.dp, Divider)
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) SurfaceVariant.copy(alpha = 0.9f) else SurfaceVariant
        ),
        border = border,
        modifier = modifier
            .fillMaxWidth()
            .testTag("result_card_${result.id}")
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(
                        checkedColor = Primary,
                        uncheckedColor = TextSecondary
                    ),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                // Host Header + Status code badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = result.host,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = AccentCyan,
                        fontSize = 15.sp,
                        maxLines = 1,
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState())
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = statusColor.copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.8f))
                    ) {
                        Text(
                            text = if (result.failed) "FAIL" else "${result.code}",
                            color = statusColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Sub-line 1: Server • IP • ASN (maxLines = 2, no truncation)
                val serverText = if (result.server.isNotBlank()) result.server else "—"
                val ipText = if (result.ip.isNotBlank()) result.ip else "—"
                val rawAsn = if (result.asn.isNotBlank()) result.asn else result.org
                val asnText = if (rawAsn.isBlank() || rawAsn.equals("UNKNOWN", ignoreCase = true) || rawAsn.equals("no-asn", ignoreCase = true)) "—" else rawAsn

                Text(
                    text = "Server: $serverText  •  IP: $ipText  •  ASN: $asnText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Sub-line 2: Code • Response Time • Title (maxLines = 2, no truncation)
                val codeText = if (result.failed) "Failed" else "Code: ${result.code}"
                val msText = if (result.ms > 0) "${result.ms}ms" else ""
                val titleText = if (result.title.isNotBlank()) "Title: ${result.title}" else if (result.failed && result.errorMessage.isNotBlank()) "Err: ${result.errorMessage}" else ""

                val line2Parts = listOf(codeText, msText, titleText).filter { it.isNotBlank() }
                Text(
                    text = line2Parts.joinToString("  •  "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true
                )

                // Sub-line 3: Favicon hash - ALWAYS rendered, even if missing ("—")
                val hashDisplay = if (result.faviconHash.isBlank() || result.faviconHash == "-1" || result.faviconHash == "0") "—" else result.faviconHash
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Favicon Hash: $hashDisplay",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = TextSecondary.copy(alpha = 0.85f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    softWrap = true
                )

                if (result.cloudflareFronted && result.cloudflareOrigin.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Warning.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Warning.copy(alpha = 0.6f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Cloudflare origin leak",
                                tint = Warning,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "CF-FRONTED: ${result.cloudflareOrigin}",
                                color = Warning,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
