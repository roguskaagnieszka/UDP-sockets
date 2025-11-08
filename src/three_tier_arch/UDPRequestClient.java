package three_tier_arch;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UDPRequestClient {
    public static final String PROXY_HOST = "localhost";
    public static final int PROXY_PORT = three_tier_arch.UDPProxyServer.PROXY_PORT;

    private static final int BUF = 2048;

    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : PROXY_HOST;
        System.out.println("[CLIENT] Connecting to proxy " + host + ":" + PROXY_PORT);

        System.out.println("""
            ------------------------------------------------------------
            Available operations:
              • ADD – addition of two numbers
              • SUB – subtraction
              • MUL – multiplication
              • DIV – division
           
            Type commands in the format:  REQ <OPERATION> <A> <B>
            Type END to close the client.
            ------------------------------------------------------------
            """);

        try (DatagramSocket sock = new DatagramSocket()) {
            InetAddress proxy = InetAddress.getByName(host);
            Scanner sc = new Scanner(System.in, StandardCharsets.UTF_8);
            byte[] buf = new byte[BUF];

            while (true) {
                System.out.print("> ");
                String line = sc.nextLine();
                if (line == null) break;

                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("END")) {
                    System.out.println("[CLIENT] Bye.");
                    break;
                }

                // Send request to the PROXY
                byte[] out = line.getBytes(StandardCharsets.UTF_8);
                sock.send(new DatagramPacket(out, out.length, proxy, PROXY_PORT));

                // Wait for the response from the PROXY
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                sock.receive(dp);

                // Decode and display the received message
                String resp = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();
                System.out.println("[CLIENT] " + resp);
            }
        } catch (Exception e) {
            System.err.println("[CLIENT] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
