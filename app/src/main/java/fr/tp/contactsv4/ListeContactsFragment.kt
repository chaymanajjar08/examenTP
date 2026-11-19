package fr.tp.contactsv4

import android.content.Context
import fr.tp.contactsv4.data.Contact
import android.content.Intent
import android.net.Uri
import android.content.ActivityNotFoundException
import android.widget.Toast
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class ListeContactsFragment : Fragment(R.layout.fragment_liste) {
    private val model: ContactViewModel by activityViewModels()

    private var contactsAdapter: ContactsAdapter? = null

    override fun onResume() {
        super.onResume()
        contactsAdapter?.appliquer(PreferencesManager.lire(requireContext()))
        model.actualiser()
    }

    override fun onDestroyView() {
        contactsAdapter = null
        super.onDestroyView()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val recherche = view.findViewById<EditText>(R.id.recherche)
        val vide = view.findViewById<TextView>(R.id.vide)
        val liste = view.findViewById<RecyclerView>(R.id.liste)
        val adapter = ContactsAdapter { contact ->
            val clavier = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            clavier.hideSoftInputFromWindow(recherche.windowToken, 0)
            (requireActivity() as MainActivity).ouvrir(this, DetailContactFragment(), contact.id)
        }
        liste.layoutManager = LinearLayoutManager(requireContext())
        liste.adapter = adapter
        contactsAdapter = adapter
        view.findViewById<View>(R.id.ajouter).setOnClickListener {
            (requireActivity() as MainActivity).ouvrir(this, DetailContactFragment())
        }
        model.erreurListe.observe(viewLifecycleOwner) { erreur ->
            if (erreur != null) {
                vide.text = erreur
                vide.isVisible = true
            }
        }
        recherche.setText(model.filtre)

        fun afficher(contacts: List<Contact>) {
            val terme = model.filtre.trim()
            val resultats = contacts.filter {
                it.nom.contains(terme, true) || it.prenom.contains(terme, true) ||
                    it.telephone.contains(terme, true)
            }
            adapter.submitList(resultats)
            vide.text = "Aucun contact trouvé"
            vide.isVisible = resultats.isEmpty()
        }

        recherche.doAfterTextChanged {
            model.filtre = it.toString()
            afficher(model.contacts.value.orEmpty())
        }
        model.contacts.observe(viewLifecycleOwner) { afficher(it) }
    }
}

private class ContactsAdapter(private val clic: (Contact) -> Unit) :
    ListAdapter<Contact, ContactsAdapter.Ligne>(object : DiffUtil.ItemCallback<Contact>() {
        override fun areItemsTheSame(oldItem: Contact, newItem: Contact) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Contact, newItem: Contact) = oldItem == newItem
    }) {
    private var reglages = Reglages(16f, android.graphics.Color.BLACK, false)

    fun appliquer(valeurs: Reglages) {
        if (reglages == valeurs) return
        reglages = valeurs
        notifyItemRangeChanged(0, itemCount)
    }

    class Ligne(view: View) : RecyclerView.ViewHolder(view) {
        val nom = view.findViewById<TextView>(R.id.nom_complet)
        val numero = view.findViewById<TextView>(R.id.numero)
        val appeler = view.findViewById<View>(R.id.appel_ligne)
        val photo = view.findViewById<ImageView>(R.id.photo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Ligne =
        Ligne(LayoutInflater.from(parent.context).inflate(R.layout.ligne_contact, parent, false))

    override fun onBindViewHolder(holder: Ligne, position: Int) {
        val contact = getItem(position)
        holder.nom.text = if (contact.favori) "★ ${contact.nomComplet}" else contact.nomComplet
        holder.numero.text = contact.telephone
        holder.photo.afficherPhoto(contact.photoUri)
        holder.nom.textSize = reglages.taille
        holder.nom.setTextColor(reglages.couleur)
        holder.numero.textSize = reglages.taille
        holder.appeler.isVisible = reglages.appel
        holder.appeler.setOnClickListener {
            val contexte = it.context
            val numero = contact.telephone.trim()
            if (numero.isEmpty()) {
                Toast.makeText(contexte, "Numéro vide.", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    contexte.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numero, null)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(contexte, "Composeur indisponible.", Toast.LENGTH_SHORT).show()
                } catch (_: SecurityException) {
                    Toast.makeText(contexte, "Composeur indisponible.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        holder.itemView.setOnClickListener { clic(contact) }
    }
}
