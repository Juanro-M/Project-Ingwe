package io.ingwe.dash.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.ingwe.dash.data.transport.MockTransport
import io.ingwe.dash.data.transport.TransportGuard
import io.ingwe.dash.ui.components.ActionButton
import io.ingwe.dash.ui.components.ScreenHeader
import io.ingwe.dash.ui.theme.*
import kotlinx.coroutines.launch

private enum class LineKind { INFO, TX, RX, ERROR }
private data class LogLine(val kind: LineKind, val text: String)

private val Mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp)

/**
 * Raw ELM327 terminal. Commands go through the same [TransportGuard] allowlist
 * as the real driver, so blocked services are rejected here too.
 *
 * M1: backed by [MockTransport]. M3: swapped for BleTransport.
 */
@Composable
fun TerminalScreen(navController: NavController) {
    val scope = rememberCoroutineScope()
    val transport = remember { MockTransport() }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val log = remember {
        mutableStateListOf(
            LogLine(LineKind.INFO, "Simulated ELM327 · Amarok 3.0 V6 TDI"),
            LogLine(LineKind.INFO, "Try ATZ, 010C1, 0902, 03 — write and clear services are blocked."),
        )
    }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { transport.connect() }
    LaunchedEffect(log.size) { if (log.isNotEmpty()) listState.animateScrollToItem(log.size - 1) }

    fun send() {
        val cmd = input.trim().uppercase()
        if (cmd.isEmpty() || busy) return
        input = ""
        log.add(LogLine(LineKind.TX, cmd))
        try {
            TransportGuard.validateCommand(cmd)
        } catch (e: IllegalArgumentException) {
            log.add(LogLine(LineKind.ERROR, e.message ?: "Blocked"))
            return
        }
        busy = true
        scope.launch {
            try {
                transport.send(cmd).collect { line -> if (line != ">") log.add(LogLine(LineKind.RX, line)) }
            } catch (e: Exception) {
                log.add(LogLine(LineKind.ERROR, e.message ?: e.javaClass.simpleName))
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(IngweBackground)
            .safeDrawingPadding()
            .imePadding()
            .padding(horizontal = 24.dp, vertical = 14.dp),
    ) {
        ScreenHeader(
            title = "Terminal",
            subtitle = "Simulated adapter · read-only services",
            onBack = { navController.popBackStack() },
        ) {
            ActionButton("Clear", onClick = { log.clear() })
        }

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(IngweSurface),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            itemsIndexed(log) { _, line ->
                Row {
                    Text(
                        text = when (line.kind) {
                            LineKind.TX -> ">"
                            LineKind.RX -> " "
                            LineKind.ERROR -> "!"
                            LineKind.INFO -> "#"
                        },
                        style = Mono,
                        color = IngweTextTertiary,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(
                        text = line.text,
                        style = Mono,
                        color = when (line.kind) {
                            LineKind.TX -> IngweAccent
                            LineKind.RX -> IngweTextPrimary
                            LineKind.ERROR -> IngweAlert
                            LineKind.INFO -> IngweTextTertiary
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("AT command or OBD request", style = Mono, color = IngweTextTertiary) },
                textStyle = Mono,
                singleLine = true,
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IngweTextTertiary,
                    unfocusedBorderColor = IngweHairline,
                    focusedContainerColor = IngweSurface,
                    unfocusedContainerColor = IngweSurface,
                    focusedTextColor = IngweTextPrimary,
                    unfocusedTextColor = IngweTextPrimary,
                    cursorColor = IngweAccent,
                ),
            )
            Spacer(Modifier.width(10.dp))
            ActionButton("Send", primary = true, enabled = !busy && input.isNotBlank(), onClick = { send() })
        }
    }
}
