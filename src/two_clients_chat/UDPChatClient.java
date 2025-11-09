package two_clients_chat;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

/** Two-user UDP chat client – handles join, messaging, and turn control. */
public class UDPChatClient {
    private static final int SERVER_PORT = 6666;
    private static final int BUF = 2048;
    private static final int TIMEOUT_MS = 3000;

    public static void main(String[] args){
        // CLI arguments – username and optional server host
        String name = (args.length>0)?args[0]:"User";
        String host = (args.length>1)?args[1]:"localhost";

        // Init UDP socket, resolve server address, set timeout, and prepare input scanner
        try (DatagramSocket sock = new DatagramSocket()) {
            InetAddress srv = InetAddress.getByName(host);
            sock.setSoTimeout(TIMEOUT_MS);
            Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);

            System.out.println("Client started as \""+name+"\" -> "+host+":"+SERVER_PORT);

            // Handle Ctrl+C – send END to server before exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try { send(sock, srv, "END"); } catch (Exception ignored) {}
            }));

            boolean joined = false;

            while (!joined) {
                // Try to send join request and wait for server reply
                try {
                    send(sock, srv, "JOIN " + name);

                    // Wait for server response (handshake datagram packet)
                    DatagramPacket hdp = new DatagramPacket(new byte[BUF], BUF);
                    sock.receive(hdp);

                    // Decode and print server message (handshake message)
                    String hmsg = new String(hdp.getData(), 0, hdp.getLength(), StandardCharsets.UTF_8).trim();
                    System.out.println("[SERVER] " + hmsg);

                    // Handle name conflict – prompt user to choose another name
                    if (hmsg.startsWith("ERROR NAME_TAKEN")) {
                        System.out.print("Username taken. Choose another: ");
                        name = sc.nextLine().trim();
                        continue;
                    }

                    // Handle full room – terminate client
                    if (hmsg.startsWith("FULL")) {
                        System.out.println("Chat full. Try later.");
                        return;
                    }

                    joined = true;
                    sock.setSoTimeout(0);
                }
                // Handle timeout – no response from server within limit
                catch (SocketTimeoutException e) {
                    System.out.println("[ERROR] Server offline. Try later.");
                    return;
                }
            }

            final boolean[] canWrite = { false };
            final boolean[] promptShown = { false };

            // Listener thread – receives and processes server messages
            Thread rx = new Thread(() -> {
                try {
                    // Buffer for incoming datagrams
                    byte[] b = new byte[BUF];

                    // Incoming packet container
                    DatagramPacket dp = new DatagramPacket(b, b.length);

                    // Main receive loop
                    while (true) {
                        // Wait for message from server
                        sock.receive(dp);

                        // Decode server message
                        String msg = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();
                        System.out.println("[SERVER] " + msg);

                        // Handle server instructions and session control
                        if (msg.startsWith("YOUR_TURN")) {
                            // Enable sending – user can write now
                            canWrite[0] = true;
                            if (!promptShown[0]) {
                                System.out.println("Type message:");
                                promptShown[0] = true;
                            }
                        }
                        // Disable sending – waiting for peer's message
                        else if (msg.startsWith("WAIT_FOR") || msg.startsWith("JOINED WAIT")) {
                            canWrite[0] = false;
                        }
                        // Chat ended by user or peer
                        else if (msg.startsWith("END")) {
                            System.out.println("Chat closed.");
                            System.exit(0);
                        }
                        // Server shutdown – terminate client
                        else if (msg.startsWith("SERVER_SHUTDOWN")) {
                            System.out.println("Server stopped. Try later.");
                            System.exit(0);
                        }
                    }
                } catch (IOException e) {
                    System.out.println("Connection lost.");
                    System.exit(0);
                }
            });
            rx.setDaemon(true);
            rx.start();

            // Input loop – enforces turn-based messaging
            while (true) {
                String line = sc.nextLine().trim();
                if (line.isEmpty()) continue;
                if (!canWrite[0] && !line.equalsIgnoreCase("END")) {
                    System.out.println("(Wait for your turn)");
                    continue;
                }
                send(sock, srv, line);
            }

        } catch (Exception e) {
            System.out.println("[ERROR] Cannot connect to server.");
        }
    }

    /** Sends UTF-8 datagram and logs it locally. */
    private static void send(DatagramSocket sock, InetAddress srv, String s) throws IOException {
        byte[] d = s.getBytes(StandardCharsets.UTF_8);
        sock.send(new DatagramPacket(d, d.length, srv, SERVER_PORT));
        System.out.println("[YOU] " + s);
    }
}
