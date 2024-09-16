package com.example.basicshared

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var smsPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var contactsPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var pickContactLauncher: ActivityResultLauncher<Intent>
    private var selectedPhoneNumber by mutableStateOf("")
    private var targetPhoneNumber by mutableStateOf("")
    private var selectedContactName by mutableStateOf("")
    private var isDialogOpen by mutableStateOf(false)
    private var isTargetDialogOpen by mutableStateOf(false)
    private var currentShareType by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Permission launcher for location
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                handleLocationPermissionGranted()
            } else {
                Toast.makeText(this, "Konum izni verilmedi!", Toast.LENGTH_SHORT).show()
            }
        }

        // Permission launcher for SMS
        smsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                handleSMSPermissionGranted()
            } else {
                Toast.makeText(this, "SMS izni verilmedi!", Toast.LENGTH_SHORT).show()
            }
        }

        // Permission launcher for contacts
        contactsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                pickContact()
            } else {
                Toast.makeText(this, "Kişiler izni verilmedi!", Toast.LENGTH_SHORT).show()
            }
        }
        pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            handleContactResult(result)
        }


        setContent {
            MainScreen(
                onShareLocationClicked = { checkAndRequestPermissions("location") },
                onShareContactClicked = { checkAndRequestPermissions("contact") }
            )

            if (isTargetDialogOpen) {
                TargetContactDialog(
                    onDismiss = { isTargetDialogOpen = false
                                resetData()},
                    onConfirm = {
                        pickContact()
                        isTargetDialogOpen = false
                    }
                )
            }

            if (isDialogOpen) {
                ShareOptionsDialog(
                    onDismiss = { isDialogOpen = false
                                resetData()},
                    onShareViaSMS = {
                        smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
                        isDialogOpen = false
                    },
                    onShareViaWhatsApp = {
                        handleWhatsAppPermissionGranted()
                        isDialogOpen = false
                    }
                )
            }
        }
    }

    private fun resetData() {
        selectedContactName = ""
        selectedPhoneNumber = ""
        targetPhoneNumber = ""
    }

    private fun handleContactResult(result: ActivityResult) {
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            data?.data?.let { uri ->
                val cursor = contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val phoneNumberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                        val contactNameIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                        val phoneNumber = it.getString(phoneNumberIndex).replace(" ", "").replace("-", "")
                        val contactName = it.getString(contactNameIndex)

                        when (currentShareType) {
                            "contact" -> {
                                if (selectedPhoneNumber.isEmpty()) {
                                    selectedPhoneNumber = phoneNumber
                                    selectedContactName = contactName
                                    isTargetDialogOpen = true
                                } else {
                                    targetPhoneNumber = phoneNumber
                                    isDialogOpen = true
                                }
                            }
                            "location" -> {
                                targetPhoneNumber = phoneNumber
                                isDialogOpen = true
                            }
                        }
                    }
                }
            }
        }
    }
    private fun checkAndRequestPermissions(shareType: String) {
        currentShareType=shareType
        when (shareType) {
            "location" -> locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            "contact" -> contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            "sms" -> smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }
    }

    private fun handleLocationPermissionGranted() {
        if (isInternetAvailable()){
            if (isLocationEnabled()) {
                isTargetDialogOpen=true
            } else {
                promptEnableLocation()
            }
        }else{
            promptEnableInternet()
        }
    }

    private fun handleSMSPermissionGranted() {
        when (currentShareType) {
            "contact" -> {
                if (targetPhoneNumber.isNotEmpty() && selectedContactName.isNotEmpty() && selectedPhoneNumber.isNotEmpty()) {
                    shareViaSMS(targetPhoneNumber, selectedContactName, selectedPhoneNumber)
                } else {
                    Toast.makeText(this, "Kişi paylaşımı için gerekli bilgiler eksik!", Toast.LENGTH_SHORT).show()
                }
            }
            "location" -> {
                if (targetPhoneNumber.isNotEmpty()) {
                    // You might need to modify the message for location sharing if different
                    shareLocationViaSMS(targetPhoneNumber)
                } else {
                    Toast.makeText(this, "Konum paylaşımı için hedef telefon numarası eksik!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    private fun handleWhatsAppPermissionGranted() {
        when (currentShareType) {
            "contact" -> {
                if (targetPhoneNumber.isNotEmpty() && selectedContactName.isNotEmpty() && selectedPhoneNumber.isNotEmpty()) {
                    shareViaWhatsApp(targetPhoneNumber, selectedContactName, selectedPhoneNumber)
                } else {
                    Toast.makeText(this, "Kişi paylaşımı için gerekli bilgiler eksik!", Toast.LENGTH_SHORT).show()
                }
            }
            "location" -> {
                if (targetPhoneNumber.isNotEmpty()) {
                    shareLocationViaWhatsApp(targetPhoneNumber)  // Konum paylaşımı
                } else {
                    Toast.makeText(this, "Konum paylaşımı için hedef telefon numarası eksik!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun shareLocationViaSMS(phoneNumber: String) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val message = "Konumum: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                        sendSMS(phoneNumber, message)
                    } else {
                        Toast.makeText(this, "Konum alınamadı!", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Konum alınırken bir hata oluştu!", Toast.LENGTH_SHORT).show()
                }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        resetData()
    }
    private fun shareLocationViaWhatsApp(phoneNumber: String) {
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        val message = "Konumumu paylaş: https://maps.google.com/?q=${location.latitude},${location.longitude}"
                        openWhatsAppChat(phoneNumber, message)
                    } else {
                        Toast.makeText(this, "Konum alınamadı!", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Konum alınırken bir hata oluştu!", Toast.LENGTH_SHORT).show()
                }
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        resetData()
    }




    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    private fun shareViaWhatsApp(phoneNumber: String, contactName: String, contactPhoneNumber: String) {
        val message = "Bu kişiyle iletişime geç: $contactName : $contactPhoneNumber"
        openWhatsAppChat(phoneNumber, message)
        resetData()
    }

    private fun shareViaSMS(phoneNumber: String, contactName: String, selectedPhoneNumber: String) {
        val message = "Bu kişiyle iletişime geç: $contactName : $selectedPhoneNumber"
        sendSMS(phoneNumber, message)
        resetData()
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            Toast.makeText(this, "SMS başarıyla gönderildi!", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "SMS gönderimi başarısız oldu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openWhatsAppChat(phoneNumber: String, message: String) {
        val formattedPhoneNumber = phoneNumber.replace(" ", "").replace("-", "")
        val uri = "https://api.whatsapp.com/send?phone=$formattedPhoneNumber&text=${Uri.encode(message)}"

        val sendIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
        sendIntent.setPackage("com.whatsapp")

        try {
            startActivity(sendIntent)
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp yüklü değil veya uygun değil!", Toast.LENGTH_SHORT).show()
        }
    }


    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun isInternetAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return when {
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            else -> false
        }
    }
    private fun promptEnableLocation() {
        if (!isLocationEnabled()) {
            val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
    }
    private fun promptEnableInternet(){
            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
    }
}

@Composable
fun MainScreen(onShareLocationClicked: () -> Unit, onShareContactClicked: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Paylaşım Yap",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            Button(
                onClick = { onShareLocationClicked() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Konumu Paylaş", style = MaterialTheme.typography.bodyMedium)
            }

            Button(
                onClick = { onShareContactClicked() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(text = "Kişiyi Paylaş", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun TargetContactDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = "Hedef Kişiyi Seçin",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Text(
                text = "Lütfen paylaşmak istediğiniz kişiyi seçin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
            }) {
                Text(text = "Tamam", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(text = "İptal", color = MaterialTheme.colorScheme.secondary)
            }
        }
    )
}
@Composable
fun ShareOptionsDialog(
    onDismiss: () -> Unit,
    onShareViaSMS: () -> Unit,
    onShareViaWhatsApp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = {
            Text(
                text = "Paylaşım Yöntemi Seçin",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
        },
        text = {
            Text(
                text = "Bu kişiyi nasıl paylaşmak istersiniz?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        },
        confirmButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = { onShareViaSMS() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "SMS ile Paylaş", style = MaterialTheme.typography.bodyMedium)
                }

                Button(
                    onClick = { onShareViaWhatsApp() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(text = "WhatsApp ile Paylaş", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(text = "İptal", color = MaterialTheme.colorScheme.secondary)
            }
        }
    )
}


