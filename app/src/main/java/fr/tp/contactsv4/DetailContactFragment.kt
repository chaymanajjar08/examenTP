package fr.tp.contactsv4

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import fr.tp.contactsv4.data.Contact
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Patterns
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import java.io.File
import java.io.IOException

class DetailContactFragment : Fragment(R.layout.fragment_detail) {
    private val model: ContactViewModel by activityViewModels()
    private var photo: String? = null
    private var brouillon: Bundle? = null
    private val champs = intArrayOf(R.id.nom, R.id.prenom, R.id.telephone, R.id.email, R.id.latitude, R.id.longitude)

    override fun onResume() {
        super.onResume()
        val reglages = PreferencesManager.lire(requireContext())
        champs.take(3).forEach { id ->
            view?.findViewById<EditText>(id)?.apply {
                textSize = reglages.taille
                if (id != R.id.telephone) setTextColor(reglages.couleur)
            }
        }
    }

    private fun saisirBrouillon(): Bundle? {
        val racine = view ?: return brouillon
        if (!racine.findViewById<View>(R.id.enregistrer).isEnabled) return brouillon
        return Bundle().apply {
            champs.forEach { putString(it.toString(), racine.findViewById<EditText>(it).text.toString()) }
            putString("photo", photo)
            putBoolean("favori", racine.findViewById<android.widget.CheckBox>(R.id.favori).isChecked)
        }
    }

