plugins {
  id("org.jetbrains.kotlin.jvm")
  id("apollo.test")
  id("com.apollographql.apollo3")
}

dependencies {
  implementation(libs.apollo.runtime)
  implementation(libs.apollo.mockserver)
  testImplementation(libs.kotlin.test)
  testImplementation(libs.junit)
  testImplementation(libs.turbine)
  testImplementation(libs.apollo.testingsupport)
}

apollo {
  service("service") {
    packageName.set("testnetworktransport")
    generateDataBuilders.set(true)
  }
}
