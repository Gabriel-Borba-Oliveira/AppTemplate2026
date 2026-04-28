package com.ifpr.androidapptemplate.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.gms.location.*
import com.google.android.gms.location.Priority
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.database.*
import kotlinx.coroutines.*
import java.util.Locale
import com.ifpr.androidapptemplate.R
import com.ifpr.androidapptemplate.baseclasses.Item
import android.graphics.BitmapFactory

class HomeFragment : Fragment() {

    private lateinit var currentAddressTextView: TextView
    private lateinit var openMapsButton: Button
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationRequest: LocationRequest
    private var currentLocation: Location? = null  // Armazenar localização atual

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        inicializaGerenciamentoLocalizacao(view)

        // Inicializar botão
        openMapsButton = view.findViewById(R.id.openMapsButton)
        openMapsButton.setOnClickListener {
            openLocationInGoogleMaps()
        }

        val containerLayout = view.findViewById<LinearLayout>(R.id.itemContainer)
        carregarItensMarketplace(containerLayout)

        return view
    }

    private fun inicializaGerenciamentoLocalizacao(view: View) {
        currentAddressTextView = view.findViewById(R.id.currentAddressTextView)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireActivity())

        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestLocationPermission()
        } else {
            getCurrentLocation()
        }
    }

    private fun requestLocationPermission() {
        requestPermissions(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                getCurrentLocation()
            } else {
                Snackbar.make(
                    requireView(),
                    "Permissão negada. Não é possível acessar a localização.",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun getCurrentLocation() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { location ->
                    currentLocation = location  // Armazenar localização
                    displayAddress(location)
                }
            }
        }

        locationRequest = LocationRequest.create().apply {
            interval = 30000
            fastestInterval = 30000
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun displayAddress(location: Location) {
        val geocoder = Geocoder(requireContext(), Locale.getDefault())
        val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val address = addresses?.firstOrNull()?.getAddressLine(0) ?: "Endereço não encontrado"
                withContext(Dispatchers.Main) {
                    currentAddressTextView.text = address
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    currentAddressTextView.text = "Erro: ${e.message}"
                }
            }
        }
    }

    // Abrir localização no Google Maps
    private fun openLocationInGoogleMaps() {
        if (currentLocation == null) {
            Toast.makeText(context, "Aguardando localização...", Toast.LENGTH_SHORT).show()
            return
        }

        val latitude = currentLocation!!.latitude
        val longitude = currentLocation!!.longitude

        val uri = Uri.parse("https://www.google.com/maps/search/?api=1&query=$latitude,$longitude")
        val intent = Intent(Intent.ACTION_VIEW, uri)
        startActivity(intent)
    }

    fun carregarItensMarketplace(container: LinearLayout) {
        val databaseRef = FirebaseDatabase.getInstance().getReference("itens")

        databaseRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                container.removeAllViews()

                for (userSnapshot in snapshot.children) {
                    for (itemSnapshot in userSnapshot.children) {
                        val item = itemSnapshot.getValue(Item::class.java) ?: continue

                        val itemView = LayoutInflater.from(container.context)
                            .inflate(R.layout.item_template, container, false)

                        val imageView = itemView.findViewById<ImageView>(R.id.item_image)
                        val enderecoView = itemView.findViewById<TextView>(R.id.item_endereco)
                        val categoriaView = itemView.findViewById<TextView>(R.id.item_categoria)
                        val duracaoView = itemView.findViewById<TextView>(R.id.item_duracao)

                        enderecoView.text = "Endereço: ${item.endereco ?: "Não informado"}"
                        categoriaView.text = "Categoria: ${item.categoria ?: "Não definida"}"
                        duracaoView.text = "Duração: ${item.duracao?.toString() ?: "--"} minutos"

                        if (!item.imageUrl.isNullOrEmpty()) {
                            Glide.with(container.context).load(item.imageUrl).into(imageView)
                        } else if (!item.base64Image.isNullOrEmpty()) {
                            try {
                                val bytes = Base64.decode(item.base64Image, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                imageView.setImageBitmap(bitmap)
                            } catch (_: Exception) {}
                        }

                        container.addView(itemView)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(container.context, "Erro ao carregar dados", Toast.LENGTH_SHORT).show()
            }
        })
    }
}