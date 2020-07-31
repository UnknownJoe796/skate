package com.test

val hello = "Hello World!"

fun main(vararg args: String) {
    println(hello)
    println("args: ${args.toList()}")
}