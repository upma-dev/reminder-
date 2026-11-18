package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.cloud.Profile
import com.example.cloud.ProfileDetails
import com.example.util.ImageUtil

/**
 * Profile + safety details. Used at first sign-in and from Settings -> Edit profile.
 * Blood group / medical note / home address travel with every SOS so helpers know what to expect.
 */
@Composable
fun ProfileForm(
    initial: Profile?,
    authEmail: String,
    busy: Boolean,
    saveLabel: String,
    onSave: (ProfileDetails) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var email by remember { mutableStateOf(initial?.email?.ifBlank { authEmail } ?: authEmail) }
    var blood by remember { mutableStateOf(initial?.bloodGroup.orEmpty()) }
    var medical by remember { mutableStateOf(initial?.medicalNote.orEmpty()) }
    var address by remember { mutableStateOf(initial?.address.orEmpty()) }
    var newPhoto by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) newPhoto = ImageUtil.uriToBase64(context, uri)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(contentAlignment = Alignment.BottomEnd) {
                Avatar(name.ifBlank { "?" }, 96, photoB64 = newPhoto ?: initial?.photoB64)
                Box(
                    Modifier
                        .padding(2.dp)
                        .clickable { picker.launch("image/*") }
                ) {
                    Icon(
                        Icons.Default.CameraAlt, contentDescription = "Change photo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }
        }
        Text(
            "Photo chuno (optional) — aapke dost ise chat aur SOS me dekhenge.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Poora naam *") }, singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
        )
        OutlinedTextField(
            value = email, onValueChange = { email = it },
            label = { Text("Email") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
        )

        Text("Safety details (SOS ke saath jaayengi)", fontWeight = FontWeight.Bold)
        Text("Blood group", style = MaterialTheme.typography.labelMedium)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-")) { g ->
                FilterChip(
                    selected = blood == g,
                    onClick = { blood = if (blood == g) "" else g },
                    label = { Text(g) }
                )
            }
        }
        OutlinedTextField(
            value = medical, onValueChange = { medical = it },
            label = { Text("Medical note (allergy, dawai, asthma...)") },
            minLines = 2, maxLines = 3,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = address, onValueChange = { address = it },
            label = { Text("Ghar / PG ka address") },
            minLines = 2, maxLines = 3,
            shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(2.dp))
        Button(
            onClick = { onSave(ProfileDetails(name, email, blood, medical, address, newPhoto)) },
            enabled = name.isNotBlank() && !busy,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp).testTag("profile_save")
        ) {
            if (busy) CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            else Text(saveLabel, fontWeight = FontWeight.Bold)
        }
    }
}
