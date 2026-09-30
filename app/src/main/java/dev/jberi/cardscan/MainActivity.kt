package dev.jberi.cardscan

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.jberi.cardscan.capture.DocumentScanner
import dev.jberi.cardscan.save.ContactInsert
import dev.jberi.cardscan.spike.NanoSpike
import dev.jberi.cardscan.theme.CardScannerTheme
import dev.jberi.cardscan.ui.ReviewScreen
import dev.jberi.cardscan.ui.ScanViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val shared = sharedImageUri(intent)
    setContent {
      CardScannerTheme {
        Scaffold { p -> AppScreen(Modifier.padding(p), initialUri = shared) }
      }
    }
  }

  /** Supports being a share-sheet target for an image. */
  private fun sharedImageUri(intent: Intent?): Uri? = when (intent?.action) {
    Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    Intent.ACTION_VIEW -> intent.data
    else -> null
  }
}

@Composable
private fun AppScreen(modifier: Modifier = Modifier, initialUri: Uri? = null) {
  val vm: ScanViewModel = viewModel()
  val state by vm.state.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val activity = context as Activity
  val scope = rememberCoroutineScope()
  var diagnostics by remember { mutableStateOf<String?>(null) }
  var keepScans by remember { mutableStateOf(vm.keepForTesting) }

  val region = remember {
    val tm = context.getSystemService(TelephonyManager::class.java)
    (tm?.networkCountryIso?.takeIf { it.isNotBlank() }
      ?: context.resources.configuration.locales[0].country).uppercase()
  }

  LaunchedEffect(initialUri) { initialUri?.let { vm.process(listOf(it), region) } }

  val scanLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    // Every page of the session is its own card, so a whole stack can be captured in one go.
    vm.process(DocumentScanner.pagesFrom(result.data), region)
  }

  val pickLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
  ) { uri -> if (uri != null) vm.process(listOf(uri), region) }

  val result = state.current

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    if (result == null) {
      Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
          enabled = !state.busy,
          modifier = Modifier.fillMaxWidth(),
          onClick = {
            scope.launch {
              try {
                val sender = DocumentScanner.startScanIntent(activity)
                scanLauncher.launch(IntentSenderRequest.Builder(sender).build())
              } catch (t: Throwable) {
                vm.fail("Scanner unavailable: ${t.message}")
              }
            }
          },
        ) { Text("Scan cards") }

        OutlinedButton(
          enabled = !state.busy,
          modifier = Modifier.fillMaxWidth(),
          onClick = {
            pickLauncher.launch(
              PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
            )
          },
        ) { Text("Import image") }

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
          Checkbox(checked = keepScans, onCheckedChange = {
            keepScans = it
            vm.keepForTesting = it
          })
          Text("Keep scans on device for parser testing")
        }

        if (state.busy) {
          Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            CircularProgressIndicator()
            Text(state.progress, Modifier.padding(start = 12.dp))
          }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        Text(
          "Scanning keeps the camera open. Capture a whole stack in one session and each " +
            "card comes back separately, up to ${DocumentScanner.PAGE_LIMIT}.",
          style = MaterialTheme.typography.bodySmall,
        )

        HorizontalDivider(Modifier.padding(top = 16.dp))
        TextButton(onClick = {
          scope.launch { diagnostics = runCatching { NanoSpike.run() }.getOrElse { it.message } }
        }) { Text("Check on-device Gemini Nano") }
        diagnostics?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
      }
    } else {
      if (state.total > 1) {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
          OutlinedButton(enabled = state.index > 0, onClick = vm::previous) { Text("Previous") }
          Text("Card ${state.index + 1} of ${state.total}")
          OutlinedButton(
            enabled = state.index < state.total - 1,
            onClick = vm::next,
          ) { Text("Next") }
        }
      }
      if (state.busy) {
        Row(
          Modifier.padding(horizontal = 16.dp),
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
          CircularProgressIndicator(Modifier.padding(end = 12.dp))
          Text(state.progress, style = MaterialTheme.typography.bodySmall)
        }
      }
      Image(
        bitmap = result.preview.asImageBitmap(),
        contentDescription = "Captured card",
        modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
        contentScale = ContentScale.Fit,
      )
      ReviewScreen(
        card = result.card,
        fromBarcode = result.fromBarcode,
        onChange = vm::edited,
        onRescan = vm::reset,
        onSave = { c ->
          try {
            context.startActivity(ContactInsert.intentFor(c))
          } catch (t: Throwable) {
            Toast.makeText(context, "No contacts app found", Toast.LENGTH_LONG).show()
          }
        },
      )
      Row(Modifier.padding(16.dp)) {
        Text(
          "OCR: ${result.ocr.lines.size} lines in ${result.ocrMillis} ms",
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
  }
}
