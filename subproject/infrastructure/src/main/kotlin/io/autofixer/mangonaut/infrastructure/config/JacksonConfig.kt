package io.autofixer.mangonaut.infrastructure.config

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import tools.jackson.core.JsonParser
import tools.jackson.core.JsonToken
import tools.jackson.databind.DeserializationContext
import tools.jackson.databind.JavaType
import tools.jackson.databind.MapperFeature
import tools.jackson.databind.deser.DeserializationProblemHandler

@Configuration
class JacksonConfig {

    @Bean
    fun enumTolerantJsonMapperCustomizer(): JsonMapperBuilderCustomizer =
        JsonMapperBuilderCustomizer { builder ->
            builder
                .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true)
                .addHandler(UnknownEnumValueAsNullHandler)
        }

    private object UnknownEnumValueAsNullHandler : DeserializationProblemHandler() {
        override fun handleWeirdStringValue(
            ctx: DeserializationContext,
            targetType: Class<*>,
            valueToConvert: String,
            failureMsg: String,
        ): Any? = if (targetType.isEnum) null else NOT_HANDLED

        override fun handleUnexpectedToken(
            ctx: DeserializationContext,
            targetType: JavaType,
            token: JsonToken,
            parser: JsonParser,
            failureMsg: String?,
        ): Any? = if (targetType.rawClass.isEnum) null else NOT_HANDLED
    }
}
