package org.example

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
class JsonSerializer<T> {
    private val gson = Gson()

    suspend fun serialize(obj: T): String {
        return withContext(Dispatchers.Default) {
            gson.toJson(obj)
        }
    }

    suspend fun deserialize(jsonObj: String, clazz: Class<T>): T? {
        return withContext(Dispatchers.Default) {
            try {
                gson.fromJson(jsonObj, clazz)
            } catch (e: JsonSyntaxException) {
                null
            }
        }
    }

}