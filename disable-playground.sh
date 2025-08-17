#!/bin/bash

# Script to disable Kotlin Playground in Dokka documentation
# This is necessary because the playground doesn't have access to our library

DOKKA_OUTPUT="agents/build/dokka/html"

if [ -d "$DOKKA_OUTPUT" ]; then
    echo "Disabling Kotlin Playground buttons..."
    
    # Append CSS to hide playground elements
    cat >> "$DOKKA_OUTPUT/styles/style.css" << 'EOF'

/* Custom styles to hide Kotlin Playground run button */
.run-button {
    display: none !important;
}

.playground-controls {
    display: none !important;
}

/* Hide the "Open in Playground" link */
.playground-link,
a[href*="play.kotlinlang.org"],
a[href*="kotlin-playground"] {
    display: none !important;
}

/* Hide any element with playground in its class */
[class*="playground"] {
    display: none !important;
}

/* But keep the sample container visible */
.sample-container {
    display: block !important;
}

/* Hide the playground overlay and controls */
.CodeMirror-hints,
.CodeMirror-hint {
    display: none !important;
}

/* Make sure samples are read-only */
.sample-container .CodeMirror {
    pointer-events: none !important;
}

/* Keep copy button visible */
.sample-container .copy-button {
    display: block !important;
}
EOF
    
    echo "Playground buttons disabled successfully!"
else
    echo "Dokka output directory not found. Please run './gradlew :agents:dokkaGeneratePublicationHtml' first."
fi