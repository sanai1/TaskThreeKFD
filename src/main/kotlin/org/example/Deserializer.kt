import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.*

object AsyncDeserializer {
    suspend fun <T : Any> deserializeAsync(json: String, clazz: KClass<T>): T = coroutineScope {
        val jsonElement = Json.parseToJsonElement(json)
        return@coroutineScope deserializeJsonElement(jsonElement, clazz) as T
    }

    private suspend fun <T : Any> deserializeJsonElement(jsonElement: JsonElement, clazz: KClass<T>): Any? =
        coroutineScope {
            when (jsonElement) {
                is JsonObject -> {
                    val constructor = clazz.primaryConstructor
                        ?: throw IllegalArgumentException("Class ${clazz.simpleName} must have a primary constructor")
                    val args = constructor.parameters.associateWith { param ->
                        val fieldName = param.name
                        val fieldValue = jsonElement[fieldName]!!
                        async { deserializeJsonElement(fieldValue, getClassForType(param.type)) }
                    }.mapValues { it.value.await() }
                    constructor.callBy(args)
                }

                is JsonArray -> {
                    val elementClass = getClassForType(clazz)
                    val jobs = jsonElement.map { element ->
                        async { deserializeJsonElement(element, elementClass) }
                    }
                    jobs.awaitAll()
                }

                is JsonPrimitive -> {
                    when {
                        jsonElement.isString -> jsonElement.content
                        jsonElement.booleanOrNull != null -> jsonElement.boolean
                        jsonElement.intOrNull != null -> jsonElement.int
                        jsonElement.longOrNull != null -> jsonElement.long
                        jsonElement.doubleOrNull != null -> jsonElement.double
                        jsonElement.toString() == "null" -> null
                        else -> throw IllegalArgumentException("Unsupported primitive type: $jsonElement")
                    }
                }

                else -> throw IllegalArgumentException("Unsupported JSON element type: $jsonElement")
            }
        }

    private fun <T : Any> getClassForType(type: KClass<T>): KClass<*> {
        return when (type) {
            List::class -> Any::class
            else -> type
        }
    }

    private fun getClassForType(type: KType): KClass<*> {
        return when (val classifier = type.classifier) {
            is KClass<*> -> classifier
            else -> throw IllegalArgumentException("Unsupported type: $type")
        }
    }
}

data class MyData(
    val field1: String,
    val field2: Int,
    val field3: List<MyData>,
    val field4: MyData?,
)

fun main() = runBlocking {
    val json = """
        {
            "field1": "value1",
            "field2": 42,
            "field3": [
                {
                    "field1": "nested1",
                    "field2": 1,
                    "field3": [],
                    "field4": null
                },
                {
                    "field1": "nested2",
                    "field2": 2,
                    "field3": [],
                    "field4": null
                }
            ],
            "field4": null
        }
    """

    val result = AsyncDeserializer.deserializeAsync(json, MyData::class)
    println(result)
}