package com.example.passwordmanager.data.notes

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class Note(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long
) {
    override fun toString() = "Note(id=$id, createdAt=$createdAt, updatedAt=$updatedAt)"
}

/** A list row: the start of the body only, so long notes don't fill the cursor window. */
data class NoteSummary(
    val id: Int,
    val title: String,
    val preview: String,
    val updatedAt: Long
)
