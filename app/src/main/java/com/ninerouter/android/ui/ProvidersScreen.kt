package com.ninerouter.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ninerouter.android.ui.components.SectionCard

private val PROVIDERS = listOf(
    "Claude Code (Pro/Max)", "Codex (Plus/Pro)", "GitHub Copilot", "Cursor",
    "Cline", "Continue", "Roo", "Kilo Code", "OpenCode Free", "Kiro AI",
    "Antigravity", "Grok Build", "Devin CLI", "Qwen Code", "DeepSeek TUI",
    "OpenRouter", "GLM", "Kimi", "MiniMax", "OpenAI", "Anthropic", "Gemini",
    "DeepSeek", "Groq", "xAI", "Mistral", "Perplexity", "Together AI",
    "Fireworks", "Cerebras", "Cohere", "NVIDIA", "SiliconFlow", "Nebius",
    "Chutes", "Hyperbolic",
    "Self-hosted STT/TTS/Embedding", "Custom OpenAI/Anthropic-compatible endpoint",
)

@Composable
fun ProvidersScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Providers", style = MaterialTheme.typography.titleLarge)
        }

        Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Provider credentials are configured inside the 9Router dashboard, using accounts you legitimately own.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This app never hard-codes third-party credentials, and never auto-generates, " +
                            "harvests, refreshes, or rotates provider tokens in a way that bypasses " +
                            "provider policies, quotas, or authentication controls.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.OpenInNew, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open Dashboard to configure")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            SectionCard("Supported by upstream 9Router") {
                PROVIDERS.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        row.forEach { p ->
                            Text("• $p", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
