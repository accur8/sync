# API Documentation Setup

Since the project uses a custom build configuration, here are simple ways to generate API documentation:

## Method 1: Using the Shell Script (Recommended)

```bash
./generate-docs.sh
```

This will:
1. Compile all modules
2. Generate ScalaDoc for each module
3. Create an index page linking all documentation
4. Output to `target/api-docs/`

## Method 2: Manual SBT Commands

Generate documentation for individual modules:

```bash
# For shared module
sbt "project shared" doc

# For logging module
sbt "project loggingJVM" doc

# For all modules
sbt ";project shared;doc;project loggingJVM;doc;project logging_logback;doc;project stager;doc"
```

Documentation will be in each module's `target/scala-3.7.1/api/` directory.

## Method 3: Using Standard ScalaDoc

For a specific module:
```bash
cd shared
sbt doc
```

## Viewing Documentation

### Local Web Server

After generating docs:

```bash
cd target/api-docs
python -m http.server 8000
# Or with Python 2
python -m SimpleHTTPServer 8000
```

Open http://localhost:8000 in your browser.

### Direct File Access

Open `target/api-docs/index.html` in your browser.

## Publishing to GitHub Pages

1. Generate documentation:
   ```bash
   ./generate-docs.sh
   ```

2. Create/checkout gh-pages branch:
   ```bash
   git checkout --orphan gh-pages
   git rm -rf .
   ```

3. Copy documentation:
   ```bash
   cp -r target/api-docs/* .
   git add .
   git commit -m "Update API documentation"
   git push origin gh-pages
   ```

4. Access at: `https://[your-org].github.io/sync/`

## Documentation Structure

```
target/api-docs/
├── index.html           # Main documentation index
├── shared/              # a8-sync-shared API docs
├── loggingJVM/          # a8-logging API docs
├── logging_logback/     # a8-logging-logback API docs
└── stager/              # ahs-stager API docs
```

## Writing Good ScalaDoc

When adding documentation to your code:

```scala
/**
 * Synchronizes data between source and target databases.
 * 
 * This class provides row-level synchronization with change detection,
 * supporting insert, update, and delete operations.
 * 
 * @param sourceConn Source database connection factory
 * @param targetConn Target database connection factory
 * @param config Configuration for sync behavior
 * @tparam T The type of records being synchronized
 * 
 * @example
 * {{{
 * val sync = RowSync[User](source, target, config)
 * val result = sync.sync()
 * println(s"Synced ${result.totalProcessed} records")
 * }}}
 * 
 * @since 1.0.0
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

### Documentation Not Generating

1. Ensure project compiles:
   ```bash
   sbt compile
   ```

2. Check Scala version compatibility:
   ```bash
   sbt "show scalaVersion"
   ```

3. Clear and retry:
   ```bash
   sbt clean
   ./generate-docs.sh
   ```

### Missing Modules

If a module's documentation is missing, check:
- Module has source files in `src/main/scala`
- Module compiles successfully
- Correct Scala version path in script

### Styling Issues

The generated ScalaDoc uses default styling. To customize:
1. Create custom CSS
2. Copy to documentation directory after generation
3. Modify generated HTML files to include custom CSS

## Future Improvements

For a more integrated solution, consider:
1. Adding sbt-unidoc plugin support to the build
2. Setting up automatic documentation deployment
3. Integrating with CI/CD pipeline
4. Adding search functionality