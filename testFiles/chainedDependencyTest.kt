@file:Import("dependsTest.kt")
@file:Import("includesTest.kt")

package com.test

import lk.kotlin.jvm.utils.async.Async
import lk.kotlin.okhttp.defaultClient
import lk.kotlin.okhttp.jackson.lambdaJackson
import okhttp3.Request
import okhttp3.Response
import com.ivieleague.humanhash.humanHash

fun main() {
    println(hello.humanHash())
}