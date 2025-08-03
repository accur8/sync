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
        if [ -d "${module}/target/scala-3.7.1/api" ]; then
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
echo -e "${YELLOW}Step 3: Creating index page...${NC}"

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
            <li><a href="../../../HOW_TO_USE.md">How to Use Guide</a></li>
            <li><a href="../../../DATABASE_SETUP.md">Database Setup Guide</a></li>
            <li><a href="../../../LOGGING_BEST_PRACTICES.md">Logging Best Practices</a></li>
            <li><a href="../../../examples/">Code Examples</a></li>
        </ul>
    </div>
    
    <p style="margin-top: 40px; color: #999; font-size: 0.8em;">
        Generated on $(date)
    </p>
</body>
</html>
EOF

echo -e "${GREEN}✓ Index page created${NC}"
echo

echo -e "${GREEN}Documentation generated successfully!${NC}"
echo -e "View documentation at: ${DOCS_DIR}/index.html"
echo
echo "To view in browser:"
echo "  cd $DOCS_DIR && python -m http.server 8000"
echo "  Then open http://localhost:8000"