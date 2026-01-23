# hytale-stubs

> [!WARNING]
> **You should just use Hytale's official releases instead.**
> 
> This project generated stubs from Hytale's server JAR before Hytale released it on a Maven repository. Now that it's on their Maven repository, use that instead:
> 
> **Find the latest version:**
> - Release: https://maven.hytale.com/release/com/hypixel/hytale/Server/maven-metadata.xml
> - Pre-Release: https://maven.hytale.com/pre-release/com/hypixel/hytale/Server/maven-metadata.xml
> 
> ### Maven
> ```xml
> <repositories>
>     <!-- Hytale release repository -->
>     <repository>
>         <id>hytale-release</id>
>         <url>https://maven.hytale.com/release</url>
>     </repository>
>     <!-- Hytale pre-release repository -->
>     <repository>
>         <id>hytale-pre-release</id>
>         <url>https://maven.hytale.com/pre-release</url>
>     </repository>
> </repositories>
> 
> <dependency>
>     <groupId>com.hypixel.hytale</groupId>
>     <artifactId>Server</artifactId>
>     <!-- Replace with latest version, we provide jars for the last five releases -->
>     <version>2026.01.22-6f8bdbdc4</version>
> </dependency>
> ```
> 
> ### Gradle (Kotlin DSL)
> ```kotlin
> repositories {
>     maven {
>         name = "hytale-release"
>         url = uri("https://maven.hytale.com/release")
>     }
>     maven {
>         name = "hytale-pre-release"
>         url = uri("https://maven.hytale.com/pre-release")
>     }
> }
> 
> dependencies {
>     implementation("com.hypixel.hytale:Server:<version>")
> }
> ```
> 
> ### Gradle (Groovy DSL)
> ```groovy
> repositories {
>     maven {
>         name 'hytale-release'
>         url 'https://maven.hytale.com/release'
>     }
>     maven {
>         name 'hytale-pre-release'
>         url 'https://maven.hytale.com/pre-release'
>     }
> }
> 
> dependencies {
>     implementation 'com.hypixel.hytale:Server:<version>'
> }
> ```

A repository for generating Hytale stubs from the source server JAR.

## Generated Code

All generated methods throw `GeneratedStubException` to indicate they are stubs:

## Planned Features

- **Documentation generation**: Load and apply Javadoc from JSON files to generated sources
- **Parameter names**: Extract actual parameter names from bytecode using ASM

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome. Please ensure all tests pass before submitting pull requests.
