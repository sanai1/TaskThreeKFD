import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.full.memberProperties

// Асинхронный десериализатор
object AsyncDeserializer {

    // Основная функция десериализации
    suspend fun <T : Any> deserializeAsync(json: String, clazz: KClass<T>): T = coroutineScope {
        val jsonElement = Json.parseToJsonElement(json)
        return@coroutineScope deserializeJsonElement(jsonElement, clazz) as T
    }

    // Рекурсивная функция для десериализации JsonElement
    private suspend fun <T : Any> deserializeJsonElement(jsonElement: JsonElement, clazz: KClass<T>): Any? = coroutineScope {
        when (jsonElement) {
            is JsonObject -> {
                // Получаем первичный конструктор класса
                val constructor = clazz.primaryConstructor
                    ?: throw IllegalArgumentException("Class ${clazz.simpleName} must have a primary constructor")

                // Параллельно десериализуем все поля
                val args = constructor.parameters.associateWith { param ->
                    val fieldName = param.name
                    val fieldValue = jsonElement[fieldName]
                    if (fieldValue != null) {
                        // Рекурсивно десериализуем значение поля
                        async { deserializeJsonElement(fieldValue, getClassForType(param.type)) }
                    } else {
                        // Если поле отсутствует, используем значение по умолчанию (null)
                        async { null }
                    }
                }.mapValues { it.value.await() } // Ожидаем завершения всех задач

                // Создаем объект с использованием десериализованных значений
                constructor.callBy(args)
            }
            is JsonArray -> {
                // Определяем тип элементов массива
                val elementClass = getClassForType(clazz)
                // Десериализуем каждый элемент массива параллельно
                val jobs = jsonElement.map { element ->
                    async { deserializeJsonElement(element, elementClass) }
                }
                // Ожидаем завершения всех задач и возвращаем список
                jobs.awaitAll()
            }
            is JsonPrimitive -> {
                // Десериализация примитивных значений
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

    // Вспомогательная функция для получения KClass из типа
    private fun <T : Any> getClassForType(type: KClass<T>): KClass<*> {
        return when (type) {
            List::class -> Any::class // Для списков используем Any как тип элементов
            else -> type
        }
    }

    private fun getClassForType(type: KType): KClass<*> {
        // Если тип является обобщенным (например, List<String>), возвращаем его raw-тип (List::class)
        return when (val classifier = type.classifier) {
            is KClass<*> -> classifier
            else -> throw IllegalArgumentException("Unsupported type: $type")
        }
    }
}

// Пример класса для десериализации
data class MyData(
    val field1: String,
    val field2: Int,
    val field3: List<MyData>,
    val field4: MyData?
)

// Пример использования
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