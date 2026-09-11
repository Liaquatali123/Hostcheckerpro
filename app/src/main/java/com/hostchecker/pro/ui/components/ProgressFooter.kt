package com.hostchecker.pro.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostchecker.pro.ui.theme.AccentCyan
import com.hostchecker.pro.ui.theme.Background
import com.hostchecker.pro.ui.theme.Primary
import com.hostchecker.pro.ui.theme.Surface
import com.hostchecker.pro.ui.theme.SurfaceVariant
import com.hostchecker.pro.ui.theme.TextPrimary
import com.hostchecker.pro.ui.theme.TextSecondary
import com.hostchecker.pro.ui.theme.Warning
import java.util.Locale

@Composable
fun ProgressFooter(
    scanned: Int,
    total: Int,
    elapsedSeconds: Long,
    hostsPerSecond: Double,
    modifier: Modifier = Modifier
) {
    val progress = if (total > 0) (scanned.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "progressAnimation"
    )

    val remaining = (total - scanned).coerceAtLeast(0)

    // Format Time: mm:ss or hh:mm:ss
    val formattedTime = formatElapsed(elapsedSeconds)

    // Calculate ETA
    val etaText = if (hostsPerSecond > 0.05 && remaining > 0) {
        val remainingSeconds = (remaining / hostsPerSecond).toLong()
        formatEta(remainingSeconds)
    } else if (remaining == 0 && total > 0) {
        "0s"
    } else {
        "--"
    }

    val progressPercent = String.format(Locale.US, "%.1f%%", progress * 100)
    val speedText = if (hostsPerSecond > 0) String.format(Locale.US, "%.0f/s", hostsPerSecond) else "0/s"

    Surface(
        color = Surface,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("progress_footer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Row 1: Time | Remaining
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Time: ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = formattedTime,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = Warning,
                        fontSize = 13.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Remaining: ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Text(
                        text = "$remaining",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // LinearProgressIndicator with rounded ends
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = Primary,
                trackColor = SurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Progress % | Speed | ETA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Progress: $progressPercent",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = AccentCyan
                )

                Text(
                    text = "Speed: $speedText",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = TextPrimary
                )

                Text(
                    text = "ETA: $etaText",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = Warning
                )
            }
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }
}

private fun formatEta(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hrs > 0 -> "${hrs}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}
