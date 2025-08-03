#!/bin/bash
# Script to generate API documentation for Accur8 Sync

echo "=== Accur8 Sync Documentation Generator ==="
echo

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if sbt is available
if ! command -v sbt &> /dev/null; then
    echo -e "${RED}Error: sbt is not installed or not in PATH${NC}"
    exit 1
fi

# Create output directory
DOCS_DIR="target/api-docs"
mkdir -p "$DOCS_DIR"

echo -e "${YELLOW}Step 1: Compiling project...${NC}"
sbt compile

if [ $? -ne 0 ]; then
    echo -e "${RED}Compilation failed!${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Compilation successful${NC}"
echo

echo -e "${YELLOW}Step 2: Generating ScalaDoc...${NC}"

# Generate docs for each module
MODULES=("shared" "loggingJVM" "logging_logback" "stager")

for module in "${MODULES[@]}"; do
    echo -e "  Generating docs for ${module}..."
    sbt "project ${module}" doc
    
    if [ $? -eq 0 ]; then
        # Copy generated docs to unified location
        if [ "$module" = "loggingJVM" ] && [ -d "logging/jvm/target/scala-3.7.1/api" ]; then
            cp -r "logging/jvm/target/scala-3.7.1/api" "$DOCS_DIR/loggingJVM"
            echo -e "  ${GREEN}✓${NC} ${module} docs generated"
        elif [ -d "${module}/target/scala-3.7.1/api" ]; then
            cp -r "${module}/target/scala-3.7.1/api" "$DOCS_DIR/${module}"
            echo -e "  ${GREEN}✓${NC} ${module} docs generated"
        elif [ -d "${module}/target/scala-3.7.1/doc" ]; then
            cp -r "${module}/target/scala-3.7.1/doc" "$DOCS_DIR/${module}"
            echo -e "  ${GREEN}✓${NC} ${module} docs generated"
        fi
    else
        echo -e "  ${RED}✗${NC} Failed to generate docs for ${module}"
    fi
done

echo
echo -e "${YELLOW}Step 3: Copying documentation files...${NC}"

# Copy markdown documentation to the docs directory
if [ -f "HOW_TO_USE.md" ]; then
    cp HOW_TO_USE.md "$DOCS_DIR/"
    echo -e "  ${GREEN}✓${NC} Copied HOW_TO_USE.md"
fi

if [ -f "DATABASE_SETUP.md" ]; then
    cp DATABASE_SETUP.md "$DOCS_DIR/"
    echo -e "  ${GREEN}✓${NC} Copied DATABASE_SETUP.md"
fi

if [ -f "LOGGING_BEST_PRACTICES.md" ]; then
    cp LOGGING_BEST_PRACTICES.md "$DOCS_DIR/"
    echo -e "  ${GREEN}✓${NC} Copied LOGGING_BEST_PRACTICES.md"
fi

if [ -f "API_DOCUMENTATION.md" ]; then
    cp API_DOCUMENTATION.md "$DOCS_DIR/"
    echo -e "  ${GREEN}✓${NC} Copied API_DOCUMENTATION.md"
fi

# Copy examples directory
if [ -d "examples" ]; then
    cp -r examples "$DOCS_DIR/"
    echo -e "  ${GREEN}✓${NC} Copied examples directory"
fi

# Copy markdown viewer if it exists
if [ -f "md-viewer.html" ]; then
    cp md-viewer.html "$DOCS_DIR/"
    echo -e "  ${GREEN}✓${NC} Copied markdown viewer"
fi

# Copy code viewer if it exists
if [ -f "code-viewer.html" ]; then
    cp code-viewer.html "$DOCS_DIR/"
    echo -e "  ${GREEN}✓${NC} Copied code viewer"
fi

echo
echo -e "${YELLOW}Step 4: Creating index page...${NC}"

