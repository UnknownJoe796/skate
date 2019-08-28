@file:Import("https://gist.githubusercontent.com/UnknownJoe796/a243c44985b23956d6a9ce841c2fa049/raw/7a6173b2c99be5e04c23b0165ff4ce1b0d8da106/HumanHash.kt")

package com.test

import com.ivieleague.humanhash.humanHash
import java.util.*

fun main() {
    val scanner = Scanner(System.`in`)
    println("Hello!  Use 'q' or 'quit' to quit, anything else WILL BE HASHED!")
    while (true) {
        val line = scanner.nextLine()
        when (line) {
            "quit", "q" -> return
            else -> println("The human hash is: ${line.humanHash()}")
        }
    }
}