package com.example.passwordmanager.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [Credential::class], version = 1)
abstract class CredentialDatabase: RoomDatabase() {
    abstract val dao: CredentialDao

}