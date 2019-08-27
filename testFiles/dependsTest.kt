@file:Repository("https://dl.bintray.com/lightningkite/com.lightningkite.kotlin")
@file:DependsOn("com.lightningkite.kotlin:okhttp-jackson:0.6.5")

package com.test

import lk.kotlin.jvm.utils.async.Async
import lk.kotlin.okhttp.defaultClient
import lk.kotlin.okhttp.jackson.lambdaJackson
import okhttp3.Request
import okhttp3.Response

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

    defaultClient.dispatcher().executorService().shutdown()
    Async.shutdown()
    println("Shut down")
}
//Test comment 2
