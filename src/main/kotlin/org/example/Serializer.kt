package org.example

import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.reflect.full.memberProperties

private const val BATCH_SIZE = 100

suspend fun <T : Any> asyncSerializeObject(obj: T): String = coroutineScope {
    when (obj) {
        is List<*> -> {
            val jsonList = obj
                .asFlow()
                .flatMapMerge(concurrency = BATCH_SIZE) { item ->
                    flow {
                        emit(
                            if (item != null) {
                                asyncSerializeObject(item)
                            } else {
                                "null"
                            }
                        )
                    }
                }
                .buffer(BATCH_SIZE)
                .toList()
            "[${jsonList.joinToString(",")}]"
        }
        is String, is Number, is Boolean -> Gson().toJson(obj)
        else -> {
            val kClass = obj::class
            val properties = kClass.memberProperties
            val fields = properties.map { property ->
                async(Dispatchers.Default) {
                    val fieldName = property.name
                    val fieldValue = property.getter.call(obj)
                    val serializedValue = if (fieldValue != null) {
                        asyncSerializeObject(fieldValue)
                    } else {
                        "null"
                    }
                    """"$fieldName":$serializedValue"""
                }
            }.awaitAll().joinToString(",")
            "{$fields}"
        }
    }
}

fun <T : Any> serializeObject(obj: T): String =
    when (obj) {
        is List<*> -> {
            val results = obj.map { item ->
                if (item != null) {
                    serializeObject(item)
                } else {
                    "null"
                }
            }
            "[${results.joinToString(",")}]"
        }

        is String, is Number, is Boolean -> Gson().toJson(obj)
        else -> {
            val kClass = obj::class
            val fields = kClass.memberProperties.map { property ->
                val fieldName = property.name
                val fieldValue = property.getter.call(obj)
                val serializedValue = if (fieldValue != null) {
                    serializeObject(fieldValue)
                } else {
                    "null"
                }
                """"$fieldName":$serializedValue"""
            }
            "{${fields.joinToString(",")}}"
        }
    }