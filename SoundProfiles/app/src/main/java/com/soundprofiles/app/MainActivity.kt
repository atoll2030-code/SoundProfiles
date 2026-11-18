package com.soundprofiles.app

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = ProfileStore(this)
        val controller = SoundController(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                SoundProfilesScreen(
                    store = store,
                    onApply = controller::apply,
                    onRequestPolicyAccess = {
                        startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SoundProfilesScreen(
    store: ProfileStore,
    onApply: (SoundProfile) -> ApplyResult,
    onRequestPolicyAccess: () -> Unit
) {
    var profiles by remember {
        mutableStateOf(ProfileId.entries.associateWith(store::load))
    }
    var lastApplied by remember { mutableStateOf(store.lastApplied()) }
    var editing by remember { mutableStateOf<ProfileId?>(null) }
    var message by remember { mutableStateOf("Коснитесь профиля, чтобы применить его") }
    var permissionNeeded by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("SoundProfiles", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Профили звука", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.height(28.dp))

            ProfileId.entries.forEach { id ->
                val profile = profiles.getValue(id)
                val selected = lastApplied == id
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp)
                        .combinedClickable(
                            onClick = {
                                when (val result = onApply(profile)) {
                                    ApplyResult.PermissionNeeded -> {
                                        permissionNeeded = true
                                        message = "Для переключения режима звука требуется системный доступ"
                                    }
                                    is ApplyResult.Failed -> message = result.message
                                    is ApplyResult.Applied -> {
                                        store.markApplied(id)
                                        lastApplied = id
                                        message = if (result.warnings.isEmpty()) {
                                            "Профиль «${id.label}» применён"
                                        } else {
                                            "Профиль применён с ограничениями: ${result.warnings.joinToString("; ")}"
                                        }
                                    }
                                }
                            },
                            onLongClick = { editing = id },
                            onClickLabel = "Применить профиль ${id.label}",
                            onLongClickLabel = "Настроить профиль ${id.label}"
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(id.icon, style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.width(18.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(id.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "Звонок ${profile.ring}% · Уведомления ${profile.notification}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Медиа ${profile.media}% · ${if (profile.vibrate) "Вибрация" else "Звук"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (selected) Text("✓", style = MaterialTheme.typography.headlineSmall)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(20.dp))
            Text(
                "Нажатие — применить · Долгое нажатие — изменить",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Подсветка показывает последний применённый профиль, а не текущее состояние системы.",
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }

    editing?.let { id ->
        ProfileEditor(
            id = id,
            initial = profiles.getValue(id),
            onDismiss = { editing = null },
            onSave = { updated ->
                try {
                    store.save(id, updated)
                    profiles = profiles + (id to updated.normalized())
                    editing = null
                    message = "Настройки профиля «${id.label}» сохранены. Нажмите его для применения."
                } catch (e: IllegalStateException) {
                    message = e.message ?: "Не удалось сохранить профиль"
                }
            }
        )
    }

    if (permissionNeeded) {
        AlertDialog(
            onDismissRequest = { permissionNeeded = false },
            title = { Text("Нужен системный доступ") },
            text = { Text("Разрешите SoundProfiles доступ к настройкам режима «Не беспокоить», затем вернитесь и повторно нажмите профиль.") },
            confirmButton = {
                TextButton(onClick = {
                    permissionNeeded = false
                    onRequestPolicyAccess()
                }) { Text("Открыть настройки") }
            },
            dismissButton = {
                TextButton(onClick = { permissionNeeded = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun ProfileEditor(
    id: ProfileId,
    initial: SoundProfile,
    onDismiss: () -> Unit,
    onSave: (SoundProfile) -> Unit
) {
    var ring by remember(id) { mutableIntStateOf(initial.ring) }
    var notification by remember(id) { mutableIntStateOf(initial.notification) }
    var media by remember(id) { mutableIntStateOf(initial.media) }
    var vibrate by remember(id) { mutableStateOf(initial.vibrate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Настройка: ${id.label}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                VolumeSlider("Звонок", ring) { ring = it }
                VolumeSlider("Уведомления", notification) { notification = it }
                VolumeSlider("Мультимедиа", media) { media = it }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Режим вибрации", modifier = Modifier.weight(1f))
                    Switch(checked = vibrate, onCheckedChange = { vibrate = it })
                }
                if (vibrate) {
                    Text("При вибрации звонок будет без звука. Значение громкости сохранится для обычного режима.", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(SoundProfile(ring, notification, media, vibrate)) }) {
                Text("Сохранить")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

@Composable
private fun VolumeSlider(label: String, value: Int, onChange: (Int) -> Unit) {
    Text("$label: $value%", style = MaterialTheme.typography.bodyMedium)
    Slider(
        value = value.toFloat(),
        onValueChange = { onChange(it.roundToInt()) },
        valueRange = 0f..100f,
        steps = 99
    )
}
