# hytale-stubs - Agent Guide

This document provides guidance for AI agents working on the hytale-stubs project.

## Project Overview

**hytale-stubs** is a Gradle plugin and code generation tool that generates Java stub source files from compiled bytecode in JAR files. The project extracts class information from JARs using ClassGraph, processes documentation metadata from JSON files, and generates skeleton Java source files that preserve the API surface while replacing implementations with stub exceptions.

### Key Capabilities

- Extract class metadata from compiled JAR files
- Generate stub Java source files with preserved API signatures
- Support documentation metadata via JSON schema
- Integrate with Gradle build systems as a plugin
- Handle all Java type systems: classes, interfaces, enums, records, annotations

### Technology Stack

- **Language:** Kotlin (plugin/tooling), Java (generated stubs & test fixtures)
- **Build System:** Gradle 8.x with Kotlin DSL
- **Java Version:** JDK 21 (via jvmToolchain)
- **Key Libraries:**
  - ClassGraph: JAR scanning and bytecode analysis
  - JavaPoet (Palantir fork): Java code generation
  - Kotlinx Serialization: JSON processing
  - Kotest: Testing framework

## Project Structure

```
hytale-stubs/
├── buildSrc/                           # Gradle plugin implementation
│   ├── src/main/kotlin/io/github/hytalekt/stubs/
│   │   ├── HytaleStubsPlugin.kt       # Main Gradle plugin entry point
│   │   ├── DocModels.kt               # Documentation data models
│   │   ├── DocSourceDirectorySet.kt   # Custom Gradle SourceDirectorySet
│   │   ├── util/                      # Core utilities
│   │   │   ├── TypeUtil.kt           # Type name parsing & conversion
│   │   │   ├── ClassUtil.kt          # Class modifier utilities
│   │   │   └── MethodUtil.kt         # Method generation utilities
│   │   ├── spec/                      # TypeSpec builders (polymorphic)
│   │   │   ├── TypeSpecBuilder.kt    # Factory interface
│   │   │   ├── ClassTypeSpecBuilder.kt
│   │   │   ├── InterfaceTypeSpecBuilder.kt
│   │   │   ├── EnumTypeSpecBuilder.kt
│   │   │   ├── RecordTypeSpecBuilder.kt
│   │   │   └── AnnotationTypeSpecBuilder.kt
│   │   ├── task/
│   │   │   └── GenerateSourcesTask.kt # Main Gradle task
│   │   └── jar/
│   │       └── JarResult.kt           # JAR scanning results
│   └── src/test/                      # Test suite
│       ├── kotlin/                    # Unit tests (Kotest)
│       │   ├── TypeNameParserTest.kt
│       │   └── SourceBuilderTest.kt
│       └── java/io/github/hytalekt/stubs/suite/  # Test fixtures
│           ├── TestBasicClass.java
│           ├── TestGenericClass.java
│           ├── TestMethodVariations.java
│           └── [13 more test classes]
├── src/main/
│   ├── java/io/github/kytale/stubs/
│   │   └── GeneratedStubException.java  # Exception thrown by stubs
│   └── docs/                            # Documentation sources
│       └── com/hytale/api/Example.json
├── class-doc.schema.json                # JSON Schema for documentation
├── build.gradle.kts                     # Root project configuration
└── settings.gradle.kts
```

### Key Components

#### 1. Type System (`util/`)

**TypeUtil.kt** - Complex type name parsing and conversion:
- Parses primitive types, arrays, parameterized types, wildcards
- Handles both JVM descriptor format (`Ljava/lang/String;`) and readable format
- Supports deeply nested generics: `Map<String, List<? extends Number>>`
- Converts type names to JavaPoet `TypeName` objects

**ClassUtil.kt** - Class modifier handling:
- Converts Java reflection modifiers to JavaPoet modifiers
- Handles visibility (public, protected, private) and semantic modifiers (final, static, abstract)

**MethodUtil.kt** - Method and constructor generation:
- Builds `MethodSpec` objects from `ClassInfo` metadata
- Generates stub method bodies throwing `GeneratedStubException`
- Handles type parameters, annotations, exceptions
- Special handling for abstract, interface, and annotation methods

