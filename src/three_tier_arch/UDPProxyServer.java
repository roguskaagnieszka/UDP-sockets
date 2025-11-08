package three_tier_arch;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

public class UDPProxyServer {
    public static final int PROXY_PORT = 7000;
    public static final String TARGET_HOST = "localhost";
    public static final int TARGET_PORT = three_tier_arch.UDPTargetServer.TARGET_PORT;

    private static final int BUF = 2048;
    private static final int TARGET_TIMEOUT_MS = 2000;

    public static void main(String[] args) {
        System.out.println("[PROXY] UP on " + PROXY_PORT + " -> target " + TARGET_HOST + ":" + TARGET_PORT);
        byte[] buf = new byte[BUF];

        try (DatagramSocket clientSock = new DatagramSocket(PROXY_PORT);
             DatagramSocket toTarget = new DatagramSocket()) {

            toTarget.setSoTimeout(TARGET_TIMEOUT_MS);

            while (true) {
                // Receive request from client
                DatagramPacket fromClient = new DatagramPacket(buf, buf.length);
                clientSock.receive(fromClient);
                String msg = new String(fromClient.getData(), 0, fromClient.getLength(), StandardCharsets.UTF_8).trim();
                SocketAddress clientAddr = fromClient.getSocketAddress();
                System.out.println("[PROXY] RX client " + clientAddr + " :: \"" + msg + "\"");

                // Handle local END command (do not forward)
                if (msg.equalsIgnoreCase("END")) {
                    String bye = "END Bye (client requested local termination)";
                    clientSock.send(new DatagramPacket(bye.getBytes(StandardCharsets.UTF_8), bye.length(), clientAddr));
                    System.out.println("[PROXY] TX client " + clientAddr + " :: \"" + bye + "\"");
                    continue;
                }

                String reply;
                try {
                    // Forward request to target server
                    byte[] out = msg.getBytes(StandardCharsets.UTF_8);
                    InetAddress targetAddr = InetAddress.getByName(TARGET_HOST);
                    DatagramPacket toT = new DatagramPacket(out, out.length, targetAddr, TARGET_PORT);

                    Instant t0 = Instant.now();
                    toTarget.send(toT);

                    // Wait for target response (with timeout)
                    byte[] buf2 = new byte[BUF];
                    DatagramPacket fromTarget = new DatagramPacket(buf2, buf2.length);
                    toTarget.receive(fromTarget);
                    long rttMs = Duration.between(t0, Instant.now()).toMillis();

                    String res = new String(fromTarget.getData(), 0, fromTarget.getLength(), StandardCharsets.UTF_8).trim();
                    // Process and modify response
                    if (res.startsWith("RES ")) {
                        reply = res + " | via-proxy rtt=" + rttMs + "ms";
                    } else {
                        reply = res + " | via-proxy";
                    }
                    System.out.println("[PROXY] RX target " + fromTarget.getSocketAddress() + " :: \"" + res + "\"");

                } catch (SocketTimeoutException ste) {
                    // Target not responding
                    reply = "ERR TARGET_DOWN";
                    System.out.println("[PROXY] ⚠ Target timeout — sending ERR TARGET_DOWN to client " + clientAddr);
                } catch (Exception ex) {
                    // Internal proxy error
                    reply = "ERR PROXY_INTERNAL";
                    System.out.println("[PROXY] ⚠ Exception -> " + ex.getMessage());
                }

                // Send final response back to client
                byte[] outReply = reply.getBytes(StandardCharsets.UTF_8);
                clientSock.send(new DatagramPacket(outReply, outReply.length, clientAddr));
                System.out.println("[PROXY] TX client " + clientAddr + " :: \"" + reply + "\"");
            }

        } catch (Exception e) {
            System.err.println("[PROXY] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
