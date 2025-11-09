package three_tier_arch;

import java.net.*;
import java.nio.charset.StandardCharsets;

/** UDP target server – executes arithmetic operations requested by clients via proxy. */
public class UDPTargetServer {
    public static final int TARGET_PORT = 7001;
    private static final int BUF = 2048;

    public static void main(String[] args) {
        // Startup info
        System.out.println("[TARGET] UP on " + TARGET_PORT + " (ops: ADD, SUB, MUL, DIV)");

        // Shared input buffer
        byte[] buf = new byte[BUF];

        // Bind socket and start receive loop
        try (DatagramSocket socket = new DatagramSocket(TARGET_PORT)) {
            while (true) {
                // Receive request packet
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                socket.receive(dp);

                // Decode client message
                String msg = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();

                // Process and prepare response
                String resp = handle(msg);
                byte[] out = resp.getBytes(StandardCharsets.UTF_8);

                // Send response back to client (proxy)
                socket.send(new DatagramPacket(out, out.length, dp.getSocketAddress()));

                // Log request and response
                System.out.println("[TARGET] RX \"" + msg + "\" -> TX \"" + resp + "\" to " + dp.getSocketAddress());
            }
        } catch (Exception e) {
            // Global error handler
            System.err.println("[TARGET] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Handle arithmetic request. Format: REQ <OPERATION> <A> <B> */
    private static String handle(String msg) {
        try {
            // Normalize case (accept lowercase commands)
            msg = msg.trim().toUpperCase();

            // Validate message format
            if (!msg.startsWith("REQ ")) return "ERR BAD_REQUEST";
            String[] p = msg.trim().split("\\s+");
            if (p.length != 4) return "ERR BAD_REQUEST";

            // Parse operation and operands
            String op = p[1].toUpperCase();
            double a = Double.parseDouble(p[2]);
            double b = Double.parseDouble(p[3]);

            // Execute operation
            double res;
            switch (op) {
                case "ADD": res = a + b; break;
                case "SUB": res = a - b; break;
                case "MUL": res = a * b; break;
                case "DIV": res = (b == 0) ? Double.NaN : a / b; break;
                default: return "ERR BAD_OP";
            }

            // Return numeric result
            return "RES " + res;

        } catch (Exception ex) {
            // Handle invalid input
            return "ERR BAD_REQUEST";
        }
    }
}