#### 2. TypeSpec Builders (`spec/`)

Polymorphic pattern for generating different Java type declarations:

- **TypeSpecBuilder.kt**: Factory interface with `companion object builder(ClassInfo)` that selects the appropriate builder
- **ClassTypeSpecBuilder**: Standard Java classes
- **InterfaceTypeSpecBuilder**: Interfaces
- **EnumTypeSpecBuilder**: Enums (includes enum constant generation)
- **RecordTypeSpecBuilder**: Java records (14+)
- **AnnotationTypeSpecBuilder**: Annotation types

#### 3. Documentation Models

**DocModels.kt** defines comprehensive documentation metadata:
- `ClassDoc`: Class-level documentation (description, authors, versions, deprecation)
- `MethodDoc`: Method documentation with params, returns, throws, version tracking
- `ConstructorDoc`: Constructor-specific documentation
- `FieldDoc`: Field documentation
- `EnumConstantDoc`: Per-constant enum documentation
- `ParamDoc`: Parameter documentation
- `ThrowsDoc`: Exception documentation

**Schema:** `class-doc.schema.json` defines the JSON structure for documentation files.

#### 4. Gradle Plugin

**HytaleStubsPlugin.kt**:
- Registers custom "docs" SourceDirectorySet for each source set
- Configures IDEA integration for documentation directories
- Filters JSON documentation files

**GenerateSourcesTask.kt**:
- Main Gradle task for stub generation
- Inputs: JAR file + documentation sources
- Output: Generated Java sources in `build/gen/sources`
- Uses ClassGraph to scan and extract metadata

## Current State and TODOs

### Implemented ✅

- ✅ Gradle plugin infrastructure with custom SourceDirectorySet
- ✅ Comprehensive type parsing with full generic support (tested with 40+ parametric test cases)
- ✅ Modifier handling and conversion utilities
- ✅ Complete documentation data models and JSON schema
- ✅ Polymorphic TypeSpecBuilder pattern
- ✅ All TypeSpecBuilder implementations (Class, Interface, Enum, Record, Annotation)
- ✅ Test infrastructure with 15+ test fixture classes and 90 passing tests
- ✅ Migration to Kotest testing framework with JUnit5 runner
- ✅ GenerateSourcesTask integration with spec builders
- ✅ Constructor generation for classes, enums, and records
- ✅ Field generation with proper modifiers and initializers
- ✅ Method generation with proper signatures, throws clauses, and stub bodies
- ✅ Generic type parameter support across all builders
- ✅ Inheritance and interface implementation
- ✅ Enum constant, constructor, and field generation
- ✅ Interface default methods with bodies
- ✅ Interface constant initializers
- ✅ Record canonical constructors and accessor methods
- ✅ Annotation default values and parameter values
- ✅ Full annotation parameter support across all builders

### Recently Fixed Issues

The following issues were previously documented as limitations but have been fixed:

1. **Throws Clauses** - Methods now correctly include `throws` declarations using `methodInfo.thrownExceptionNames`
2. **Enum Constructors/Fields** - Enums now include constructors and non-constant fields (enum constructor parameters are filtered to exclude synthetic name/ordinal)
3. **Record Constructors** - Records now generate canonical constructors from their component fields
4. **Record Accessors** - Record accessor methods (name(), age(), etc.) are now generated
5. **Annotation Default Values** - Annotation members now include default values using `classInfo.annotationDefaultParameterValues`
6. **Annotation Parameter Values** - Annotations now include their parameter values (e.g., `@Retention(RetentionPolicy.RUNTIME)`)
7. **Interface Field Initializers** - Interface constants now include their initializer values using `fieldInfo.constantInitializerValue`

### Future Work / Lower Priority

1. **Documentation integration**
   - Documentation models exist but aren't used in code generation
   - Need to load JSON docs from docSourceSet
   - Apply documentation to generated TypeSpecs as Javadoc via `.addJavadoc()`

