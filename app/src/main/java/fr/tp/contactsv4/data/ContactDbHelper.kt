package fr.tp.contactsv4.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ContactDbHelper private constructor(context: Context) :
    SQLiteOpenHelper(context, "contacts.db", null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) = ContactDao.initialiser(db)

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion == 1 && newVersion == 2) ContactDao.migrerVers2(db)
        else error("Migration manquante : $oldVersion vers $newVersion")
    }

    companion object {
        const val DATABASE_VERSION = 2
        @Volatile private var instance: ContactDbHelper? = null

        fun obtenir(context: Context): ContactDbHelper = instance ?: synchronized(this) {
            instance ?: ContactDbHelper(context.applicationContext).also { instance = it }
        }
    }
}
