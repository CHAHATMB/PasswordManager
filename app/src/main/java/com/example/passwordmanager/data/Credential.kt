package com.example.passwordmanager.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Credential (

    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val website: String,
    val username: String,
    val password: String,
    val dataCreate: Long,
    val isFav: Boolean = false,
    val note: String,
    /** 0 until the first edit; rows from before DB v4 and imports also start at 0. */
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = 0,
//    val iv: String? = null,
//
//    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
//    val ivBlob: ByteArray?,
//
//    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
//    val passwordBlob: ByteArray?
)
