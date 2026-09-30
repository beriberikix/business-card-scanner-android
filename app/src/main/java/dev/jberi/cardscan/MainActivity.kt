package dev.jberi.cardscan

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.jberi.cardscan.spike.NanoSpike
import dev.jberi.cardscan.theme.CardScannerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { CardScannerTheme { Scaffold { p -> SpikeScreen(Modifier.padding(p)) } } }
  }
}

@Composable
private fun SpikeScreen(modifier: Modifier = Modifier) {
  val scope = rememberCoroutineScope()
  var running by remember { mutableStateOf(false) }
  var output by remember { mutableStateOf("Tap to probe on-device Gemini Nano.") }

  Column(modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
    Button(
      enabled = !running,
      onClick = {
        running = true
        output = "Running..."
        scope.launch {
          val r = withContext(Dispatchers.Default) { runCatching { NanoSpike.run() } }
          output = r.getOrElse { "spike crashed: ${it::class.java.name}: ${it.message}" }
          running = false
        }
      },
    ) { Text("Run Gemini Nano spike") }

    if (running) CircularProgressIndicator(Modifier.padding(top = 16.dp))

    Text(output, Modifier.padding(top = 16.dp), style = MaterialTheme.typography.bodySmall)
  }
}
