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
    var isFav: Boolean = false,
    val note: String,
//    val iv: String? = null,
//
//    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
//    val ivBlob: ByteArray?,
//
//    @ColumnInfo(typeAffinity = ColumnInfo.BLOB)
//    val passwordBlob: ByteArray?
){
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Credential) return false
        return id == other.id && username == other.username && password == other.password && isFav == other.isFav
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + username.hashCode()
        result = 31 * result + password.hashCode()
        result = 31 * result + isFav.hashCode()
        return result
    }
}