package io.autofixer.mangonaut

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MangonautApplication

fun main(args: Array<String>) {
    runApplication<MangonautApplication>(*args)
}
