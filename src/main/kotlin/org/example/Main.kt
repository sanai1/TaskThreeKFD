package org.example

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.random.Random

@Serializable
data class User(val id: Int, val name: String, val email: String)

@Serializable
data class Address(val street: String, val city: String, val country: String)

@Serializable
data class Profile(val user: User, val address: Address, val age: Int)

@Serializable
data class Company(val name: String, val employees: List<Profile>)

fun test() = runBlocking {
    val repeat = 10000
    val users = List(repeat) {
        User(it, "User${Random}", "user${Random}@example.com")
    }

    val addresses = List(repeat) {
        Address("Street ${Random.nextInt()}", "City ${Random.nextInt()}", "Country ${Random.nextInt()}")
    }

    val profiles = users.zip(addresses) { user: User, address: Address ->
        Profile(user, address, 20 + user.id % 50)
    }

    val company = Company("Example company", profiles)

    return@runBlocking Pair(async(company), ordinary(company))
}

fun ordinary(company: Company): Int {
    val startSerializerOrdinary = System.currentTimeMillis()
    val jsonStringOrdinary = serializeObject(company)
    val finishSerializerOrdinary = System.currentTimeMillis()
//    return (finishSerializerOrdinary - startSerializerOrdinary).toInt()

    val startDeserializerOrdinary = System.currentTimeMillis()
    val deserializeCompanyOrdinary = Deserializer.deserialize(jsonStringOrdinary, Company::class)
    val finishDeserializerOrdinary = System.currentTimeMillis()
    return (finishDeserializerOrdinary - startDeserializerOrdinary).toInt()
}

fun async(company: Company) = runBlocking {
    val startSerializer = System.currentTimeMillis()
    val json = asyncSerializeObject(company)
    val finishSerializer = System.currentTimeMillis()
//    return@runBlocking (finishSerializer - startSerializer).toInt()

    val startDeserializer = System.currentTimeMillis()
    val deserializeCompany = AsyncDeserializer.deserializeAsync(json, Company::class)
    val finishDeserializer = System.currentTimeMillis()
    return@runBlocking (finishDeserializer - startDeserializer).toInt()
}

fun main() = runBlocking {
    repeat(5) { i ->
        val pair = test()
        println("test ${i+1}")
        println("async ${pair.first}")
        println("ordinary - ${pair.second}")
        println()
    }
}








































