package org.example

import kotlinx.coroutines.runBlocking

data class User(val name: String, val secondName: String, val age: Int)

fun main() = runBlocking {
    val user = User("FirstName", "SecondName", 18)

    val userSerializer = JsonSerializer<User>()

    var jsonString = userSerializer.serialize(user)
    println(jsonString)

    jsonString = jsonString.replace("FirstName", "TestName")

    val deserializeUser = userSerializer.deserialize(jsonString, User::class.java)
    println(deserializeUser)
}