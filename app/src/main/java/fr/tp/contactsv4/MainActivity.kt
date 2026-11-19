package fr.tp.contactsv4

import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.commit

class MainActivity : FragmentActivity(R.layout.activity_main) {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val menu = findViewById<android.widget.Toolbar>(R.id.barre).menu
        val model = androidx.lifecycle.ViewModelProvider(this)[ContactViewModel::class.java]
        val importer = menu.add("Importer 100 contacts")
        importer.setOnMenuItemClickListener {
            android.app.AlertDialog.Builder(this)
                .setTitle("Importer 100 contacts ?")
                .setMessage("100 contacts de démonstration seront ajoutés et deux durées seront mesurées.")
                .setNegativeButton("Annuler", null)
                .setPositiveButton("Importer") { _, _ -> model.importer() }
                .show()
            true
        }
        model.importEnCours.observe(this) { importer.isEnabled = !it }
        model.resultatImport.observe(this) { resultat ->
            if (resultat != null) {
                model.resultatImport.value = null
                android.app.AlertDialog.Builder(this)
                    .setTitle("Import de contacts")
                    .setMessage(resultat)
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
        menu.add("Paramètres").setOnMenuItemClickListener {
            val source = supportFragmentManager.findFragmentById(R.id.container)
            if (source != null && source !is ParametresFragment) ouvrir(source, ParametresFragment())
            true
        }
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.container, ListeContactsFragment())
            }
        }
    }

    fun ouvrir(source: Fragment, destination: Fragment, id: Long = 0L) {
        val manager = supportFragmentManager
        if (manager.isStateSaved || manager.findFragmentById(R.id.container) !== source) return
        destination.arguments = bundleOf("contact_id" to id)
        manager.commit {
            setReorderingAllowed(true)
            replace(R.id.container, destination)
            addToBackStack(null)
        }
        manager.executePendingTransactions()
    }
}
