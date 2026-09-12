package com.hostchecker.pro.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostchecker.pro.ui.theme.AccentCyan
import com.hostchecker.pro.ui.theme.AppBarTeal
import com.hostchecker.pro.ui.theme.Background
import com.hostchecker.pro.ui.theme.Divider as DividerColor
import com.hostchecker.pro.ui.theme.Primary
import com.hostchecker.pro.ui.theme.Status2xx
import com.hostchecker.pro.ui.theme.StatusFailed
import com.hostchecker.pro.ui.theme.SurfaceVariant
import com.hostchecker.pro.ui.theme.TextPrimary
import com.hostchecker.pro.ui.theme.TextSecondary
import com.hostchecker.pro.ui.theme.Warning
import com.hostchecker.pro.ui.viewmodel.DetailViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResultDetailScreen(
    resultId: Long,
    viewModel: DetailViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val result by viewModel.result.collectAsStateWithLifecycle()
    val isRescanning by viewModel.isRescanning.collectAsStateWithLifecycle()
    var requestDetailsExpanded by remember { mutableStateOf(true) }
    var headersExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(resultId) {
        viewModel.loadResult(resultId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Host Details",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.rescanHost() },
                        enabled = !isRescanning,
                        modifier = Modifier.testTag("rescan_button")
                    ) {
                        if (isRescanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Primary,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Re-scan host",
                                tint = AccentCyan
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBarTeal),
                modifier = Modifier.testTag("result_detail_top_app_bar")
            )
        },
        containerColor = Background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        val item = result
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Primary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // Main Info Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    border = BorderStroke(1.dp, DividerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Host Header
                        Text(
                            text = item.host,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = AccentCyan
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        val rawAsn = item.asn.ifBlank { "" }
                        val asnDisplay = if (rawAsn.isBlank() || rawAsn.equals("UNKNOWN", ignoreCase = true) || rawAsn.equals("no-asn", ignoreCase = true)) "—" else rawAsn
                        val orgDisplay = if (item.org.isNotBlank() && !item.org.equals("Unknown", ignoreCase = true)) " (${item.org})" else ""
                        val asnOrgValue = if (asnDisplay == "—" && orgDisplay.isEmpty()) "—" else "$asnDisplay$orgDisplay"
                        val faviconDisplay = if (item.faviconHash.isBlank() || item.faviconHash == "-1" || item.faviconHash == "0") "—" else item.faviconHash

                        DetailRow(label = "Status", value = if (item.failed) "Failed (${item.errorMessage})" else "${item.code} OK")
                        DetailRow(label = "Response Time", value = "${item.ms} ms")
                        DetailRow(label = "IP Address", value = item.ip.ifBlank { "—" })
                        DetailRow(label = "ASN / Org", value = asnOrgValue)
                        DetailRow(label = "Server", value = item.server.ifBlank { "—" })
                        DetailRow(label = "Page Title", value = item.title.ifBlank { "—" })
                        DetailRow(label = "Favicon MMH3", value = faviconDisplay)
                    }
                }

                // Collapsible Request Details Section (for Debugging and Accuracy Verification)
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    border = BorderStroke(1.dp, DividerColor),
                    modifier = Modifier.fillMaxWidth().testTag("request_details_card")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { requestDetailsExpanded = !requestDetailsExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Request Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Icon(
                                imageVector = if (requestDetailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (requestDetailsExpanded) "Collapse" else "Expand",
                                tint = TextSecondary
                            )
                        }

                        if (requestDetailsExpanded) {
                            Spacer(modifier = Modifier.height(12.dp))
                            val reqUrl = item.requestedUrl.ifBlank { "${item.scheme.lowercase()}://${item.host}" }
                            val finUrl = item.finalUrl.ifBlank { reqUrl }
                            val origStatus = if (item.originalCode > 0) "${item.originalCode}" else if (!item.failed) "${item.code}" else "—"
                            val finStatus = if (item.finalCode > 0) "${item.finalCode}" else if (!item.failed) "${item.code}" else "—"
                            val chainDisplay = if (item.redirectChain.isNotEmpty()) {
                                item.redirectChain.joinToString("\n↳ ")
                            } else if (item.redirectCount > 0) {
                                "${item.redirectCount} redirect(s)"
                            } else {
                                "None (direct response)"
                            }

                            DetailRow(label = "Requested URL", value = reqUrl)
                            DetailRow(label = "Protocol (Scheme)", value = item.scheme)
                            DetailRow(label = "HTTP Method", value = item.httpMethod)
                            DetailRow(label = "Original Status", value = origStatus)
                            DetailRow(label = "Final Status", value = finStatus)
                            DetailRow(label = "Final URL", value = finUrl)
                            DetailRow(label = "Redirect Chain", value = chainDisplay)
                            DetailRow(label = "Resolved IP", value = item.ip.ifBlank { "—" })
                            DetailRow(label = "Response Time", value = "${item.ms} ms")
                            DetailRow(label = "Server Header", value = item.server.ifBlank { "—" })
                            DetailRow(
                                label = "Error",
                                value = if (item.failed) item.errorMessage.ifBlank { "Network failure" } else "None"
                            )
                        }
                    }
                }

                // Cloudflare Origin Leak Card (if fronted)
                if (item.cloudflareFronted) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Warning.copy(alpha = 0.1f)),
                        border = BorderStroke(1.dp, Warning),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = Warning,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Cloudflare Fronted",
                                    fontWeight = FontWeight.Bold,
                                    color = Warning,
                                    fontSize = 16.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (item.cloudflareOrigin.isNotBlank()) {
                                    item.cloudflareOrigin
                                } else {
                                    "Server matches Cloudflare CDN, but direct/origin subdomain probes did not leak the origin IP."
                                },
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                // SSL Certificate Subject Alternative Names (SAN)
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    border = BorderStroke(1.dp, DividerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "SSL Certificate SANs (${item.san.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        if (item.san.isEmpty()) {
                            Text(
                                text = "No SAN entries discovered or SSL not configured.",
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        } else {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                item.san.forEach { san ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = AppBarTeal,
                                        border = BorderStroke(1.dp, AccentCyan.copy(alpha = 0.5f))
                                    ) {
                                        Text(
                                            text = san,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 12.sp,
                                            color = AccentCyan,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // HTTP Headers (Collapsible Card)
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    border = BorderStroke(1.dp, DividerColor),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { headersExpanded = !headersExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "HTTP Headers (${item.headers.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Icon(
                                imageVector = if (headersExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (headersExpanded) "Collapse" else "Expand",
                                tint = TextSecondary
                            )
                        }

                        AnimatedVisibility(visible = headersExpanded) {
                            Column(modifier = Modifier.padding(top = 12.dp)) {
                                if (item.headers.isEmpty()) {
                                    Text("No headers captured.", color = TextSecondary, fontSize = 13.sp)
                                } else {
                                    item.headers.forEach { (name, value) ->
                                        Row(modifier = Modifier.padding(vertical = 3.dp)) {
                                            Text(
                                                text = "$name: ",
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Bold,
                                                color = AccentCyan,
                                                fontSize = 12.sp
                                            )
                                            Text(
                                                text = value,
                                                fontFamily = FontFamily.Monospace,
                                                color = TextPrimary,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Action Buttons Row: Copy Host, Copy IP, Open Browser
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("Host", item.host))
                            Toast.makeText(context, "Host copied", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Host", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = {
                            if (item.ip.isNotBlank()) {
                                val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cb.setPrimaryClip(ClipData.newPlainText("IP", item.ip))
                                Toast.makeText(context, "IP copied", Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = item.ip.isNotBlank(),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("IP", fontSize = 12.sp)
                    }

                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://${item.host}"))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1.2f)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Browser", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Text(
            text = value,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary,
            fontSize = 13.sp,
            softWrap = true,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}
