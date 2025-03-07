package org.example

import kotlinx.coroutines.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.jvmErasure

suspend fun <T : Any> serialize(obj: T): String = coroutineScope {
    if (obj::class.isList()) {
        val list = obj as List<*>
        val job = list.map { item ->
            async {
                if (item != null) serialize(item) else "null"
            }
        }.awaitAll()
        return@coroutineScope "[" + job.joinToString(",") + "]"
    }
    val properties = obj::class.memberProperties

    val serializedProperties = properties.map { prop ->
        val value = prop.getter.call(obj)

        when {
            value == null -> "\"${prop.name}\":null"
            prop.returnType.isPrimitiveOrString() -> "\"${prop.name}\":\"${value}\""
            prop.returnType.isList() -> {
                val list = value as List<*>
                val serializedList = list.map { item ->
                    async {
                        item?.let { serialize(it) } ?: "null"
                    }
                }.awaitAll().joinToString(",")
                "\"${prop.name}\":[$serializedList]"
            }

            else -> "\"${prop.name}\":${serialize(value)}" // Для объектов
        }
    }

    return@coroutineScope "{${serializedProperties.joinToString(",")}}"
}

fun KType.isPrimitiveOrString(): Boolean {
    val simpleName = listOf("Int", "Long", "Float", "Double", "Boolean", "Char", "String")
    return when (this.jvmErasure.simpleName) {
        in simpleName -> true
        else -> false
    }
}

fun KType.isList(): Boolean {
    return this.javaType.typeName.contains("java.util.List")
}

fun KClass<*>.isList(): Boolean {
    return this.java.typeName.contains("java.util.List")
}