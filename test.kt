@file:Repository("https://dl.bintray.com/lightningkite/com.lightningkite.kotlin")
@file:DependsOn("com.lightningkite.kotlin:okhttp-jackson:0.6.3")

package com.test

import lk.kotlin.okhttp.jackson.lambdaJackson
import okhttp3.Request

val hello = "Hello World!"

data class Post(
    var userId: Int = 0,
    var id: Int = 0,
    var title: String = "",
    var body: String = ""
)

fun main() {
    println(hello)

    Request.Builder().url("https://jsonplaceholder.typicode.com/posts")
        .get()
        .lambdaJackson<List<Post>>()
        .invoke()
        .result?.let {
        it.forEach { println(it) }
    }
}

//Test comment 2