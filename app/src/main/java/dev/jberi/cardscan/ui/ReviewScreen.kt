package dev.jberi.cardscan.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import dev.jberi.cardscan.model.ContactCard
import dev.jberi.cardscan.model.Email
import dev.jberi.cardscan.model.Phone
import dev.jberi.cardscan.model.PhoneType

/**
 * Editable view of the parsed card. Nothing reaches Contacts until the user taps save, and a
 * number that failed E.164 normalization is called out so OCR digit errors get caught here.
 */
@Composable
fun ReviewScreen(
  card: ContactCard,
  fromBarcode: Boolean,
  onChange: (ContactCard) -> Unit,
  onSave: (ContactCard) -> Unit,
  onRescan: () -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    if (fromBarcode) {
      AssistChip(onClick = {}, label = { Text("From QR vCard (exact)") })
    }

    Field("Given name", card.givenName) { onChange(card.copy(givenName = it)) }
    Field("Middle name", card.middleName) { onChange(card.copy(middleName = it)) }
    Field("Family name", card.familyName) { onChange(card.copy(familyName = it)) }
    Field("Job title", card.jobTitle) { onChange(card.copy(jobTitle = it)) }
    Field("Company", card.company) { onChange(card.copy(company = it)) }

    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text("Phones", style = MaterialTheme.typography.titleSmall)
    card.phones.forEachIndexed { i, phone ->
      PhoneRow(
        phone = phone,
        onChange = { updated ->
          onChange(card.copy(phones = card.phones.toMutableList().also { it[i] = updated }))
        },
        onRemove = {
          onChange(card.copy(phones = card.phones.filterIndexed { idx, _ -> idx != i }))
        },
      )
    }
    OutlinedButton(onClick = {
      onChange(card.copy(phones = card.phones + Phone("", PhoneType.WORK)))
    }) { Text("Add phone") }

    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Text("Emails", style = MaterialTheme.typography.titleSmall)
    card.emails.forEachIndexed { i, email ->
      Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
          value = email.address,
          onValueChange = { v ->
            onChange(
              card.copy(
                emails = card.emails.toMutableList()
                  .also { it[i] = email.copy(address = v, repaired = false) },
              ),
            )
          },
          label = { Text(if (email.repaired) "Email (repaired from: ${email.raw})" else "Email") },
          isError = email.repaired,
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = {
          onChange(card.copy(emails = card.emails.filterIndexed { idx, _ -> idx != i }))
        }) { Text("X") }
      }
    }
    OutlinedButton(onClick = { onChange(card.copy(emails = card.emails + Email(""))) }) {
      Text("Add email")
    }

    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    card.urls.forEachIndexed { i, url ->
      Field("Website", url) { v ->
        onChange(card.copy(urls = card.urls.toMutableList().also { it[i] = v }))
      }
    }
    Field("Address", card.address) { onChange(card.copy(address = it)) }
    Field("Note", card.note) { onChange(card.copy(note = it)) }

    Row(
      Modifier.fillMaxWidth().padding(top = 8.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Button(onClick = { onSave(card) }, modifier = Modifier.weight(1f)) {
        Text("Save to Contacts")
      }
      OutlinedButton(onClick = onRescan) { Text("Rescan") }
    }
  }
}

@Composable
private fun Field(label: String, value: String, onValueChange: (String) -> Unit) {
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(label) },
    singleLine = label != "Address" && label != "Note",
    modifier = Modifier.fillMaxWidth(),
  )
}

@Composable
private fun PhoneRow(phone: Phone, onChange: (Phone) -> Unit, onRemove: () -> Unit) {
  var menuOpen by remember { mutableStateOf(false) }
  Row(verticalAlignment = Alignment.CenterVertically) {
    OutlinedTextField(
      value = phone.number,
      onValueChange = { onChange(phone.copy(number = it)) },
      label = { Text(if (phone.normalized) "Number" else "Number (check: ${phone.raw})") },
      isError = !phone.normalized,
      singleLine = true,
      keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
      modifier = Modifier.weight(1f),
    )
    OutlinedButton(onClick = { menuOpen = true }) { Text(phone.type.name.take(4)) }
    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
      PhoneType.entries.forEach { t ->
        DropdownMenuItem(
          text = { Text(t.name) },
          onClick = {
            onChange(phone.copy(type = t))
            menuOpen = false
          },
        )
      }
    }
    IconButton(onClick = onRemove) { Text("X") }
  }
}
