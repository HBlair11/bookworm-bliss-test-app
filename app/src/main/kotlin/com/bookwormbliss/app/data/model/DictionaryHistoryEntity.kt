package com.bookwormbliss.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dictionary_history")
data class DictionaryHistoryEntity(
    @PrimaryKey val id: String,
    val word: String,
    val partOfSpeech: String,
    val definition: String,
    val example: String? = null,
    val bookTitle: String? = null,
    val lookedUpDate: Long,
)
