package fr.tp.contactsv4.data

import android.content.ContentValues
import android.content.Context
import android.os.SystemClock
import java.io.File
import fr.tp.contactsv4.data.ContactContract.FAVORI
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import fr.tp.contactsv4.data.ContactContract.TABLE
import fr.tp.contactsv4.data.ContactContract.ID
import fr.tp.contactsv4.data.ContactContract.NOM
import fr.tp.contactsv4.data.ContactContract.PRENOM
import fr.tp.contactsv4.data.ContactContract.TEL
import fr.tp.contactsv4.data.ContactContract.EMAIL
import fr.tp.contactsv4.data.ContactContract.PHOTO
import fr.tp.contactsv4.data.ContactContract.LAT
import fr.tp.contactsv4.data.ContactContract.LNG

enum class OrdreContacts { NOM, PRENOM, FAVORIS }

data class TempsImport(val avec: Double, val sans: Double)

class ContactDao(private val helper: ContactDbHelper) {
    fun insert(contact: Contact): Long =
        helper.writableDatabase.insertOrThrow(TABLE, null, valeurs(contact))

    fun update(contact: Contact): Int =
        helper.writableDatabase.update(TABLE, valeurs(contact), "$ID = ?", arrayOf(contact.id.toString()))

    fun delete(id: Long): Int =
        helper.writableDatabase.delete(TABLE, "$ID = ?", arrayOf(id.toString()))

    fun getAll(ordre: OrdreContacts = OrdreContacts.FAVORIS): List<Contact> {
        val tri = when (ordre) {
            OrdreContacts.NOM -> TRI_NOM
            OrdreContacts.PRENOM -> "$PRENOM COLLATE NOCASE, $NOM COLLATE NOCASE, $ID"
            OrdreContacts.FAVORIS -> "$FAVORI DESC, $TRI_NOM"
        }
        return lire(tri = tri)
    }

    fun importer100(context: Context): TempsImport {
        val lot = (1..100).map { numero ->
            val suffixe = numero.toString().padStart(3, '0')
            Contact(nom = "Démo $suffixe", prenom = "Import", telephone = "00000$suffixe")
        }
        fun mesurer(transaction: Boolean): Double {
            val fichier = File.createTempFile("comparaison-", ".db", context.cacheDir)
            try {
                return SQLiteDatabase.openOrCreateDatabase(fichier, null).use { db ->
                    creerTable(db)
                    val debut = SystemClock.elapsedRealtimeNanos()
                    insererLot(db, lot, transaction)
                    (SystemClock.elapsedRealtimeNanos() - debut) / 1_000_000.0
                }
            } finally {
                SQLiteDatabase.deleteDatabase(fichier)
            }
        }

        val sans = mesurer(false)
        val avec = mesurer(true)
        insererLot(helper.writableDatabase, lot, true)
        return TempsImport(avec, sans)
    }

    private fun insererLot(db: SQLiteDatabase, lot: List<Contact>, transaction: Boolean) {
        if (transaction) db.beginTransaction()
        try {
            lot.forEach { db.insertOrThrow(TABLE, null, valeurs(it)) }
            if (transaction) db.setTransactionSuccessful()
        } finally {
            if (transaction) db.endTransaction()
        }
    }

    fun getById(id: Long): Contact? = lire("$ID = ?", arrayOf(id.toString())).firstOrNull()

    fun search(texte: String): List<Contact> = getAll().filter {
        it.nom.contains(texte, true) || it.prenom.contains(texte, true) ||
            it.telephone.contains(texte, true)
    }

    private fun lire(selection: String? = null, args: Array<String>? = null, tri: String = TRI_NOM): List<Contact> =
        helper.readableDatabase.query(TABLE, null, selection, args, null, null,
            tri).use { cursor ->
            buildList {
                while (cursor.moveToNext()) add(ligne(cursor))
            }
        }

    private fun ligne(cursor: Cursor): Contact {
        fun index(colonne: String) = cursor.getColumnIndexOrThrow(colonne)
        fun reel(colonne: String) = index(colonne).let { if (cursor.isNull(it)) null else cursor.getDouble(it) }
        return Contact(cursor.getLong(index(ID)), cursor.getString(index(NOM)),
            cursor.getString(index(PRENOM)), cursor.getString(index(TEL)),
            cursor.getString(index(EMAIL)), cursor.getString(index(PHOTO)), reel(LAT), reel(LNG), cursor.getInt(index(FAVORI)) != 0)
    }

    companion object {
        private const val TRI_NOM = "$NOM COLLATE NOCASE, $PRENOM COLLATE NOCASE, $ID"

        internal fun migrerVers2(db: SQLiteDatabase) {
            db.execSQL("ALTER TABLE $TABLE ADD COLUMN $FAVORI INTEGER NOT NULL DEFAULT 0")
        }
        private fun valeurs(c: Contact) = ContentValues().apply {
            put(NOM, c.nom)
            put(PRENOM, c.prenom)
            put(TEL, c.telephone)
            put(EMAIL, c.email)
            put(PHOTO, c.photoUri)
            put(LAT, c.latitude)
            put(LNG, c.longitude)
            put(FAVORI, if (c.favori) 1 else 0)
        }

        private fun creerTable(db: SQLiteDatabase) {
            db.execSQL("""
                CREATE TABLE $TABLE (
                    $ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $NOM TEXT NOT NULL,
                    $PRENOM TEXT NOT NULL,
                    $TEL TEXT NOT NULL,
                    $EMAIL TEXT,
                    $PHOTO TEXT,
                    $LAT REAL,
                    $LNG REAL,
                    $FAVORI INTEGER NOT NULL DEFAULT 0
                )
            """.trimIndent())
        }

        internal fun initialiser(db: SQLiteDatabase) {
            creerTable(db)
            listOf(
                Contact(nom = "Mansour", prenom = "Leïla", telephone = "00000001", latitude = 33.875, longitude = 10.858),
                Contact(nom = "Trabelsi", prenom = "Amine", telephone = "00000002", latitude = 36.807, longitude = 10.181),
                Contact(nom = "Ben Salem", prenom = "Nour", telephone = "00000003"),
                Contact(nom = "O'Neil", prenom = "Sami", telephone = "00000004"),
                Contact(nom = "Jaziri", prenom = "Inès", telephone = "00000005", latitude = 34.74, longitude = 10.76)
            ).forEach { db.insertOrThrow(TABLE, null, valeurs(it)) }
        }
    }
}
