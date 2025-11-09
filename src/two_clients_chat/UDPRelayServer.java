package two_clients_chat;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** UDP relay server for two-user turn-based chat. */
public class UDPRelayServer {
    public static final int PORT = 6666;
    private static final int BUF = 2048;
    private static final long TIMEOUT_MS = 600_000; // 10 min idle timeout

    /** Basic client record (address, port, username). */
    private static class Client {
        final InetAddress addr; final int port; final String name;
        Client(InetAddress a,int p,String n){ addr=a; port=p; name=n; }

        // Check if packet source matches this client
        boolean same(SocketAddress sa){
            if(!(sa instanceof InetSocketAddress s)) return false;
            return Objects.equals(addr,s.getAddress()) && port==s.getPort();
        }

        // Return socket address of this client
        SocketAddress sa(){ return new InetSocketAddress(addr,port); }

        public String toString(){ return name+"@"+addr.getHostAddress()+":"+port; }
    }

    private DatagramSocket sock;
    private final byte[] buf = new byte[BUF];
    private Client c1=null,c2=null;
    private int turn=0;
    private long lastSeen1=0, lastSeen2=0;

    public static void main(String[] args){ new UDPRelayServer().run(); }

    /** Main loop – receives datagrams, classifies and dispatches them. */
    private void run(){
        try{
            // Start server socket
            sock = new DatagramSocket(PORT);
            System.out.println("[SERVER] Running on port "+PORT);

            // Graceful shutdown handler
            Runtime.getRuntime().addShutdownHook(new Thread(this::notifyShutdown));

            // Continuous receive loop
            while(true){
                // Receive incoming datagram
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                sock.receive(dp);

                // Extract sender and message
                InetSocketAddress from = (InetSocketAddress) dp.getSocketAddress();
                touch(from); // update last activity
                String msg = new String(dp.getData(),0,dp.getLength(),StandardCharsets.UTF_8).trim();
                System.out.println("[RX] "+from+" -> "+msg);

                // Remove idle clients and route messages
                reapStale();
                if (msg.startsWith("JOIN")) onJoin(from, msg);
                else if (msg.equalsIgnoreCase("END")) onEnd(from);
                else onChat(from, msg);
            }
        } catch(Exception e){ notifyShutdown(); }
    }

    /** Handle new connections and pairing logic. */
    private void onJoin(InetSocketAddress from,String msg) throws IOException{
        reapStale();

        // Reject if already joined or room full
        if (isPart(from)){ send(from,"INFO Already joined"); return; }
        if (c1!=null && c2!=null){ send(from,"FULL"); return; }

        // Extract and validate username
        String name = msg.length()>4 ? msg.substring(5).trim() : "";
        if (name.isEmpty()) name = (c1==null) ? "A" : "B";
        if ((c1!=null && c1.name.equalsIgnoreCase(name)) || (c2!=null && c2.name.equalsIgnoreCase(name))) {
            send(from, "ERROR NAME_TAKEN"); return;
        }

        // Register new client
        if (c1==null){
            c1=new Client(from.getAddress(),from.getPort(),name);
            lastSeen1 = System.currentTimeMillis();
            send(c1.sa(),"JOINED WAIT Waiting for partner...");
        } else {
            c2=new Client(from.getAddress(),from.getPort(),name);
            lastSeen2 = System.currentTimeMillis();
            startChat();
        }
    }

    /** Start new chat session between paired clients. */
    private void startChat() throws IOException{
        turn=0;
        send(c1.sa(),"PAIRED_WITH "+c2.name);
        send(c2.sa(),"PAIRED_WITH "+c1.name);
        send(c1.sa(),"YOUR_TURN");
        send(c2.sa(),"WAIT_FOR "+c1.name);
        System.out.println("[SERVER] Chat started: "+c1+" <-> "+c2);
    }

