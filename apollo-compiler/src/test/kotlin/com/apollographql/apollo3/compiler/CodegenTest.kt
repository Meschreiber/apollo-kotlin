package com.apollographql.apollo3.compiler

import com.apollographql.apollo3.compiler.TestUtils.checkTestFixture
import com.apollographql.apollo3.compiler.TestUtils.shouldUpdateMeasurements
import com.apollographql.apollo3.compiler.TestUtils.shouldUpdateTestFixtures
import org.junit.AfterClass
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@RunWith(Parameterized::class)
@OptIn(ExperimentalTime::class)
class CodegenTest(private val folder: File, private val fragmentsCodegenMode: FragmentsCodegenMode) {
  private class Measurement(
      val name: String,
      val fragmentsCodegenMode: FragmentsCodegenMode,
      val totalLineOfCode: Int,
      val codegenDuration: Duration,
      val compileDuration: Duration,
  )


  private class Options(
      val operationFiles: Set<File>,
      val outputDir: File,

      val incomingOptions: GraphQLCompiler.IncomingOptions,
      val moduleOptions: GraphQLCompiler.ModuleOptions,
  )


  @Test
  fun generateExpectedClasses() {
    val args = arguments(
        folder = folder,
        fragmentAsInterfaces = fragmentsCodegenMode == FragmentsCodegenMode.AsInterfaces,
    )
    generateExpectedClasses(args)
  }

  private fun generateExpectedClasses(options: Options) {
    options.outputDir.deleteRecursively()

    val codegenDuration = measureTime {
      GraphQLCompiler().write(
          operationFiles = options.operationFiles,
          outputDir = options.outputDir,
          incomingOptions = options.incomingOptions,
          moduleOptions = options.moduleOptions
      )
    }

    val expectedRoot = folder.parentFile.parentFile.parentFile
    val expectedRelativeRoot = folder.relativeTo(expectedRoot)

    val actualRoot = options.outputDir
    val actualFiles = actualRoot.walk().filter {
      // extension should always be the correct one, it's a bug else
      it.isFile && it.name != "metadata"
    }

    val expectedFiles = folder.resolve(fragmentsCodegenMode.name.decapitalize()).walk().filter { it.isFile && it.extension == "expected" }

    expectedFiles.forEach { expected ->
      val relativePath = expected.relativeTo(folder.resolve(fragmentsCodegenMode.name.decapitalize())).path.removeSuffix(".expected")

      val actual = actualRoot.resolve(expectedRelativeRoot).resolve(relativePath)
      if (!actual.exists()) {
        if (shouldUpdateTestFixtures()) {
          println("removing actual file: ${expected.absolutePath}")
          expected.delete()
          return@forEach
        } else {
          throw Exception("No actual file for ${actual.absolutePath}")
        }
      }

      // Do not generate a diff everytime the version changes
      actual.replaceVersionWithPlaceHolder()
      checkTestFixture(actual = actual, expected = expected)
    }

    actualFiles.forEach { actual ->
      val relativePath = actual.relativeTo(actualRoot).relativeTo(expectedRelativeRoot).path
      val expected = expectedRoot.resolve(expectedRelativeRoot).resolve(fragmentsCodegenMode.name.decapitalize()).resolve("$relativePath.expected")
      if (!expected.exists()) {
        if (shouldUpdateTestFixtures()) {
          println("adding expected file: ${actual.absolutePath} - ${actual.path}")
          expected.parentFile.mkdirs()
          actual.replaceVersionWithPlaceHolder()
          actual.copyTo(expected)
          return@forEach
        } else {
          throw Exception("No expected file at ${expected.absolutePath}")
        }
      }
      // no need to call checkTestFixture again, this has been taken care of
    }

    val totalLineOfCode = if (shouldUpdateMeasurements()) {
      expectedFiles.fold(0) { totalCount, file -> totalCount + file.readLines().size }
    } else -1

    // And that they compile
    val expectedWarnings = folder.name in listOf("deprecation", "custom_scalar_type_warnings", "arguments_complex", "arguments_simple")
    val compileDuration = measureTime {
      KotlinCompiler.assertCompiles(actualFiles.toSet(), !expectedWarnings)
    }

    measurements.add(
        Measurement(
            name = options.moduleOptions.moduleName,
            fragmentsCodegenMode = fragmentsCodegenMode,
            totalLineOfCode = totalLineOfCode,
            codegenDuration = codegenDuration,
            compileDuration = compileDuration,
        )
    )
  }

  private fun File.replaceVersionWithPlaceHolder() {
    writeText(
        readText().replace(
            "This class was automatically generated by Apollo GraphQL version '$VERSION'",
            "This class was automatically generated by Apollo GraphQL version '${'$'}VERSION'"
        )
    )
  }
  enum class FragmentsCodegenMode {
    AsInterfaces, AsClasses
  }

