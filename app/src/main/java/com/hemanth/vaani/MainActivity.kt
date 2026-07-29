package com.hemanth.vaani

import android.Manifest
import android.app.role.RoleManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.hemanth.vaani.data.CallLogEntity
import com.hemanth.vaani.data.ScreeningOutcome
import com.hemanth.vaani.data.VaaniDatabase
import com.hemanth.vaani.ui.chat.ChatScreen
import com.hemanth.vaani.ui.theme.VaaniTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class MainActivity : ComponentActivity() {

    private val callLogFlow = MutableStateFlow<List<CallLogEntity>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val dao = VaaniDatabase.getInstance(applicationContext).vaaniDao()
        dao.observeCallLog().onEach { callLogFlow.value = it }.launchIn(lifecycleScope)

        setContent {
            VaaniTheme(darkTheme = true) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = "home") {
                        composable("home") {
                            VaaniHome(
                                callLog = callLogFlow,
                                onRequestScreeningRole = ::requestCallScreeningRole,
                                onRequestMicPermission = ::requestMicPermission,
                                hasScreeningRole = ::hasCallScreeningRole,
                                hasMicPermission = ::hasMicPermission,
                                onOpenChat = { navController.navigate("chat") }
                            )
                        }
                        composable("chat") {
                            ChatScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* result handled by re-checking hasCallScreeningRole() on next recomposition */ }

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op; UI re-reads permission state */ }

    private fun hasCallScreeningRole(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val roleManager = getSystemService(RoleManager::class.java)
        return roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
    }

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true) {
            roleRequestLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        }
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestMicPermission() {
        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
fun VaaniHome(
    callLog: MutableStateFlow<List<CallLogEntity>>,
    onRequestScreeningRole: () -> Unit,
    onRequestMicPermission: () -> Unit,
    hasScreeningRole: () -> Boolean,
    hasMicPermission: () -> Boolean,
    onOpenChat: () -> Unit
) {
    val entries by callLog.asStateFlow().collectAsStateWithLifecycle()
    var screeningGranted by remember { mutableStateOf(hasScreeningRole()) }
    var micGranted by remember { mutableStateOf(hasMicPermission()) }

    Column(modifier = Modifier.fillMaxSize().padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Vaani", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Your on-device, multilingual assistant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FilledTonalIconButton(onClick = onOpenChat) {
                Icon(Icons.Filled.Chat, contentDescription = "Open chat")
            }
        }

        Spacer(Modifier.height(24.dp))

        SetupRow(
            icon = Icons.Filled.Call,
            title = "Call screening",
            granted = screeningGranted,
            actionLabel = "Grant role",
            onClick = { onRequestScreeningRole(); screeningGranted = hasScreeningRole() }
        )
        Spacer(Modifier.height(12.dp))
        SetupRow(
            icon = Icons.Filled.Mic,
            title = "Microphone (voice assistant)",
            granted = micGranted,
            actionLabel = "Grant permission",
            onClick = { onRequestMicPermission(); micGranted = hasMicPermission() }
        )

        Spacer(Modifier.height(28.dp))
        Text("Recent calls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        if (entries.isEmpty()) {
            Text(
                "No calls screened yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(entries) { entry -> CallLogRow(entry) }
            }
        }
    }
}

@Composable
private fun SetupRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    granted: Boolean,
    actionLabel: String,
    onClick: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.bodyLarge)
            }
            if (granted) {
                Text("Enabled", color = MaterialTheme.colorScheme.primary)
            } else {
                TextButton(onClick = onClick) { Text(actionLabel) }
            }
        }
    }
}

@Composable
private fun CallLogRow(entry: CallLogEntity) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(entry.phoneNumber, style = MaterialTheme.typography.bodyLarge)
                Text(entry.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val (label, color) = when (entry.outcome) {
                ScreeningOutcome.ALLOWED -> "Rang" to MaterialTheme.colorScheme.primary
                ScreeningOutcome.WHITELISTED -> "Whitelisted" to MaterialTheme.colorScheme.primary
                ScreeningOutcome.SILENCED_SPAM -> "Silenced" to MaterialTheme.colorScheme.error
            }
            Text(label, color = color, fontWeight = FontWeight.Medium)
        }
    }
}