    override fun onDestroyView() {
        brouillon = saisirBrouillon()
        super.onDestroyView()
    }
    private val galerie = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                choisirPhoto(uri)
            } catch (_: SecurityException) {
                message("Impossible de lire cette photo.")
            }
        }
    }
    private val camera = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            try {
                val fichier = File.createTempFile("contact-", ".jpg", requireContext().cacheDir)
                fichier.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                choisirPhoto(Uri.fromFile(fichier))
            } catch (_: IOException) {
                message("Impossible d’enregistrer la photo.")
            }
        }
    }

    private fun choisirPhoto(uri: Uri) {
        photo = uri.toString()
        view?.findViewById<ImageView>(R.id.photo)?.afficherPhoto(photo)
    }

    private fun message(texte: String) {
        Toast.makeText(requireContext(), texte, Toast.LENGTH_SHORT).show()
    }

    private fun lancer(messageErreur: String, action: () -> Unit) {
        try { action() } catch (_: ActivityNotFoundException) { message(messageErreur) }
        catch (_: SecurityException) { message(messageErreur) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBundle("brouillon", saisirBrouillon())
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val id = requireArguments().getLong("contact_id")
        val enregistrer = view.findViewById<View>(R.id.enregistrer)
        enregistrer.isEnabled = false
        view.findViewById<View>(R.id.annuler).setOnClickListener { parentFragmentManager.popBackStack() }
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                preparer(view, id, savedInstanceState?.getBundle("brouillon") ?: brouillon)
            } catch (e: CancellationException) {
                throw e
            } catch (_: android.database.SQLException) {
                message("Impossible de charger le contact.")
            }
        }
    }

    private suspend fun preparer(view: View, id: Long, saisie: Bundle?) {
        val contact = (if (id == 0L) Contact() else model.trouver(id)) ?: run {
            message("Contact introuvable.")
            parentFragmentManager.popBackStack()
            return
        }
        fun champ(idChamp: Int) = view.findViewById<EditText>(idChamp)
        val nom = champ(R.id.nom)
        val prenom = champ(R.id.prenom)
        val tel = champ(R.id.telephone)
        val email = champ(R.id.email)
        val lat = champ(R.id.latitude)
        val lng = champ(R.id.longitude)
        nom.setText(contact.nom)
        prenom.setText(contact.prenom)
        tel.setText(contact.telephone)
        email.setText(contact.email)
        lat.setText(contact.latitude?.toString().orEmpty())
        lng.setText(contact.longitude?.toString().orEmpty())
        saisie?.let { etat -> champs.forEach { champ(it).setText(etat.getString(it.toString())) } }
        photo = if (saisie == null) contact.photoUri else saisie.getString("photo")
        val favori = view.findViewById<android.widget.CheckBox>(R.id.favori)
        favori.isChecked = saisie?.getBoolean("favori") ?: contact.favori
        val sauver = view.findViewById<View>(R.id.enregistrer)
        val supprimer = view.findViewById<View>(R.id.supprimer)
        sauver.isEnabled = true
        supprimer.visibility = if (id == 0L) View.GONE else View.VISIBLE
        view.findViewById<View>(R.id.carte).visibility = supprimer.visibility

        fun modifier(action: suspend () -> Unit) {
            if (!sauver.isEnabled) return
            sauver.isEnabled = false
            supprimer.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    action()
                    model.actualiser()
                    parentFragmentManager.popBackStack()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    message(if (e is IllegalStateException) e.message ?: "Opération échouée." else "Opération échouée. Vérifie les données et la photo.")
                    sauver.isEnabled = true
                    supprimer.isEnabled = true
                }
            }
        }

        supprimer.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle("Supprimer le contact ?")
                .setMessage(contact.nomComplet)
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Supprimer") { _, _ -> modifier { model.supprimer(id) } }
                .show()
        }
        view.findViewById<ImageView>(R.id.photo).afficherPhoto(photo)

        fun clic(bouton: Int, action: () -> Unit) {
            view.findViewById<View>(bouton).setOnClickListener { action() }
        }
        clic(R.id.appeler) {
            val numero = tel.text.toString().trim()
            if (numero.isBlank()) tel.error = "Téléphone obligatoire"
            else lancer("Composeur indisponible.") {
                startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numero, null)))
            }
        }
        clic(R.id.envoyer) {
            val adresse = email.text.toString().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(adresse).matches()) email.error = "Adresse e-mail invalide"
            else lancer("Aucune application de messagerie disponible.") {
                startActivity(Intent(Intent.ACTION_SENDTO, Uri.fromParts("mailto", adresse, null)))
            }
        }
        clic(R.id.galerie) { lancer("Galerie indisponible.") { galerie.launch(arrayOf("image/*")) } }
        clic(R.id.camera) { lancer("Caméra indisponible.") { camera.launch(null) } }
        clic(R.id.annuler) { parentFragmentManager.popBackStack() }
        clic(R.id.carte) { (requireActivity() as MainActivity).ouvrir(this, CarteContactFragment(), id) }
        clic(R.id.enregistrer) {
            val nomTexte = nom.text.toString().trim()
            val prenomTexte = prenom.text.toString().trim()
            val telTexte = tel.text.toString().trim()
            val emailTexte = email.text.toString().trim()
            val latTexte = lat.text.toString().trim()
            val lngTexte = lng.text.toString().trim()
            val latitude = latTexte.replace(',', '.').toDoubleOrNull()
            val longitude = lngTexte.replace(',', '.').toDoubleOrNull()

            when {
                nomTexte.isEmpty() -> nom.error = "Nom obligatoire"
                prenomTexte.isEmpty() -> prenom.error = "Prénom obligatoire"
                telTexte.none { it.isDigit() } -> tel.error = "Téléphone invalide"
                emailTexte.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(emailTexte).matches() -> email.error = "Adresse e-mail invalide"
                latTexte.isNotEmpty() && (latitude == null || latitude !in -90.0..90.0) ->
                    lat.error = "Latitude entre −90 et 90"
                lngTexte.isNotEmpty() && (longitude == null || longitude !in -180.0..180.0) ->
                    lng.error = "Longitude entre −180 et 180"
                latTexte.isEmpty() != lngTexte.isEmpty() ->
                    message("Renseigne les deux coordonnées ou laisse les deux champs vides.")
                else -> {
                    val contexte = requireContext().applicationContext
                    val source = photo
                    val estFavori = favori.isChecked
                    modifier {
                        val copie = withContext(Dispatchers.IO) {
                            if (source == null || source == contact.photoUri) source
                            else {
                                val fichier = File.createTempFile("photo-", ".jpg", contexte.filesDir)
                                try {
                                    val flux = contexte.contentResolver.openInputStream(Uri.parse(source))
                                        ?: throw IOException("Image illisible")
                                    flux.use { entree -> fichier.outputStream().use { entree.copyTo(it) } }
                                    fichier.absolutePath
                                } catch (e: IOException) {
                                    fichier.delete()
                                    throw e
                                }
                            }
                        }
                        model.enregistrer(contact.copy(nom = nomTexte, prenom = prenomTexte, telephone = telTexte,
                            email = emailTexte.ifBlank { null }, photoUri = copie, latitude = latitude, longitude = longitude, favori = estFavori))
                    }
                }
            }
        }
    }
}
