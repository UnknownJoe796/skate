# Project Skate

SKT - Standalone Kotlin

## What is it?

Using Skate, you can edit and run standalone KT files like the following:

```kotlin
@file:Repository("https://jcenter.bintray.com")
@file:DependsOn("org.jetbrains.kotlinx:kotlinx-html-jvm:0.6.11")

import kotlinx.html.*
import kotlinx.html.stream.*

fun main() = print(createHTML().html {
    body {
        h1 { +"Hello World!" }
    }
})
```

Skate does **NOT** support `.kts` files (at least yet) due to some complications with their interactions with IntelliJ 
projects and the embedded Kotlin compiler.  If you're interested in helping or extending this, please contact me on the 
Kotlin Slack channel.  My username is `josephivie`.

**This is primarily an educational project** to learn working with the Kotlin compiler, Maven, and IntelliJ to complete a 
full build.  Eventually this will lead to a library for building multiplatform projects without a build system - instead 
just using a Maven library to make it more convenient.

## How do I use it?

Grab the installation from the releases and unzip it.  You'll probably want to add it to your path, then you can do 
things like this:

- `skate myFile.kt` - Runs the file
- `skate -e myFile.kt` - Opens the file as a project in IntelliJ with full completion
- `skate -p myFile.kt` - Creates/updates an IntelliJ project for the file without opening it.
- `skate -i myFile.kt` - Opens a REPL with your definitions loaded in.

## Notes

- These repositories are included by default:
    - Maven Central
    - JCenter
    - Google
    - Your local Maven `.m2` folder
    
## Differences from KScript

- Creates IntelliJ projects directly; no Gradle sync necessary
- Fully Windows compatible (in fact, programmed on Windows)
- Kotlin compiler is embedded; installing Kotlin separately is not necessary.
- No special Unix support; not build to be used as a script runner.
- Does not support `.kts` at all
    - On a side note, I recommend using normal Kotlin files anyways.  No need for multiple standards.
    - If you know how to change this such that KTS is supported, please contact me!

## Known Issues

- Under some certain circumstances, you have to run the script multiple times to get all the dependencies to resolve.  The cause of this is unknown to me at the moment.
    
## License

Apache 2.0

Please pull anything you'd like from this project into your own.  I've learned a lot messing around with these tools.
