# WebSocket Chat Rooms

An in-memory WebSocket Chat Rooms server built with `http4s` which uses the WebSocket protocol for persistent two-way 
communication. Users can join rooms and chat with text messages in real-time.

## Requirements

1. sbt
2. scala 3.3.5
3. JDK 23

## Run the chat server

```bash
scala wschat.jar
```

## Run Chat Clients

Go to `localhost:8080/chat.html`.

### Commands

```
Commands:
/help             - Show this text
/name <username>  - Register your name
/room             - Change to default/entry room
/room <room name> - Change to the specified room
/rooms            - List all rooms
/members          - List members in current room
```


Following the tutorial from [Rock the JVM - WebSockets in Scala](https://rockthejvm.com/articles/websockets-in-scala-part-1-http4s)