2. **SourceBuilder.kt refactoring**
   - Legacy monolithic implementation in `SourceBuilder.kt`
   - Being replaced by TypeSpecBuilder pattern
   - Contains reference implementation logic that should be migrated

3. **Java 14+ Record keyword support**
   - JavaPoet 0.9.0 doesn't support the `record` keyword
   - Records are currently generated as `final class` with fields and constructor
   - Future JavaPoet versions may add native record support

## Writing Tests

### Testing Framework: Kotest

The project uses **Kotest** with the FunSpec style. Tests are located in `buildSrc/src/test/`.

### Test Structure

```kotlin
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.collections.shouldHaveSize

class MyTest : FunSpec({
    test("should do something") {
        val result = myFunction()
        result shouldBe expectedValue
    }

    context("when condition X") {
        test("should behave Y") {
            // test code
        }
    }
})
```

### Parametric Testing

Use `withData` for data-driven tests:

```kotlin
import io.kotest.datatest.withData

context("type parsing") {
    withData(
        "int" to Int::class.javaPrimitiveType,
        "boolean" to Boolean::class.javaPrimitiveType,
        "java.lang.String" to String::class.java,
    ) { (input, expected) ->
        parseTypeName(input) shouldBe TypeName.get(expected)
    }
}
```

### Available Test Fixtures

15+ Java test classes in `buildSrc/src/test/java/io/github/hytalekt/stubs/suite/`:

- **TestBasicClass.java**: Multiple fields with different visibility, constructors, methods
- **TestGenericClass.java**: Type parameters with bounds, generic methods
- **TestComplexGenerics.java**: Multi-level type bounds, wildcard types
- **TestMethodVariations.java**: Various method modifiers, multiple exceptions, generic returns
- **TestFieldVariations.java**: Different field visibility and modifiers
- **TestAbstractClass.java**: Abstract classes with abstract methods
- **TestInterface.java**: Basic interface with methods
- **TestInterfaceWithGenerics.java**: Generic interfaces
- **TestEnum.java**: Enum with constants and methods
- **TestRecord.java**: Java record types
- **TestAnnotation.java**: Custom annotation types
- **TestAnnotatedClass.java**: Classes with annotations
- **TestInnerClasses.java**: Nested and inner classes
- **TestExtendingClass.java**: Class inheritance
- **TestMultipleInheritance.java**: Multiple interface implementation

### Writing New Tests

1. **Unit tests** for utilities:
   ```kotlin
   class MyUtilTest : FunSpec({
       test("should parse complex type") {
           val result = TypeUtil.parseTypeName("Map<String, List<Integer>>")
           result shouldBeInstanceOf ParameterizedTypeName::class
       }
   })
   ```

2. **Integration tests** for builders:
   ```kotlin
   class ClassTypeSpecBuilderTest : FunSpec({
       test("should generate basic class") {
           val classInfo = // ... load from test fixture
           val builder = ClassTypeSpecBuilder(classInfo)
           val typeSpec = builder.build()

           typeSpec.name shouldBe "TestBasicClass"
           typeSpec.methodSpecs shouldHaveSize 3
       }
   })
   ```

3. **Add test fixtures** when testing new language features:
   - Create Java file in `buildSrc/src/test/java/io/github/hytalekt/stubs/suite/`
   - Name it `Test[Feature].java`
   - Include comprehensive examples of the feature
   - Reference it in integration tests

### Running Tests

```bash
# Run all tests
./gradlew test

# Run specific test class
./gradlew test --tests "TypeNameParserTest"

# Run tests in buildSrc only
./gradlew :buildSrc:test

# Run with verbose output
./gradlew test --info
```

### Test Coverage Guidelines

