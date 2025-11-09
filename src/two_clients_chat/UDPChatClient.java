package two_clients_chat;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UDPChatClient {
    private static final int SERVER_PORT = 6666;
    private static final int BUF = 2048;

    // Entry point – handles connection, joining and user interaction
    public static void main(String[] args){
        String name = (args.length>0)?args[0]:"User";
        String host = (args.length>1)?args[1]:"localhost";

        try (DatagramSocket sock = new DatagramSocket()) {
            InetAddress srv = InetAddress.getByName(host);
            Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);

            System.out.println("Client started as \""+name+"\" -> "+host+":"+SERVER_PORT);

            // Try joining the chat until successful
            while (true) {
                send(sock, srv, "JOIN " + name);

                byte[] hb = new byte[BUF];
                DatagramPacket hdp = new DatagramPacket(hb, hb.length);
                sock.receive(hdp);
                String hmsg = new String(hdp.getData(), 0, hdp.getLength(), StandardCharsets.UTF_8).trim();
                System.out.println("[SERVER] " + hmsg);

                // Username already in use
                if (hmsg.startsWith("ERROR NAME_TAKEN")) {
                    System.out.print("Choose another username: ");
                    String newName = sc.nextLine();
                    if (newName != null && !newName.isBlank()) {
                        name = newName.trim();
                        continue;
                    }
                }

                // Room is full, cannot join
                if (hmsg.startsWith("FULL")) {
                    System.out.println("Chat is full. Try later.");
                    return;
                }

                // Accepted or waiting for another client
                break;
            }

            final boolean[] canWrite = { false };
            final boolean[] initialPromptPrinted = { false };

            // Thread responsible for receiving server messages
            Thread rx = new Thread(() -> {
                byte[] b = new byte[BUF];
                DatagramPacket dp = new DatagramPacket(b, b.length);
                try {
                    while (true) {
                        sock.receive(dp);
                        String msg = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();
                        System.out.println("[SERVER] " + msg);

                        // Client's turn to write
                        if (msg.startsWith("YOUR_TURN")) {
                            canWrite[0] = true;
                            if (!initialPromptPrinted[0]) {
                                System.out.println("Type message (END to quit):");
                                initialPromptPrinted[0] = true;
                            }
                        }
                        // Other client's turn or waiting for pairing
                        else if (msg.startsWith("WAIT_FOR") || msg.startsWith("JOINED WAIT")) {
                            canWrite[0] = false;
                        }
                        // Server ends the chat
                        else if (msg.startsWith("END")) {
                            System.out.println("Chat closed.");
                            System.exit(0);
                        }
                    }
                } catch (IOException ignored) {}
            });
            rx.setDaemon(true);
            rx.start();

            // Read user input and send when allowed
            while (true) {
                String line = sc.nextLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                // Enforce turn-based messaging (except END)
                if (!canWrite[0] && !line.equalsIgnoreCase("END")) {
                    System.out.println("(Wait for your turn)");
                    continue;
                }

                send(sock, srv, line);
            }

        } catch (Exception e) { e.printStackTrace(); }
    }

    // Sends a UTF-8 encoded message to the server
    private static void send(DatagramSocket sock, InetAddress srv, String s) throws IOException {
        byte[] d = s.getBytes(StandardCharsets.UTF_8);
        sock.send(new DatagramPacket(d, d.length, srv, SERVER_PORT));
        System.out.println("[YOU] -> \""+s+"\"");
    }
}
