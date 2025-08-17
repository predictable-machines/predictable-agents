#!/bin/bash

# Generate Dokka documentation
echo "Generating documentation..."
./gradlew :agents:dokkaGeneratePublicationHtml

# Check if documentation was generated
if [ -d "agents/build/dokka/html" ]; then
    echo "Documentation generated successfully!"
    
    # Disable Kotlin Playground buttons
    ./disable-playground.sh
    
    echo "Opening documentation at http://localhost:8000"
    echo "Press Ctrl+C to stop the server"
    
    # Start a simple HTTP server
    cd agents/build/dokka/html
    python3 -m http.server 8000
else
    echo "Error: Documentation not found. Please check the build output."
    exit 1
fi