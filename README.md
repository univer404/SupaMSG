# SupaMSG

A lightweight private-messaging plugin for Paper/Purpur servers.

## Features
- Private messages: `/msg`, `/message`, `/whisper`, `/tell`, `/dm`, and `/w`
- Replies: `/r` and `/reply`
- Ignore/block toggle: `/block`, `/ignore`, and `/unignore`
- Incoming private-message toggle: `/msgtoggle`
- Checks both players' ignore lists and the recipient's message preference
- Persists ignored players and message-toggle preferences in `plugins/SupaMSG/data.yml`
- RGB message colors, action bars, denial sound, and tab completion
- GitHub Actions build artifact on pushes, pull requests, and manual workflow runs

## Requirements
- Paper or Purpur 1.21.11
- Java 21

## Build
Run `mvn clean package`. The JAR is `target/SupaMSG-1.0.0.jar`.

## Install
1. Download the JAR from the GitHub Actions artifact or build it locally.
2. Put it in the server's `plugins` folder.
3. Restart the server.
4. The plugin creates `plugins/SupaMSG/data.yml` automatically.

Permission: `supamsg.use` (default: everyone).

Blocked-player lists and the private-message toggle persist across restarts. Reply targets are session-only and clear when a player joins or quits.