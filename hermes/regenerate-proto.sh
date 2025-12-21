#!/usr/bin/env bash
set -euo pipefail

# Script to regenerate ScalaPB code from proto files using scalapbc CLI
# Generated Scala files are committed to git in src/main/scala/

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GODEV_DIR="/Users/glen/code/accur8/godev"
PROTO_DIR="$SCRIPT_DIR/../hermes-proto/src/main/protobuf"
SCALA_OUT_DIR="$SCRIPT_DIR/../hermes-proto/src/main/scala"

echo "=== Copying proto files from godev ==="

# Create proto directory if it doesn't exist
mkdir -p "$PROTO_DIR"

# Copy proto files we need from godev
cp "$GODEV_DIR/nefario/rpc/nefario_rpc.proto" "$PROTO_DIR/"
cp "$GODEV_DIR/pkg/rpc/auth/auth.proto" "$PROTO_DIR/"
cp "$GODEV_DIR/pkg/rpc/mailbox/mailbox.proto" "$PROTO_DIR/"
cp "$GODEV_DIR/pkg/rpc/process/process_rpc.proto" "$PROTO_DIR/"
cp "$GODEV_DIR/pkg/rpc/db/db.proto" "$PROTO_DIR/"

echo "✓ Copied 5 proto files"

# Fix package declarations for Scala
echo ""
echo "=== Fixing proto package declarations ==="

for proto in "$PROTO_DIR"/*.proto; do
  filename=$(basename "$proto")
  echo "Processing $filename..."

  # Add Scala-specific options if not present
  if ! grep -q "option java_package" "$proto"; then
    case "$filename" in
      nefario_rpc.proto)
        package_line='option java_package = "a8.hermes.proto.nefario";'
        ;;
      auth.proto)
        package_line='option java_package = "a8.hermes.proto.auth";'
        ;;
      mailbox.proto)
        package_line='option java_package = "a8.hermes.proto.mailbox";'
        ;;
      process_rpc.proto)
        package_line='option java_package = "a8.hermes.proto.process";'
        ;;
      db.proto)
        package_line='option java_package = "a8.hermes.proto.db";'
        ;;
    esac

    # Insert after the package declaration
    sed -i '' "/^package /a\\
$package_line\\
option java_multiple_files = true;
" "$proto"
  fi
done

echo "✓ Fixed package declarations"

# Generate Scala code with scalapbc
echo ""
echo "=== Generating Scala code with scalapbc ==="

# Check if scalapbc is available
if ! command -v scalapbc &> /dev/null; then
  echo "ERROR: scalapbc not found in PATH"
  echo "Make sure you've run 'direnv allow' after adding protobuf fragment to flake.nix"
  exit 1
fi

echo "Using scalapbc: $(which scalapbc)"
echo "ScalaPB version: $(scalapbc --version)"

# Remove old generated code
echo "Removing old generated Scala files..."
rm -rf "$SCALA_OUT_DIR/a8/hermes/proto"
rm -rf "$SCALA_OUT_DIR/com/google/protobuf"

# Generate new code
# scalapbc options:
#   --scala_out=<dir>  - Output directory for generated Scala files
#   --proto_path=<dir> - Where to find proto files
#   --grpc=false       - Don't generate gRPC code (we use NATS)
echo "Generating Scala code..."

scalapbc \
  --scala_out="$SCALA_OUT_DIR" \
  --proto_path="$PROTO_DIR" \
  --grpc=false \
  "$PROTO_DIR"/*.proto

echo "✓ Generated Scala code"

# Count generated files
GENERATED_COUNT=$(find "$SCALA_OUT_DIR/a8/hermes/proto" -name "*.scala" 2>/dev/null | wc -l)
echo ""
echo "=== Summary ==="
echo "Proto files: $PROTO_DIR"
echo "Generated Scala: $SCALA_OUT_DIR"
echo "Generated files: $GENERATED_COUNT Scala files"
echo ""
echo "Generated files are in standard src/main/scala/ directory."
echo "Run 'git add hermes-proto/src/main/scala' to commit them."
