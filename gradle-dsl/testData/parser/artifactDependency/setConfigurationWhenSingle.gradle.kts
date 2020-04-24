dependencies {
  val version by extra("2.0")
  val sampleDep by extra("a:b:1.0")
  test("org.gradle.test.classifiers:service:1.0:jdk15@jar")
  compile(sampleDep)
  api(sampleDep)
  testCompile("org.hibernate:hibernate:3.1") {
    force = true
  }
  implementation(group="com.example", name="artifact", version="1.0")
  api(group="org.example", name="artifact", version="2.0")
  debug(mapOf("group" to "org.example", "name" to "artifact", "version" to "$version")) {
    force = true
  }
}
