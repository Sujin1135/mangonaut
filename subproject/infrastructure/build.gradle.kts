// Infrastructure layer: Repository implementations, External API clients, Configurations
// Depends on: domain, presentation (for service interface implementations)

dependencies {
    implementation(project(":subproject:domain"))
    implementation(project(":subproject:presentation"))
    implementation("com.auth0:java-jwt:4.5.0")
}
