# Accur8 Sync Documentation

This directory contains the source files for the Accur8 Sync documentation site.

## Building Documentation

### Prerequisites

- SBT 1.x
- JDK 11 or higher

### Generate Documentation

```bash
# Generate all documentation
sbt generateDocs

# Generate only API docs
sbt unidoc

# Generate only mdoc site
sbt mdoc

# Build the complete site
sbt makeSite
```

### Local Preview

After generating the documentation:

```bash
# Start a local web server
cd target/site
python -m http.server 8000
# Or with Python 2
python -m SimpleHTTPServer 8000
```

Then open http://localhost:8000 in your browser.

## Documentation Structure

```
docs/
├── index.md              # Home page
├── getting-started.md    # Quick start guide
├── architecture.md       # System architecture
├── database-configuration.md  # Database setup
├── examples.md          # Code examples
├── logging-guide.md     # Logging configuration
└── README.md           # This file
```

## Writing Documentation

### Mdoc

Documentation uses [mdoc](https://scalameta.org/mdoc/) for type-checked code examples:

- `scala mdoc` - Compiled and executed
- `scala mdoc:silent` - Compiled but output suppressed
- `scala mdoc:compile-only` - Only compiled, not executed
- `scala mdoc:fail` - Expected to fail compilation

### Variables

Available variables in mdoc:
- `@VERSION@` - Current project version
- `@SCALA_VERSION@` - Scala version

### Adding New Pages

1. Create a new `.md` file in the `docs/` directory
2. Add front matter:
   ```yaml
   ---
   layout: page
   title: Your Page Title
   ---
   ```
3. Write your content using Markdown
4. Add navigation links in `index.md` or related pages

## Publishing

Documentation is automatically published to GitHub Pages when:
- Changes are pushed to the main branch
- The GitHub Actions workflow runs successfully

Manual publishing:
```bash
sbt ghpagesPushSite
```

## API Documentation

API documentation is generated from ScalaDoc comments using sbt-unidoc:

```scala
/**
 * Synchronizes data between source and target databases.
 * 
 * @param sourceConn Source database connection factory
 * @param targetConn Target database connection factory
 * @param config Synchronization configuration
 * @tparam T The type of records being synchronized
 */
class RowSync[T](
  sourceConn: ConnFactory,
  targetConn: ConnFactory,
  config: SyncConfig
) {
  // Implementation
}
```

## Troubleshooting

### Documentation Build Fails

1. Clean and rebuild:
   ```bash
   sbt clean
   sbt generateDocs
   ```

2. Check for syntax errors in markdown files
3. Ensure all code examples compile
4. Verify front matter is valid YAML

### GitHub Pages Not Updating

1. Check GitHub Actions workflow status
2. Ensure branch protection rules allow GitHub Pages
3. Verify repository settings have GitHub Pages enabled
4. Check the gh-pages branch exists

## Contributing

When contributing documentation:

1. Follow the existing style and structure
2. Test all code examples
3. Run `sbt generateDocs` locally before submitting
4. Update navigation if adding new pages
5. Keep examples practical and concise