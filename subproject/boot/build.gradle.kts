// Boot module: Application entry point, assembles all layers

dependencies {
    implementation(project(":subproject:domain"))
    implementation(project(":subproject:application"))
    implementation(project(":subproject:presentation"))
    implementation(project(":subproject:infrastructure"))
}

springBoot {
    mainClass.set("io.autofixer.mangonaut.MangonautApplicationKt")
}
