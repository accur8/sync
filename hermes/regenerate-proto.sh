#!/usr/bin/env bash
set -euo pipefail

# Script to copy proto files from godev and regenerate ScalaPB code
# Generated Scala files are kept in git for transparency

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GODEV_DIR="/Users/glen/code/accur8/godev"
PROTO_DIR="$SCRIPT_DIR/src/main/protobuf"
SCALA_GEN_DIR="$SCRIPT_DIR/src/main/scala-gen"

echo "=== Copying proto files from godev ==="

# Create proto directory if it doesn't exist
mkdir -p "$PROTO_DIR"

# Copy proto files we need
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
    # Determine the appropriate java_package based on filename
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

echo ""
echo "=== Generating Scala code with ScalaPB ==="

# Remove old generated code
rm -rf "$SCALA_GEN_DIR"
mkdir -p "$SCALA_GEN_DIR"

# Temporarily enable PB.targets in build.sbt
BUILD_SBT="$SCRIPT_DIR/../build.sbt"
echo "Temporarily enabling PB.targets in build.sbt..."
sed -i '' 's|// Disable automatic proto generation - we use regenerate-proto.sh script instead|// PB.targets temporarily enabled by regenerate-proto.sh|' "$BUILD_SBT"
sed -i '' 's|// Compile / PB.targets|Compile / PB.targets|' "$BUILD_SBT"
sed -i '' 's|//   scalapb.gen|  scalapb.gen|' "$BUILD_SBT"
sed -i '' 's|// ),|),|' "$BUILD_SBT"

# Run sbt to generate code
cd "$SCRIPT_DIR/.."
sbt "hermes/compile"

# Restore build.sbt to original state
echo "Restoring build.sbt..."
sed -i '' 's|// PB.targets temporarily enabled by regenerate-proto.sh|// Disable automatic proto generation - we use regenerate-proto.sh script instead|' "$BUILD_SBT"
sed -i '' 's|^      Compile / PB.targets|      // Compile / PB.targets|' "$BUILD_SBT"
sed -i '' 's|^        scalapb.gen|      //   scalapb.gen|' "$BUILD_SBT"
sed -i '' 's|^      ),|      // ),|' "$BUILD_SBT"

# Move generated code from target to src
GENERATED_DIR="$SCRIPT_DIR/target/scala-3.7.3/src_managed/main/scalapb"
if [ -d "$GENERATED_DIR" ]; then
  cp -r "$GENERATED_DIR"/* "$SCALA_GEN_DIR/"
  echo "✓ Moved generated code to src/main/scala-gen/"
else
  echo "ERROR: Generated code not found at $GENERATED_DIR"
  exit 1
fi

echo ""
echo "=== Summary ==="
echo "Proto files: $PROTO_DIR"
echo "Generated Scala: $SCALA_GEN_DIR"
echo ""
echo "Generated files are now in git-tracked directories."
echo "Run 'git add hermes/src/main/scala-gen' to commit them."
