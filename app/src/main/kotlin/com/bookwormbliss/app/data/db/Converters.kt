package com.bookwormbliss.app.data.db

import androidx.room.TypeConverter
import com.bookwormbliss.app.data.model.HighlightColor
import com.bookwormbliss.app.data.model.ReadingStampType

/** Room stores our enums as their plain name() string — small, stable, human-readable in DB dumps. */
class Converters {
    @TypeConverter
    fun highlightColorToString(value: HighlightColor): String = value.name

    @TypeConverter
    fun stringToHighlightColor(value: String): HighlightColor =
        runCatching { HighlightColor.valueOf(value) }.getOrDefault(HighlightColor.YELLOW)

    @TypeConverter
    fun stampTypeToString(value: ReadingStampType): String = value.name

    @TypeConverter
    fun stringToStampType(value: String): ReadingStampType =
        runCatching { ReadingStampType.valueOf(value) }.getOrDefault(ReadingStampType.STARTED)
}
