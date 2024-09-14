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
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices


class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var pickContactLauncher: ActivityResultLauncher<Intent>
    private var selectedPhoneNumber by mutableStateOf("")
    private var targetPhoneNumber by mutableStateOf("")
    private var selectedContactName by mutableStateOf("")
    private var isDialogOpen by mutableStateOf(false)
    private var isTargetDialogOpen by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val smsPermissionGranted = permissions[Manifest.permission.SEND_SMS] ?: false
            val contactsPermissionGranted = permissions[Manifest.permission.READ_CONTACTS] ?: false

            if (locationPermissionGranted && smsPermissionGranted && contactsPermissionGranted) {
                if (isLocationEnabled()) {
                    pickContact("select")
                } else {
                    promptEnableLocation()
                }
            } else {
                Toast.makeText(this, "Gerekli izinler verilmedi!", Toast.LENGTH_SHORT).show()
            }
        }

        pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
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

                            if (selectedPhoneNumber.isEmpty()) {
                                selectedPhoneNumber = phoneNumber
                                selectedContactName = contactName
                                isTargetDialogOpen = true // Target seçmek için dialog'u aç
                            } else {
                                targetPhoneNumber = phoneNumber
                                isDialogOpen = true // Paylaşım yöntemini seçmek için dialog'u aç
                            }
                        }
                    }
                }
            }
        }

        setContent {
            MainScreen(
                onShareLocationClicked = { checkAndRequestPermissions(true) },
                onShareContactClicked = { checkAndRequestPermissions(isSharingLocation = false) }
            )

            if (isTargetDialogOpen) {
                TargetContactDialog(
                    onDismiss = { isTargetDialogOpen = false },
                    onConfirm = {
                        pickContact("target")
                        isTargetDialogOpen = false
                    }
                )
            }

            if (isDialogOpen) {
                ShareOptionsDialog(
                    onDismiss = { isDialogOpen = false },
                    onShareViaSMS = {
                        shareViaSMS(targetPhoneNumber, selectedContactName,selectedPhoneNumber)
                        isDialogOpen = false
                        selectedPhoneNumber = ""
                        targetPhoneNumber = ""
                    },
                    onShareViaWhatsApp = {
                        shareViaWhatsApp(targetPhoneNumber, selectedContactName,selectedPhoneNumber)
                        isDialogOpen = false
                        selectedPhoneNumber = ""
                        targetPhoneNumber = ""
                    }
                )
            }
        }
    }

    private fun checkAndRequestPermissions(isSharingLocation: Boolean) {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_CONTACTS
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            if (isSharingLocation) {
                // Konum paylaşımı yapılıyorsa konumun açık olup olmadığını kontrol et
                if (!isLocationEnabled()) {
                    Toast.makeText(this, "Konumunuz açık değil. Lütfen konumunuzu açın.", Toast.LENGTH_LONG).show()
                    promptEnableLocation()
                } else {
                    pickContact("select")
                }
            } else {
                // Kişi paylaşımı yapılıyorsa sadece kişiyi seç
                pickContact("select")
            }
        }
    }
    private fun enableLocationServices() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            val locationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(1000)
                .setFastestInterval(500)

            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    // Handle location updates
                }
            }

            locationManager.requestLocationUpdates(locationRequest, locationCallback, null)
        }
    }


    private fun pickContact(type: String) {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    private fun shareViaWhatsApp(phoneNumber: String, contactName: String,contactPhoneNumber:String) {
        val message = "Bu kişiyle iletişime geç: $contactName : $contactPhoneNumber"
        openWhatsAppChat(phoneNumber, message)
    }

    private fun shareViaSMS(phoneNumber: String, contactName: String,selectedPhoneNumber : String) {
        val message = "Bu kişiyle iletişime geç: $contactName : $selectedPhoneNumber"
        sendSMS(phoneNumber, message)
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
        val encodedMessage = Uri.encode(message)
        val uri = "https://api.whatsapp.com/send?phone=$phoneNumber&text=$encodedMessage"
        val sendIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(uri)
            setPackage("com.whatsapp")
        }
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

    private fun promptEnableInternet() {
        if (!isInternetAvailable()) {
            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
        }
    }

    private fun promptEnableLocation() {
        if (!isLocationEnabled()) {
            val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
    }
}

// Compose UI for main screen
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
            Button(
                onClick = { onShareLocationClicked() },
                modifier = Modifier.padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(text = "Konumu Paylaş")
            }

            Button(
                onClick = { onShareContactClicked() },
                modifier = Modifier.padding(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(text = "Kişiyi Paylaş")
            }
        }
    }
}

// Dialog for selecting target contact
@Composable
fun TargetContactDialog(
    onDismiss: () -> Unit,   // Dialog'u kapatmak için fonksiyon
    onConfirm: () -> Unit   // Target kişiyi seçmek için fonksiyon
) {
    AlertDialog(
        onDismissRequest = { onDismiss() },
        title = { Text(text = "Hedef Kişiyi Seçin") },
        text = { Text(text = "Lütfen paylaşmak istediğiniz kişiyi seçin.") },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()  // İşlem bittikten sonra dialog'u kapat
            }) {
                Text(text = "Tamam")
            }
        },
        dismissButton = {
            TextButton(onClick = { onDismiss() }) {
                Text(text = "İptal")
            }
        }
    )
}

// Dialog for share options
@Composable
fun ShareOptionsDialog(
    onDismiss: () -> Unit,
    onShareViaSMS: () -> Unit,
    onShareViaWhatsApp: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            onDismiss()
        },
        title = { Text(text = "Paylaşım Yöntemi Seçin") },
        text = { Text(text = "Bu kişiyi nasıl paylaşmak istersiniz?") },
        confirmButton = {
            TextButton(onClick = {
                onShareViaSMS()
                onDismiss()
            }) {
                Text(text = "SMS ile Paylaş")
            }
        },
        dismissButton = {
            TextButton(onClick = {
                onShareViaWhatsApp()
                onDismiss()
            }) {
                Text(text = "WhatsApp ile Paylaş")
            }
        }
    )
}