# Create a simple index.html
cat > "$DOCS_DIR/index.html" << 'EOF'
<!DOCTYPE html>
<html>
<head>
    <title>Accur8 Sync API Documentation</title>
    <style>
        body {
            font-family: Arial, sans-serif;
            margin: 40px;
            background-color: #f5f5f5;
        }
        h1 {
            color: #333;
        }
        .modules {
            background-color: white;
            padding: 20px;
            border-radius: 8px;
            box-shadow: 0 2px 4px rgba(0,0,0,0.1);
        }
        .module {
            margin: 10px 0;
            padding: 10px;
            border-left: 4px solid #007bff;
        }
        a {
            color: #007bff;
            text-decoration: none;
        }
        a:hover {
            text-decoration: underline;
        }
        .description {
            color: #666;
            font-size: 0.9em;
            margin-top: 5px;
        }
    </style>
</head>
<body>
    <h1>Accur8 Sync API Documentation</h1>
    
    <div class="modules">
        <h2>Modules</h2>
        
        <div class="module">
            <a href="shared/index.html">a8-sync-shared</a>
            <div class="description">Core utilities, JDBC functional layer, JSON processing</div>
        </div>
        
        <div class="module">
            <a href="loggingJVM/index.html">a8-logging</a>
            <div class="description">Cross-platform logging abstraction</div>
        </div>
        
        <div class="module">
            <a href="logging_logback/index.html">a8-logging-logback</a>
            <div class="description">Logback implementation for logging</div>
        </div>
        
        <div class="module">
            <a href="stager/index.html">ahs-stager</a>
            <div class="description">Business-specific data staging application</div>
        </div>
    </div>
    
    <div class="modules" style="margin-top: 20px;">
        <h2>Additional Documentation</h2>
        <ul>
            <li><a href="md-viewer.html?file=HOW_TO_USE.md">How to Use Guide</a></li>
            <li><a href="md-viewer.html?file=DATABASE_SETUP.md">Database Setup Guide</a></li>
            <li><a href="md-viewer.html?file=LOGGING_BEST_PRACTICES.md">Logging Best Practices</a></li>
            <li><a href="md-viewer.html?file=API_DOCUMENTATION.md">API Documentation Guide</a></li>
            <li><a href="examples/">Code Examples</a></li>
        </ul>
        <p style="font-size: 0.9em; color: #666; margin-top: 10px;">
            Note: You can also view the raw markdown files directly: 
            <a href="HOW_TO_USE.md">HOW_TO_USE.md</a>, 
            <a href="DATABASE_SETUP.md">DATABASE_SETUP.md</a>, 
            <a href="LOGGING_BEST_PRACTICES.md">LOGGING_BEST_PRACTICES.md</a>
        </p>
    </div>
    
    <p style="margin-top: 40px; color: #999; font-size: 0.8em;">
        Generated on $(date)
    </p>
</body>
</html>
EOF

echo -e "${GREEN}✓ Index page created${NC}"
echo

# Count what was generated
MODULE_COUNT=$(find "$DOCS_DIR" -maxdepth 1 -type d -name "*" | grep -v "^$DOCS_DIR$" | grep -v "/examples$" | wc -l | tr -d ' ')
DOC_COUNT=$(find "$DOCS_DIR" -maxdepth 1 -name "*.md" -type f | wc -l | tr -d ' ')
EXAMPLE_COUNT=0
if [ -d "$DOCS_DIR/examples" ]; then
    EXAMPLE_COUNT=$(find "$DOCS_DIR/examples" -name "*.scala" -type f | wc -l | tr -d ' ')
fi

echo -e "${GREEN}Documentation generated successfully!${NC}"
echo -e "  • ${MODULE_COUNT} module API docs"
echo -e "  • ${DOC_COUNT} documentation guides"
echo -e "  • ${EXAMPLE_COUNT} code examples"
echo
echo -e "View documentation at: ${DOCS_DIR}/index.html"
echo
echo "To view in browser:"
echo "  cd $DOCS_DIR && python -m http.server 8000"
echo "  Then open http://localhost:8000"