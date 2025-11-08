package three_tier_arch;

import java.net.*;
import java.nio.charset.StandardCharsets;

public class UDPTargetServer {
    public static final int TARGET_PORT = 7001;
    private static final int BUF = 2048;

    public static void main(String[] args) {
        System.out.println("[TARGET] UP on " + TARGET_PORT + " (ops: ADD, SUB, MUL, DIV)");
        byte[] buf = new byte[BUF];

        try (DatagramSocket socket = new DatagramSocket(TARGET_PORT)) {
            while (true) {
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                socket.receive(dp);
                String msg = new String(dp.getData(), 0, dp.getLength(), StandardCharsets.UTF_8).trim();
                String resp = handle(msg);
                byte[] out = resp.getBytes(StandardCharsets.UTF_8);
                socket.send(new DatagramPacket(out, out.length, dp.getSocketAddress()));
                System.out.println("[TARGET] RX \"" + msg + "\" -> TX \"" + resp + "\" to " + dp.getSocketAddress());
            }
        } catch (Exception e) {
            System.err.println("[TARGET] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Message format: REQ <OPERATION> <A> <B>
    private static String handle(String msg) {
        try {
            if (!msg.startsWith("REQ ")) return "ERR BAD_REQUEST";
            String[] p = msg.trim().split("\\s+");
            if (p.length != 4) return "ERR BAD_REQUEST";
            String op = p[1].toUpperCase();
            double a = Double.parseDouble(p[2]);
            double b = Double.parseDouble(p[3]);

            double res;
            switch (op) {
                case "ADD": res = a + b; break;
                case "SUB": res = a - b; break;
                case "MUL": res = a * b; break;
                case "DIV": res = (b == 0) ? Double.NaN : a / b; break;
                default: return "ERR BAD_OP";
            }
            return "RES " + res;
        } catch (Exception ex) {
            return "ERR BAD_REQUEST";
        }
    }
}