    /** Forward messages between clients with turn enforcement. */
    private void onChat(InetSocketAddress from,String text) throws IOException{
        // Wait for second participant if only one joined
        if (c1==null || c2==null){ send(from,"WAIT"); return; }

        // Determine active and passive client
        Client cur = (turn==0)?c1:c2, oth = (turn==0)?c2:c1;

        // Enforce turn-based sending
        if (!cur.same(from)){ send(from,"NOT_YOUR_TURN"); return; }

        // Relay message to other client
        send(oth.sa(),"MSG "+cur.name+": "+text);

        // Swap turn and notify both
        turn = 1 - turn;
        pushTurnHints();
    }

    /** Handle END command and keep remaining client waiting. */
    private void onEnd(InetSocketAddress from) throws IOException{
        Client me = who(from);
        if (me==null) { send(from,"INFO Not in chat"); return; }

        Client other = (me==c1)?c2:c1;
        send(me.sa(),"END");
        System.out.println("[SERVER] "+me.name+" left");

        // Promote remaining client or reset room
        if (other!=null) {
            promoteToC1(other);
            send(c1.sa(),"PEER_LEFT "+me.name);
            send(c1.sa(),"JOINED WAIT");
        } else reset();
    }

    /** Notify both clients about the next turn. */
    private void pushTurnHints() throws IOException {
        if (c1==null || c2==null) return;
        Client cur=(turn==0)?c1:c2, oth=(turn==0)?c2:c1;
        send(cur.sa(),"YOUR_TURN");
        send(oth.sa(),"WAIT_FOR "+cur.name);
    }

    /** Promote remaining client to c1 after peer disconnects. */
    private void promoteToC1(Client survivor) {
        if (survivor == null) { reset(); return; }

        if (c2 != null && c2 == survivor) {
            c1 = c2;
            lastSeen1 = lastSeen2;
        } else if (c1 != null && c1 == survivor) {
            c1 = survivor;
        }

        c2 = null;
        lastSeen2 = 0;
        turn = 0;
        System.out.println("[SERVER] Promoted "+c1+" to c1; waiting for new peer");
    }

    /** Reset chat room after both clients leave. */
    private void reset() {
        c1=null; c2=null; turn=0; lastSeen1=lastSeen2=0;
        System.out.println("[SERVER] Room reset");
    }

    // Check if sender is an active participant
    private boolean isPart(InetSocketAddress from) {
        return (c1!=null && c1.same(from)) || (c2!=null && c2.same(from));
    }

    // Identify which client sent the message
    private Client who(InetSocketAddress from) {
        if (c1!=null && c1.same(from)) return c1;
        if (c2!=null && c2.same(from)) return c2;
        return null;
    }

    /** Send UTF-8 datagram to client. */
    private void send(SocketAddress to,String s) throws IOException {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        sock.send(new DatagramPacket(data,data.length,to));
        System.out.println("[TX] "+s+" -> "+to);
    }

    /** Update last activity timestamps. */
    private void touch(InetSocketAddress from) {
        if (c1!=null && c1.same(from)) lastSeen1 = System.currentTimeMillis();
        if (c2!=null && c2.same(from)) lastSeen2 = System.currentTimeMillis();
    }

    /** Removes idle clients (handles both c1 and c2). */
    private void reapStale() throws IOException {
        long now = System.currentTimeMillis();

        checkTimeout(c1, now, true);
        checkTimeout(c2, now, false);
    }

    /** Check and remove client if inactive. */
    private void checkTimeout(Client client, long now, boolean first) throws IOException {
        if (client == null) return;
        long lastSeen = first ? lastSeen1 : lastSeen2;

        if (now - lastSeen <= TIMEOUT_MS) return;

        System.out.println("[SERVER] Timeout: " + client);

        Client other = first ? c2 : c1;

        if (other != null) {
            promoteToC1(other);
            send(c1.sa(), "PEER_LEFT " + client.name);
            send(c1.sa(), "JOINED WAIT");
        } else {
            reset();
        }
    }

    /** Notify clients on server shutdown and close socket. */
    private void notifyShutdown() {
        try {
            if (c1!=null) send(c1.sa(), "SERVER_SHUTDOWN");
            if (c2!=null) send(c2.sa(), "SERVER_SHUTDOWN");
        } catch (IOException ignored) {}
        finally {
            if (sock != null && !sock.isClosed()) sock.close();
            System.out.println("[SERVER] Shutdown complete");
        }
    }
}