- **Unit tests** for all utility functions (TypeUtil, MethodUtil, ClassUtil)
- **Parametric tests** for parsing functions (leverage Kotest's withData)
- **Integration tests** for TypeSpecBuilder implementations
- **End-to-end tests** for GenerateSourcesTask (once implemented)
- Test edge cases: empty classes, complex generics, multiple inheritance, annotations

## Common Development Tasks

### Adding Support for a New Type Feature

1. Create or update test fixture in `buildSrc/src/test/java/.../suite/`
2. Add unit tests in Kotest for parsing the feature
3. Update relevant utility (TypeUtil, MethodUtil, ClassUtil)
4. Update or create TypeSpecBuilder implementation
5. Add integration test verifying end-to-end generation
6. Update this document with the new feature

### Implementing a TypeSpecBuilder

See `EnumTypeSpecBuilder.kt` for reference implementation:

```kotlin
class MyTypeSpecBuilder(private val classInfo: ClassInfo) : TypeSpecBuilder {
    override fun build(): TypeSpec {
        require(classInfo.meetsCondition()) {
            "Error message"
        }

        val typeSpec = TypeSpec.myTypeBuilder(classInfo.simpleName)

        // Add modifiers
        typeSpec.addModifiers(ClassUtil.convertModifiers(classInfo.modifiers))

        // Add members using utility functions
        classInfo.methodInfo.forEach { method ->
            typeSpec.addMethod(MethodUtil.buildMethod(method))
        }

        return typeSpec.build()
    }
}
```

### Integrating Documentation

1. Load JSON docs from `docSourceSet` input
2. Parse using Kotlinx Serialization and `ClassDoc` models
3. Match docs to ClassInfo by fully qualified name
4. Apply Javadoc to TypeSpec/MethodSpec using `.addJavadoc()`

Example:
```kotlin
val classDoc = loadDocumentation(classInfo.name)
typeSpec.addJavadoc(classDoc.description)

classDoc.methods.forEach { methodDoc ->
    val methodSpec = // find matching method
    methodSpec.addJavadoc(buildMethodJavadoc(methodDoc))
}
```

### Debugging Generated Code

1. Set breakpoints in TypeSpecBuilder implementations
2. Use `toString()` on TypeSpec to preview generated code
3. Write JavaFile to temporary directory for inspection:
   ```kotlin
   val javaFile = JavaFile.builder(packageName, typeSpec).build()
   javaFile.writeTo(File("/tmp/debug"))
   ```
4. Compare with test fixtures to verify correctness

### Working with ClassGraph

ClassGraph provides rich metadata from bytecode:

```kotlin
val classInfo: ClassInfo = // from scan
classInfo.isInterface       // true for interfaces
classInfo.isEnum           // true for enums
classInfo.isRecord         // true for records
classInfo.isAnnotation     // true for annotations
classInfo.modifiers        // int bitmask of modifiers
classInfo.methodInfo       // MethodInfoList
classInfo.fieldInfo        // FieldInfoList
classInfo.typeSignature    // generic type signature
classInfo.superclasses     // list of superclasses
classInfo.interfaces       // list of implemented interfaces
```

See ClassGraph documentation for full API.

## Architectural Patterns

### Polymorphic TypeSpec Building

The project uses a factory pattern for creating type-specific builders:

```kotlin
val builder = TypeSpecBuilder.builder(classInfo)  // Returns appropriate subclass
val typeSpec = builder.build()
```

This allows clean separation of concerns for each Java type while maintaining a uniform interface.

### Separation of Concerns

- **Parsing:** TypeUtil handles all type name parsing and conversion
- **Modifiers:** ClassUtil handles modifier conversion
- **Methods:** MethodUtil handles method/constructor generation
- **Types:** TypeSpecBuilder implementations assemble everything

### Gradle Integration

The plugin extends Gradle's source set model:

```kotlin
sourceSets {
    main {
        docs {  // Custom SourceDirectorySet
            srcDir("src/main/docs")
        }
    }
}
```

Documentation sources are treated as first-class build inputs alongside Java/Kotlin sources.

## Best Practices

1. **Read before modifying**: Always examine existing code and patterns before adding new code
2. **Test-driven**: Write tests before implementing features
3. **Use utilities**: Leverage TypeUtil, MethodUtil, ClassUtil rather than reimplementing
4. **Parametric tests**: Use Kotest's `withData` for testing multiple similar cases
5. **Validate inputs**: Use `require()` at the start of functions to validate preconditions
6. **Consistent naming**: Follow existing patterns (e.g., `Test*.java` for fixtures)
7. **Documentation**: Update this guide when adding significant features
8. **Type safety**: Leverage Kotlin's type system and JavaPoet's builder patterns

## Commit Message Guidelines

Follow these conventions for commit messages:

- **feat:** New feature or capability (e.g., `feat: nested inner classes`, `feat: gen test suite`)
- **fix:** Bug fixes (e.g., `fix: RuntimeException in MethodUtil`, `fix: enum constructor parameters`)
- **chore:** Build setup, dependencies, tooling (e.g., `chore: setup gradle`, `chore: migrate tests to kotest`)
- **refactor:** Code restructuring without behavior change (e.g., `refactor: extract type & method utils`)
- **docs:** Documentation updates (e.g., `docs: AGENTS.md`, `docs: README.md`)
- **test:** Test-only changes (e.g., `test: add inner class test fixtures`)

Examples:
```
feat: add documentation models & schema
fix: enum constructor synthetic parameters
chore: setup gradle build
refactor: extract type & method utils from SourceBuilder
docs: add comprehensive AGENTS.md guide
test: add type parser test cases
```

## Useful Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Run specific test
./gradlew test --tests "TypeNameParserTest"

# Generate sources (once task is implemented)
./gradlew generateSources

# Clean build outputs
./gradlew clean

# See all tasks
./gradlew tasks

# Build with info logging
./gradlew build --info

# Build with debug logging
./gradlew build --debug
```

## References

- **JavaPoet Documentation**: https://github.com/palantir/javapoet
- **ClassGraph Documentation**: https://github.com/classgraph/classgraph/wiki
- **Kotest Documentation**: https://kotest.io/docs/framework/framework.html
- **Gradle Plugin Development**: https://docs.gradle.org/current/userguide/custom_plugins.html

## Getting Started Checklist for Agents

When starting work on this project:

- [ ] Read this document thoroughly
- [ ] Examine the test suite to understand expected behavior
- [ ] Review TypeUtil.kt to understand type parsing
- [ ] Look at EnumTypeSpecBuilder.kt as a reference implementation
- [ ] Check GenerateSourcesTask.kt to understand the integration point
- [ ] Run tests to ensure working environment: `./gradlew test`
- [ ] Identify which TODO you're addressing (see TODOs section above)
- [ ] Write tests first for new functionality
- [ ] Follow existing code patterns and conventions

## Key Files Reference

| File | Purpose | Status |
|------|---------|--------|
| `HytaleStubsPlugin.kt` | Gradle plugin entry point | Complete |
| `GenerateSourcesTask.kt` | Main task for generation | Complete |
| `TypeUtil.kt` | Type parsing utilities | Complete |
| `MethodUtil.kt` | Method generation | Complete |
| `ClassUtil.kt` | Modifier utilities | Complete |
| `TypeSpecBuilder.kt` | Builder factory interface | Complete |
| `ClassTypeSpecBuilder.kt` | Class generation | Complete |
| `InterfaceTypeSpecBuilder.kt` | Interface generation | Complete |
| `EnumTypeSpecBuilder.kt` | Enum generation | Complete |
| `RecordTypeSpecBuilder.kt` | Record generation | Complete |
| `AnnotationTypeSpecBuilder.kt` | Annotation generation | Complete |
| `DocModels.kt` | Documentation models | Complete |
| `TypeNameParserTest.kt` | Type parsing tests | Complete |
| `TypeSpecBuilderTest.kt` | Builder integration tests | Complete |
| `KnownLimitationsTest.kt` | Feature verification tests | Complete |

## Questions?

When uncertain about implementation details:

1. Check existing test fixtures for examples
2. Review similar code in other TypeSpecBuilder implementations
3. Examine MethodUtil/TypeUtil for relevant utility functions
4. Look at JavaPoet documentation for API details
5. Test with simple cases first before handling complex scenarios
