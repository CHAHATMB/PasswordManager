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
//    val iv: String? = null,
//
//    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
//    val ivBlob: ByteArray?,
//
//    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
//    val passwordBlob: ByteArray?
)
