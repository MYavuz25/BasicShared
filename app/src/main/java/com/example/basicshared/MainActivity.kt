package com.example.basicshared

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.provider.ContactsContract
import android.telephony.SubscriptionManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Task

class MainActivity : ComponentActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var pickContactLauncher: ActivityResultLauncher<Intent>
    private var selectedPhoneNumber by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // İzinleri yönetmek için
        requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val locationPermissionGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
            val smsPermissionGranted = permissions[Manifest.permission.SEND_SMS] ?: false
            val phoneStatePermissionGranted = permissions[Manifest.permission.READ_PHONE_STATE] ?: false
            val contactsPermissionGranted = permissions[Manifest.permission.READ_CONTACTS] ?: false

            if (locationPermissionGranted && smsPermissionGranted && phoneStatePermissionGranted && contactsPermissionGranted) {
                if (isLocationEnabled()) {
                    pickContact()
                } else {
                    promptEnableLocation()
                }
            } else {
                Toast.makeText(this, "Gerekli izinler verilmedi!", Toast.LENGTH_SHORT).show()
            }
        }

        // Kişi seçmek için
        pickContactLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                data?.data?.let { uri ->
                    val cursor = contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val phoneNumberIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            selectedPhoneNumber = it.getString(phoneNumberIndex).replace(" ", "").replace("-", "")
                        }
                    }
                    if (selectedPhoneNumber.isNotEmpty()) {
                        getLastKnownLocationAndSendSMS(selectedPhoneNumber)
                    }
                }
            }
        }

        setContent {
            MainScreen(
                onShareLocationClicked = { checkAndRequestPermissions() }
            )
        }
    }

    // İzinleri kontrol etme ve gerekirse isteme
    private fun checkAndRequestPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_PHONE_STATE,
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
            if (!isInternetAvailable()) {
                Toast.makeText(this, "İnternet bağlantısı yok. Lütfen interneti açın.", Toast.LENGTH_LONG).show()
                promptEnableInternet()
            } else if (!isLocationEnabled()) {
                Toast.makeText(this, "Konumunuz açık değil. Lütfen konumunuzu açın.", Toast.LENGTH_LONG).show()
                promptEnableLocation()
            } else {
                pickContact()
            }
        }
    }

    // Kişi seçme işlemi
    private fun pickContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        pickContactLauncher.launch(intent)
    }

    // Konumu al ve SMS gönder
    private fun getLastKnownLocationAndSendSMS(phoneNumber: String) {
        try {
            fusedLocationClient.lastLocation
                .addOnCompleteListener { task: Task<Location> ->
                    val location: Location? = task.result
                    if (location != null) {
                        sendLocationAsSMS(location, phoneNumber)
                    } else {
                        promptEnableLocation()
                        Toast.makeText(this, "Konum alınamadı!", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: SecurityException) {
            Toast.makeText(this, "Konum erişim hatası: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Konum bilgisini SMS ile gönder
    private fun sendLocationAsSMS(location: Location, phoneNumber: String) {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_PHONE_STATE
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, "Telefon durumu izni verilmedi!", Toast.LENGTH_SHORT).show()
            return
        }

        val message = "Benim konumum: https://maps.google.com/?q=${location.latitude},${location.longitude}"

        try {
            val subscriptionManager = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as SubscriptionManager
            val subscriptionInfoList = subscriptionManager.activeSubscriptionInfoList

            if (subscriptionInfoList.isNotEmpty()) {
                val subscriptionId = subscriptionInfoList[0].subscriptionId
                val smsManager = SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
                Toast.makeText(this, "Konum başarıyla gönderildi!", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "SIM kart bilgisi alınamadı!", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "SMS gönderimi başarısız oldu: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // Konumun açık olup olmadığını kontrol et
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
            networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            else -> false
        }
    }
    private fun promptEnableInternet() {
        if (!isInternetAvailable()){
            val intent = Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
        }
    }

    // Kullanıcıya konum açması için yönlendirme
    private fun promptEnableLocation() {
        if (!isLocationEnabled()) {
            val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            startActivity(intent)
        }
    }
}

// Compose UI
@Composable
fun MainScreen(onShareLocationClicked: () -> Unit) {
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
        }
    }
}
