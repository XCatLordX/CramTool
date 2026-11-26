package com.cramtool.app.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val listSer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromList(value: List<String>): String = Json.encodeToString(listSer, value)

    @TypeConverter
    fun toList(value: String): List<String> =
        if (value.isBlank()) emptyList() else Json.decodeFromString(listSer, value)

    @TypeConverter
    fun fromType(value: QuestionType): String = value.name

    @TypeConverter
    fun toType(value: String): QuestionType = QuestionType.valueOf(value)
}
