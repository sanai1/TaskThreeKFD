package org.example

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.createType

object AsyncDeserializer {
    suspend fun <T : Any> deserializeAsync(json: String, clazz: KClass<T>): T = coroutineScope {
        val jsonElement = Json.parseToJsonElement(json)
        val result = deserializeJsonElement(jsonElement, clazz.createType())
        return@coroutineScope result as T
    }

    private suspend fun deserializeJsonElement(jsonElement: JsonElement, type: KType): Any? = coroutineScope {
        when (jsonElement) {
            is JsonObject -> {
                val clazz = type.classifier as KClass<*>
                val constructor = clazz.primaryConstructor
                    ?: throw IllegalArgumentException("Class ${clazz.simpleName} must have a primary constructor")
                val args = constructor.parameters.associateWith { param ->
                    val fieldName = param.name
                    val fieldValue = jsonElement[fieldName]!!
                    async {
                        deserializeJsonElement(fieldValue, param.type)
                    }
                }.mapValues { it.value.await() }
                constructor.callBy(args)
            }
            is JsonArray -> {
                val elementType = type.arguments.firstOrNull()?.type
                    ?: throw IllegalArgumentException("List type must have a type argument")
                val jobs = jsonElement.map { element ->
                    async {
                        deserializeJsonElement(element, elementType)
                    }
                }
                jobs.awaitAll()
            }
            is JsonPrimitive -> getPrimitive(jsonElement)
            else -> throw IllegalArgumentException("Unsupported JSON element type: $jsonElement")
        }
    }
}

object Deserializer {
    fun <T : Any> deserialize(json: String, clazz: KClass<T>): T  {
        val jsonElement = Json.parseToJsonElement(json)
        val result = deserializeJsonElement(jsonElement, clazz.createType())
        return result as T
    }

    private fun deserializeJsonElement(jsonElement: JsonElement, type: KType): Any? {
        return when (jsonElement) {
            is JsonObject -> {
                val clazz = type.classifier as KClass<*>
                val constructor = clazz.primaryConstructor
                    ?: throw IllegalArgumentException("Class ${clazz.simpleName} must have a primary constructor")
                val args = constructor.parameters.associateWith { param ->
                    val fieldName = param.name
                    val fieldValue = jsonElement[fieldName]!!
                    deserializeJsonElement(fieldValue, param.type)
                }.mapValues { it.value }
                constructor.callBy(args)
            }
            is JsonArray -> {
                val elementType = type.arguments.firstOrNull()?.type
                    ?: throw IllegalArgumentException("List type must have a type argument")
                jsonElement.map { element ->
                    deserializeJsonElement(element, elementType)
                }
            }
            is JsonPrimitive -> getPrimitive(jsonElement)
            else -> throw IllegalArgumentException("Unsupported JSON element type: $jsonElement")
        }
    }
}

private fun getPrimitive(jsonElement: JsonPrimitive) = when {
    jsonElement.isString -> jsonElement.content
    jsonElement.booleanOrNull != null -> jsonElement.boolean
    jsonElement.intOrNull != null -> jsonElement.int
    jsonElement.longOrNull != null -> jsonElement.long
    jsonElement.doubleOrNull != null -> jsonElement.double
    jsonElement.toString() == "null" -> null
    else -> throw IllegalArgumentException("Unsupported primitive type: $jsonElement")
}