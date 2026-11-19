package fr.tp.contactsv4

import android.app.Application
import android.net.Uri
import android.widget.ImageView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import fr.tp.contactsv4.data.Contact
import fr.tp.contactsv4.data.ContactDao
import fr.tp.contactsv4.data.ContactDbHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ContactViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = ContactDao(ContactDbHelper.obtenir(app))
    val contacts = MutableLiveData<List<Contact>>()
    val erreurListe = MutableLiveData<String?>()
    var filtre = ""

    val importEnCours = MutableLiveData(false)
    val resultatImport = MutableLiveData<String?>()

    fun importer() {
        if (importEnCours.value == true) return
        importEnCours.value = true
        viewModelScope.launch {
            try {
                val temps = withContext(Dispatchers.IO) { dao.importer100(getApplication<Application>()) }
                actualiser()
                resultatImport.value = "100 contacts ajoutés.\nAvec transaction : %.1f ms\nSans transaction : %.1f ms"
                    .format(temps.avec, temps.sans)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                resultatImport.value = "Impossible d’importer le lot."
            } finally {
                importEnCours.value = false
            }
        }
    }

    fun actualiser() = viewModelScope.launch {
        try {
            contacts.value = withContext(Dispatchers.IO) { dao.getAll(PreferencesManager.ordre(getApplication<Application>())) }
            erreurListe.value = null
        } catch (_: android.database.SQLException) {
            erreurListe.value = "Impossible de charger les contacts."
        }
    }

    suspend fun trouver(id: Long) = withContext(Dispatchers.IO) { dao.getById(id) }

    suspend fun enregistrer(contact: Contact) = withContext(Dispatchers.IO) {
        if (contact.id == 0L) check(dao.insert(contact) > 0) { "Insertion échouée." }
        else check(dao.update(contact) == 1) { "Contact introuvable." }
    }

    suspend fun supprimer(id: Long) = withContext(Dispatchers.IO) {
        check(dao.delete(id) == 1) { "Contact introuvable." }
    }
}

fun ImageView.afficherPhoto(photo: String?) {
    setImageDrawable(null)
    if (photo != null) {
        try {
            val uri = if (photo.startsWith("content:") || photo.startsWith("file:")) Uri.parse(photo)
                else Uri.fromFile(java.io.File(photo))
            setImageURI(uri)
        } catch (_: SecurityException) {
        }
    }
    if (drawable == null) setImageResource(android.R.drawable.ic_menu_myplaces)
}
