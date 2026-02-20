package io.autofixer.mangonaut

import io.autofixer.mangonaut.infrastructure.config.MangonautProperties
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication(
    scanBasePackages = ["io.autofixer.mangonaut"]
)
@EnableConfigurationProperties(MangonautProperties::class)
@EnableScheduling
class MangonautApplication

fun main(args: Array<String>) {
    runApplication<MangonautApplication>(*args)
}
