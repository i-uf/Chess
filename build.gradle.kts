plugins {
    kotlin("jvm") version "1.9.23"
}

repositories {
    mavenCentral()
}
dependencies {
    implementation(kotlin("stdlib"))
}
tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.i_uf.AppKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) // 런타임 클래스 포함
}