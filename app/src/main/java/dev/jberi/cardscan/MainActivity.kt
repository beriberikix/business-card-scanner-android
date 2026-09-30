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

  LaunchedEffect(initialUri) { initialUri?.let { vm.process(it, region) } }

  val scanLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartIntentSenderForResult(),
  ) { result ->
    val uri = DocumentScanner.pagesFrom(result.data).firstOrNull()
    if (uri != null) vm.process(uri, region)
  }

  val pickLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia(),
  ) { uri -> if (uri != null) vm.process(uri, region) }

  val card = state.card

  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
    if (card == null) {
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
        ) { Text("Scan card") }

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

        if (state.busy) CircularProgressIndicator()
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        HorizontalDivider(Modifier.padding(top = 16.dp))
        TextButton(onClick = {
          scope.launch { diagnostics = runCatching { NanoSpike.run() }.getOrElse { it.message } }
        }) { Text("Check on-device Gemini Nano") }
        diagnostics?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
      }
    } else {
      state.bitmap?.let { bmp ->
        Image(
          bitmap = bmp.asImageBitmap(),
          contentDescription = "Captured card",
          modifier = Modifier.fillMaxWidth().heightIn(max = 200.dp),
          contentScale = ContentScale.Fit,
        )
      }
      ReviewScreen(
        card = card,
        fromBarcode = state.fromBarcode,
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
          "OCR: ${state.ocr?.lines?.size ?: 0} lines in ${state.ocrMillis} ms",
          style = MaterialTheme.typography.labelSmall,
        )
      }
    }
  }
}
