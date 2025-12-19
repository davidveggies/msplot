#!/bin/bash
# Script to install Cytoscape 3.10.4 dependencies from local installation
# This bypasses the SSL certificate issue with the remote repository

VERSION="3.10.4"
CYTOSCAPE_INSTALL="/Applications/Cytoscape_v3.10.4"
API_BUNDLE="$CYTOSCAPE_INSTALL/framework/system/org/cytoscape/api-bundle/$VERSION/api-bundle-$VERSION.jar"

if [ ! -f "$API_BUNDLE" ]; then
    echo "Error: Cytoscape installation not found at $CYTOSCAPE_INSTALL"
    echo "Please update CYTOSCAPE_INSTALL in this script to point to your installation"
    exit 1
fi

BASE_DIR="$HOME/.m2/repository/org/cytoscape"
ARTIFACTS="swing-application-api service-api model-api application-api work-api"

echo "Installing Cytoscape $VERSION API dependencies from local installation"
echo "Using API bundle: $API_BUNDLE"
echo ""

for artifact in $ARTIFACTS; do
    VERSION_DIR="$BASE_DIR/$artifact/$VERSION"
    mkdir -p "$VERSION_DIR"
    
    # Create minimal POM file
    POM_FILE="$VERSION_DIR/$artifact-$VERSION.pom"
    cat > "$POM_FILE" << EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <groupId>org.cytoscape</groupId>
    <artifactId>$artifact</artifactId>
    <version>$VERSION</version>
    <packaging>jar</packaging>
    <name>$artifact</name>
    <description>Cytoscape API - Provided by Cytoscape runtime</description>
</project>
EOF
    
    # Copy API bundle JAR (contains all APIs)
    JAR_FILE="$VERSION_DIR/$artifact-$VERSION.jar"
    cp "$API_BUNDLE" "$JAR_FILE"
    
    echo "  ✓ Installed $artifact:$VERSION"
done

echo ""
echo "Done! Dependencies installed to local Maven repository."
echo "You can now run: mvn clean compile"

