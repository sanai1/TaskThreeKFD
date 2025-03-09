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
        User(it, "User${Random.nextInt()}", "user${Random.nextInt()}@example.com")
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

fun ordinary(company: Company): Pair<Int, Int> {
    val startSerializerOrdinary = System.currentTimeMillis()
    val jsonStringOrdinary = serializeObject(company)
    val finishSerializerOrdinary = System.currentTimeMillis()

    val startDeserializerOrdinary = System.currentTimeMillis()
    val deserializeCompanyOrdinary = Deserializer.deserialize(jsonStringOrdinary, Company::class)
    val finishDeserializerOrdinary = System.currentTimeMillis()
//    println("company == deserializeCompanyOrdinary: ${company == deserializeCompanyOrdinary}")
    return Pair(
        (finishSerializerOrdinary - startSerializerOrdinary).toInt(),
        (finishDeserializerOrdinary - startDeserializerOrdinary).toInt()
    )
}

fun async(company: Company) = runBlocking {
    val startSerializer = System.currentTimeMillis()
    val json = asyncSerializeObject(company)
    val finishSerializer = System.currentTimeMillis()

    val startDeserializer = System.currentTimeMillis()
    val deserializeCompany = AsyncDeserializer.deserializeAsync(json, Company::class)
    val finishDeserializer = System.currentTimeMillis()
//    println("company == deserializeCompany: ${company == deserializeCompany}")
    return@runBlocking Pair(
        (finishSerializer - startSerializer).toInt(),
        (finishDeserializer - startDeserializer).toInt()
    )
}

fun main() = runBlocking {
    repeat(5) { i ->
        val pair = test()
        println("test ${i+1}\n")
        println("Serialize:")
        println("async - ${pair.first.first}")
        println("ordinary - ${pair.second.first}\n")
        println("Deserialize:")
        println("async - ${pair.first.second}")
        println("ordinary - ${pair.second.second}")
        println("-----------------------------------------")
    }
}
