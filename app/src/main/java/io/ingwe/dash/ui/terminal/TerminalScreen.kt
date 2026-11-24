package io.ingwe.dash.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.graphics.Color
import io.ingwe.dash.ui.theme.*

/**
 * Raw terminal screen — send AT commands, see raw ELM327 responses.
 *
 * M1: mock-only. M3: wired to real BleTransport.
 * Uses MockTransport directly so you can test commands without hardware.
 */
@Composable
fun TerminalScreen(navController: NavController) {

    var inputText by remember { mutableStateOf("") }
    val logLines = remember { mutableStateListOf("# Ingwe Terminal — Mock Mode", "# Type an AT or OBD command and press SEND") }
    val listState = rememberLazyListState()

    // Simple mock transport for terminal testing
    val mockResponses = mapOf(
        "ATZ"    to listOf("ELM327 v1.5", ">"),
        "ATE0"   to listOf("OK", ">"),
        "ATSP6"  to listOf("OK", ">"),
        "010C1"  to listOf("7E8 04 41 0C 0D 24", ">"),
        "010D1"  to listOf("7E8 03 41 0D 00", ">"),
        "03"     to listOf("7E8 02 43 00", ">"),
    )

    LaunchedEffect(logLines.size) {
        if (logLines.isNotEmpty()) {
            listState.animateScrollToItem(logLines.size - 1)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IngweBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "TERMINAL",
                    color = IngwePrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    letterSpacing = 3.sp,
                )
                TextButton(onClick = { navController.popBackStack() }) {
                    Text("← BACK", color = IngweOnSurface.copy(alpha = 0.6f))
                }
            }

            HorizontalDivider(color = IngweOutline)
            Spacer(Modifier.height(8.dp))

            // Log output
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0D0D0D), shape = MaterialTheme.shapes.small),
                state = listState,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(logLines) { line ->
                    val color = when {
                        line.startsWith("#")  -> IngweOnSurface.copy(alpha = 0.4f)
                        line.startsWith("TX:") -> IngwePrimary
                        line.startsWith("RX:") -> IngweSecondary
                        line.startsWith("ERR:") -> IngweError
                        else -> IngweOnSurface.copy(alpha = 0.8f)
                    }
                    Text(
                        text = line,
                        color = color,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Input row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it.uppercase() },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("AT command or OBD PID…", color = IngweOnSurface.copy(alpha = 0.3f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = IngwePrimary,
                        unfocusedBorderColor = IngweOutline,
                        focusedTextColor = IngweOnSurface,
                        unfocusedTextColor = IngweOnSurface,
                        cursorColor = IngwePrimary,
                    ),
                )

                Button(
                    onClick = {
                        val cmd = inputText.trim()
                        if (cmd.isEmpty()) return@Button
                        logLines.add("TX: $cmd")
                        val response = mockResponses[cmd] ?: listOf("NO DATA", ">")
                        response.forEach { logLines.add("RX: $it") }
                        inputText = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = IngwePrimary),
                ) {
                    Text("SEND", color = IngweOnPrimary, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { logLines.clear(); logLines.add("# Terminal cleared") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = IngweOnSurface),
                ) { Text("CLR") }
            }
        }
    }
}

// Needed for terminal background
private val Color = androidx.compose.ui.graphics.Color.Companion
