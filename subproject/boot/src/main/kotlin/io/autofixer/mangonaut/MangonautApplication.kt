package io.autofixer.mangonaut

import io.autofixer.mangonaut.infrastructure.config.MangonautProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication

@SpringBootApplication(
    scanBasePackages = ["io.autofixer.mangonaut"]
)
@EnableConfigurationProperties(MangonautProperties::class)
class MangonautApplication

fun main(args: Array<String>) {
    runApplication<MangonautApplication>(*args)
}
