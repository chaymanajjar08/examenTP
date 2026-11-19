package fr.tp.contactsv4

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import fr.tp.contactsv4.data.OrdreContacts
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager

data class Reglages(val taille: Float, val couleur: Int, val appel: Boolean)

object PreferencesManager {
    fun ordre(context: Context): OrdreContacts {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return when (prefs.getString("ordre_tri", "favoris")) {
            "nom" -> OrdreContacts.NOM
            "prenom" -> OrdreContacts.PRENOM
            else -> OrdreContacts.FAVORIS
        }
    }

    fun lire(context: Context): Reglages {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return Reglages(
            prefs.getString("taille_police", "16")?.toFloatOrNull() ?: 16f,
            Color.parseColor(prefs.getString("couleur_preferee", "#000000") ?: "#000000"),
            prefs.getBoolean("appel_depuis_liste", false)
        )
    }
}

class ParametresFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
