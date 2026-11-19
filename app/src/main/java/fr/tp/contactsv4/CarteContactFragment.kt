package fr.tp.contactsv4

import android.content.pm.PackageManager
import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import fr.tp.contactsv4.data.Contact
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commitNow
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class CarteContactFragment : Fragment(R.layout.fragment_carte) {
    private val model: ContactViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        view.findViewById<View>(R.id.retour).setOnClickListener { parentFragmentManager.popBackStack() }
        viewLifecycleOwner.lifecycleScope.launch {
            val contact = try {
                model.trouver(requireArguments().getLong("contact_id"))
            } catch (_: android.database.SQLException) {
                view.findViewById<TextView>(R.id.message).apply {
                    text = "Impossible de charger le contact."
                    isVisible = true
                }
                return@launch
            }
            afficher(view, savedInstanceState, contact)
        }
    }

    private fun afficher(view: View, savedInstanceState: Bundle?, contact: Contact?) {
        val lat = contact?.latitude
        val lng = contact?.longitude
        val ctx = requireContext()
        val metadata = ctx.packageManager.getApplicationInfo(ctx.packageName, PackageManager.GET_META_DATA).metaData
        val key = metadata?.getString("com.google.android.geo.API_KEY")
        val erreur = when {
            contact == null -> "Contact introuvable."
            lat == null || lng == null -> "Ce contact n’a pas de coordonnées GPS enregistrées."
            lat !in -90.0..90.0 || lng !in -180.0..180.0 -> "Coordonnées GPS invalides."
            key.isNullOrBlank() || key == "NON_CONFIGUREE" -> "Configure la clé Google Maps dans secrets.properties."
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx) != ConnectionResult.SUCCESS ->
                "Les services Google Play sont indisponibles sur cet appareil."
            else -> null
        }
        if (erreur != null) {
            view.findViewById<TextView>(R.id.message).apply {
                text = erreur
                isVisible = true
            }
            return
        }
        val position = LatLng(requireNotNull(lat), requireNotNull(lng))
        val nom = requireNotNull(contact).nomComplet
        val carte = childFragmentManager.findFragmentById(R.id.map_container) as? SupportMapFragment
            ?: SupportMapFragment.newInstance().also { fragment ->
                childFragmentManager.commitNow { replace(R.id.map_container, fragment) }
            }
        carte.getMapAsync { map ->
            map.clear()
            map.addMarker(MarkerOptions().position(position).title(nom))?.showInfoWindow()
            if (savedInstanceState == null) map.moveCamera(CameraUpdateFactory.newLatLngZoom(position, 14f))
        }
    }
}
