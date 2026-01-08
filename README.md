# hytale-stubs

A repository for generating Hytale stubs from the source server JAR.

## Generated Code

All generated methods throw `GeneratedStubException` to indicate they are stubs:

```java
public class Example {
    public String getValue() {
        throw new GeneratedStubException();
    }
}
```

## Planned Features

- **Documentation generation**: Load and apply Javadoc from JSON files to generated sources
- **Parameter names**: Extract actual parameter names from bytecode using ASM

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Contributing

Contributions are welcome. Please ensure all tests pass before submitting pull requests.
