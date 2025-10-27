#!/bin/bash

# Portfolio Development Runner
# Starts backend and frontend in separate terminal windows

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

cleanup() {
    echo "Cleaning up existing services..."

    echo "  - Stopping all Docker containers..."
    CONTAINERS=$(docker ps -aq)
    if [ -n "$CONTAINERS" ]; then
        docker stop $CONTAINERS 2>/dev/null || true
    else
        echo "    No Docker containers running"
    fi

    echo "  - Killing processes on port 8081..."
    lsof -ti:8081 | xargs kill -9 2>/dev/null || echo "    No processes on port 8081"

    echo "  - Killing processes on port 61234..."
    lsof -ti:61234 | xargs kill -9 2>/dev/null || echo "    No processes on port 61234"

    echo "✓ Cleanup complete"
    echo ""
}

echo "Starting Portfolio application..."
echo "Project directory: $PROJECT_DIR"
echo ""

cleanup

# Detect OS
if [[ "$OSTYPE" == "darwin"* ]]; then
    # macOS - use osascript to open new Terminal tab

    # Start Frontend in new tab (run in background)
    osascript -e 'tell application "Terminal"
        tell application "System Events" to tell process "Terminal" to keystroke "t" using command down
        do script "cd '"'$PROJECT_DIR'"' && echo '\''=== Starting Frontend (Vite) ==='\'' && npm run dev" in front window
    end tell' &

    # Small delay to ensure tab opens
    sleep 0.5

    echo "✓ Opened new terminal tab for frontend"
    echo "  - Frontend: Vite on http://localhost:61234"
    echo ""
    echo "=== Starting Backend (Spring Boot) in current terminal ==="
    echo "  - Backend: Spring Boot on http://localhost:8081"
    echo ""

    # Export GRADLE_OPTS and start backend in current terminal (foreground)
    export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED"
    ./gradlew bootRun

elif [[ "$OSTYPE" == "linux-gnu"* ]]; then
    # Linux - try gnome-terminal, then xterm as fallback
    if command -v gnome-terminal &> /dev/null; then
        gnome-terminal --title="Frontend (Vite)" -- bash -c "cd '$PROJECT_DIR' && echo '=== Starting Frontend (Vite) ===' && npm run dev; exec bash" &
        echo "✓ Opened gnome-terminal tab for frontend"
    elif command -v xterm &> /dev/null; then
        xterm -T "Frontend (Vite)" -e "cd '$PROJECT_DIR' && echo '=== Starting Frontend (Vite) ===' && npm run dev; bash" &
        echo "✓ Opened xterm window for frontend"
    else
        echo "Error: No supported terminal emulator found (tried gnome-terminal, xterm)"
        exit 1
    fi

    sleep 0.5

    echo "  - Frontend: Vite on http://localhost:61234"
    echo ""
    echo "=== Starting Backend (Spring Boot) in current terminal ==="
    echo "  - Backend: Spring Boot on http://localhost:8081"
    echo ""

    export GRADLE_OPTS="--enable-native-access=ALL-UNNAMED"
    ./gradlew bootRun

elif [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
    # Windows Git Bash
    echo "Windows detected. Opening new window for frontend..."
    start cmd //c "cd /d $PROJECT_DIR && echo === Starting Frontend (Vite) === && npm run dev"

    sleep 0.5

    echo "✓ Opened command prompt window for frontend"
    echo "  - Frontend: Vite on http://localhost:61234"
    echo ""
    echo "=== Starting Backend (Spring Boot) in current terminal ==="
    echo "  - Backend: Spring Boot on http://localhost:8081"
    echo ""

    set GRADLE_OPTS=--enable-native-access=ALL-UNNAMED
    gradlew.bat bootRun

else
    echo "Error: Unsupported operating system: $OSTYPE"
    exit 1
fi