  companion object {
    private val measurements = mutableListOf<Measurement>()

    @AfterClass
    @JvmStatic
    fun dumpTimes() {
      if (shouldUpdateMeasurements()) {
        File("src/test/graphql/com/example/measurements").apply {
          writeText(
              String.format(
                  "%-70s %20s %20s %20s\n",
                  "Test:",
                  "Total LOC:",
                  "Codegen:",
                  "Compilation:",
              )
          )
          appendText(
              measurements.joinToString("\n") { measurement ->
                String.format(
                    "%-70s %20s %20s %20s",
                    "${measurement.name} (${measurement.fragmentsCodegenMode})",
                    measurement.totalLineOfCode.toString(),
                    measurement.codegenDuration.toString(),
                    measurement.compileDuration.toString(),
                )
              }
          )
        }
      }
    }

    private fun arguments(folder: File, fragmentAsInterfaces: Boolean): Options {
      val customScalarsMapping = if (folder.name in listOf(
              "custom_scalar_type",
              "input_object_type",
              "mutation_create_review")) {
        mapOf(
            "Date" to "java.util.Date",
            "URL" to "java.lang.String",
        )
      } else {
        emptyMap()
      }
      val useSemanticNaming = when (folder.name) {
        "hero_details_semantic_naming" -> true
        "mutation_create_review_semantic_naming" -> true
        else -> false
      }
      val generateAsInternal = when (folder.name) {
        "mutation_create_review", "simple_fragment" -> true
        else -> false
      }
      val operationIdGenerator = when (folder.name) {
        "operation_id_generator" -> object : OperationIdGenerator {
          override fun apply(operationDocument: String, operationName: String): String {
            return "hash"
          }

          override val version: String = "1"
        }
        else -> OperationIdGenerator.Sha256
      }

      val generateFragmentImplementations = when (folder.name) {
        "named_fragment_without_implementation" -> false
        else -> true
      }

      val schemaFile = folder.listFiles()!!.find { it.isFile && (it.name == "schema.sdl" || it.name == "schema.json" || it.name == "schema.graphqls") }
          ?: File("src/test/graphql/schema.sdl")

      val graphqlFiles = setOf(File(folder, "TestOperation.graphql"))
      val operationOutputGenerator = OperationOutputGenerator.Default(operationIdGenerator)

      val incomingOptions = GraphQLCompiler.IncomingOptions.from(
          roots = Roots(listOf(folder)),
          schemaFile = schemaFile,
          customScalarsMapping = customScalarsMapping,
          generateFragmentsAsInterfaces = fragmentAsInterfaces,
          rootPackageName = "com.example.${folder.name}",
          extraSchemaFiles = emptySet()
      )

      val moduleOptions = GraphQLCompiler.DefaultModuleOptions.copy(
          operationOutputGenerator = operationOutputGenerator,
          useSemanticNaming = useSemanticNaming,
          generateAsInternal = generateAsInternal,
          generateFilterNotNull = true,
          generateFragmentImplementations = generateFragmentImplementations,
          moduleName = folder.name,
          packageNameProvider = PackageNameProvider.Flat("com.example.${folder.name}")
      )

      return Options(
          operationFiles = graphqlFiles,
          outputDir = File("build/generated/test/${folder.name}"),
          incomingOptions = incomingOptions,
          moduleOptions = moduleOptions
      )
    }

    @JvmStatic
    @Parameterized.Parameters(name = "{0} ({1})")
    fun data(): Collection<*> {
      val fragmentsCodegenMode = System.getProperty("fragmentsCodegenMode")?.trim()?.let { kotlin.runCatching { FragmentsCodegenMode.valueOf(it) }.getOrNull() }
      return File("src/test/graphql/com/example/")
          .listFiles()!!
          .filter { it.isDirectory }
          .sortedBy { it.name }
          .filter { file ->
            TestUtils.testFilterMatches(file.absolutePath)
          }
          .flatMap { file ->
            val queryFile = checkNotNull(file.walk().find { it.extension == "graphql" })
            val hasNamedFragments = queryFile.readText().contains("fragment\\s\\w*\\son\\s\\w*".toRegex())
            val hasInlineFragments = queryFile.readText().contains("\\.\\.\\.\\s*on\\s\\w*\\s*".toRegex())
            if (false && (hasNamedFragments || hasInlineFragments)) {
              if (fragmentsCodegenMode == null) {
                listOf(
                    arrayOf(file, FragmentsCodegenMode.AsInterfaces),
                    arrayOf(file, FragmentsCodegenMode.AsClasses)
                )
              } else {
                listOf(
                    arrayOf(file, fragmentsCodegenMode)
                )
              }
            } else {
              listOf(
                  arrayOf(file, FragmentsCodegenMode.AsInterfaces),
              )
            }
          }
    }
  }
}