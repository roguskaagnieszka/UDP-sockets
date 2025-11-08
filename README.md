# 💡 UDP Socket

## 1️⃣ Base Echo

A simple **UDP Echo Server and Client**.  
The client sends a message, and the server responds with the same text.

#### 🧩 Compilation
```bash
javac -d out src/base_echo/*.java
```

### ▶️ Run
#### Terminal 1 – start the server
java -cp out base_echo.UDPServer

#### Terminal 2 – start the client
java -cp out base_echo.UDPClient

### 📝 Notes
The server prints received messages and sends them back to the client.
Useful for verifying basic UDP communication setup.

## 2️⃣ Two-Client Chat
A UDP relay chat where two clients communicate through a single server.
The first client waits for the second one to join.
Messages are exchanged in turns, and typing END closes the chat for both clients.

#### 🧩 Compilation
```bash
javac -d out src/two_clients_chat/*.java
```

### ▶️ Run
#### Terminal 1 – start the relay server
```bash
java -cp out two_clients_chat.UDPRelayServer
```

#### Terminal 2 – start the first client
```bash
java -cp out two_clients_chat.UDPChatClient Alice
```

#### Terminal 3 – start the second client
```bash
java -cp out two_clients_chat.UDPChatClient Bob
```

### 📝 Notes
- RX → message received
- TX → message sent
- Port: 6666

If you see ClassNotFoundException, check the package line and classpath (-cp out).

To rebuild everything cleanly:
```bash
rm -rf out && javac -d out src/two_clients_chat/*.java
```
After END, the server resets and is ready for new clients.

## 3️⃣ Three-Tier Architecture
A three-layer UDP system consisting of a Client, a Proxy Server, and a Target Server.
The client sends a request to the proxy, which forwards it to the target.
The target performs a math operation and returns the result.
If the target is down, the proxy notifies the client.

```bash
javac -d out src/three_tier_arch/*.java
```

### ▶️ Run
#### Terminal 1 – start the target server
Handles mathematical operations.
```bash
java -cp out three_tier_arch.UDPTargetServer
```

#### Terminal 2 – start the proxy server
Acts as a middle layer between client and target.
```bash
java -cp out three_tier_arch.UDPProxyServer
```

#### Terminal 3 – start the client
Sends math requests to the proxy.
```bash
java -cp out three_tier_arch.UDPRequestClient
```

### 🧮 Available operations
Command	Description	Example

| Command | Description                         | Example                 |
|----------|-------------------------------------|--------------------------|
| `ADD`    | Addition of two numbers             | `REQ ADD 2 3 → RES 5`   |
| `SUB`    | Subtraction (first minus second)    | `REQ SUB 5 2 → RES 3`   |
| `MUL`    | Multiplication                      | `REQ MUL 4 3 → RES 12`  |
| `DIV`    | Division (first divided by second)  | `REQ DIV 8 2 → RES 4`   |


### 📝 Notes
If the target server is not running, the proxy responds with ERR TARGET_DOWN.
The proxy appends via-proxy and round-trip time (rtt) to each successful response.
Type END in the client to close it gracefully.

Default ports:
- Proxy → 7000
- Target → 7001