package com.hostchecker.pro.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostchecker.pro.domain.model.Session
import com.hostchecker.pro.ui.components.SessionCard
import com.hostchecker.pro.ui.theme.AccentCyan
import com.hostchecker.pro.ui.theme.AppBarTeal
import com.hostchecker.pro.ui.theme.Background
import com.hostchecker.pro.ui.theme.Error
import com.hostchecker.pro.ui.theme.Primary
import com.hostchecker.pro.ui.theme.SurfaceVariant
import com.hostchecker.pro.ui.theme.TextPrimary
import com.hostchecker.pro.ui.theme.TextSecondary
import com.hostchecker.pro.ui.viewmodel.SessionListViewModel
import com.hostchecker.pro.util.FileUtil
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    viewModel: SessionListViewModel,
    onNavigateToSetupWithFile: (uriString: String) -> Unit,
    onNavigateToSetupWithText: (rawText: String) -> Unit,
    onOpenSession: (sessionId: Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showTextInputDialog by remember { mutableStateOf(false) }
    var pastedText by remember { mutableStateOf("") }
    var showSourceChoiceDialog by remember { mutableStateOf(false) }

    // File picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            onNavigateToSetupWithFile(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Host Checker Pro",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppBarTeal
                ),
                modifier = Modifier.testTag("session_list_top_app_bar")
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showSourceChoiceDialog = true },
                containerColor = Primary,
                contentColor = TextPrimary,
                shape = CircleShape,
                modifier = Modifier.testTag("new_scan_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Scan"
                )
            }
        },
        containerColor = Background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("empty_state_view"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Dns,
                        contentDescription = null,
                        tint = AccentCyan.copy(alpha = 0.6f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No sessions yet",
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Tap + to start a new host scan from a file or pasted domain list.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag("sessions_lazy_column"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = sessions,
                    key = { it.id }
                ) { session ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            if (value == SwipeToDismissBoxValue.EndToStart) {
                                viewModel.deleteSession(context, session)
                                true
                            } else false
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Error, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 20.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Session",
                                    tint = Color.White
                                )
                            }
                        }
                    ) {
                        SessionCard(
                            session = session,
                            onClick = { onOpenSession(session.id) }
                        )
                    }
                }
            }
        }
    }

    // Dialog: Choose Source (Select File vs Paste Hosts)
    if (showSourceChoiceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceChoiceDialog = false },
            containerColor = SurfaceVariant,
            title = {
                Text(
                    text = "New Scan Source",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Select a host list file (.txt, .list, .csv) or paste domain names directly.",
                    color = TextSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSourceChoiceDialog = false
                        filePickerLauncher.launch(
                            arrayOf(
                                "text/plain",
                                "text/comma-separated-values",
                                "text/csv",
                                "*/*"
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary)
                ) {
                    Text("Select File", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSourceChoiceDialog = false
                        showTextInputDialog = true
                    }
                ) {
                    Text("Paste Hosts", color = AccentCyan)
                }
            }
        )
    }

    // Dialog: Paste Hosts
    if (showTextInputDialog) {
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            containerColor = SurfaceVariant,
            title = {
                Text(
                    text = "Paste Host / Domain List",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter one host per line. Lines starting with # are ignored.",
                        fontSize = 12.sp,
                        color = TextSecondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = pastedText,
                        onValueChange = { pastedText = it },
                        placeholder = { Text("example.com\ndirect.example.com\n104.21.5.12") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .testTag("paste_hosts_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTextInputDialog = false
                        if (pastedText.isNotBlank()) {
                            onNavigateToSetupWithText(pastedText)
                            pastedText = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    enabled = pastedText.isNotBlank()
                ) {
                    Text("Continue", color = TextPrimary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}
