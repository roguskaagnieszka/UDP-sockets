package two_clients_chat;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class UDPRelayServer {
    public static final int PORT = 6666;
    private static final int BUF = 2048;
    private static final long TIMEOUT_MS = 600_000; // 10 minutes

    private static class Client {
        final InetAddress addr;
        final int port;
        final String name;

        Client(InetAddress a,int p,String n){ addr=a; port=p; name=n; }

        boolean same(SocketAddress sa){
            if(!(sa instanceof InetSocketAddress s))return false;
            return Objects.equals(addr,s.getAddress()) && port==s.getPort();
        }

        SocketAddress sa(){ return new InetSocketAddress(addr,port); }

        public String toString(){ return name+"@"+addr.getHostAddress()+":"+port; }
    }

    private DatagramSocket sock;
    private final byte[] buf = new byte[BUF];
    private Client c1=null,c2=null;
    private int turn=0;
    private long lastSeen1=0, lastSeen2=0;

    // Starts the UDP relay server
    public static void main(String[] args){
        new UDPRelayServer().run();
    }

    // Main loop – receives datagrams and delegates actions
    private void run(){
        try{
            sock = new DatagramSocket(PORT);
            System.out.println("[SERVER] UP on "+PORT);

            while(true){
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                sock.receive(dp);

                InetSocketAddress from = (InetSocketAddress) dp.getSocketAddress();
                touch(from); // update last active timestamp

                String msg = new String(dp.getData(),0,dp.getLength(),StandardCharsets.UTF_8).trim();
                System.out.println("[SERVER] RX "+from+" :: \""+msg+"\"");

                reapStale(); // check timeouts

                // Route message by command type
                if (msg.startsWith("JOIN"))             onJoin(from, msg);
                else if (msg.equalsIgnoreCase("END"))   onEnd(from);
                else                                    onChat(from, msg);
            }
        } catch(Exception e){ e.printStackTrace(); }
    }

    // Handles JOIN requests and user registration
    private void onJoin(InetSocketAddress from,String msg) throws IOException{
        reapStale();

        // Ignore duplicate JOINs from already registered client
        if (isPart(from)){
            send(from,"INFO Already joined as "+who(from).name);
            return;
        }

        // Room already full
        if (c1!=null && c2!=null){
            send(from,"FULL");
            return;
        }

        // Extract username
        String name = msg.length()>4 ? msg.substring(5).trim() : "";
        if (name.isEmpty()) name = (c1==null) ? "A" : "B";

        // Prevent duplicate usernames
        if ((c1!=null && c1.name.equalsIgnoreCase(name)) ||
                (c2!=null && c2.name.equalsIgnoreCase(name))) {
            send(from, "ERROR NAME_TAKEN");
            return;
        }

        // Register first or second client
        if (c1==null){
            c1=new Client(from.getAddress(),from.getPort(),name);
            lastSeen1 = System.currentTimeMillis();
            System.out.println("[SERVER] JOIN client_1="+c1);

            // Wait until second client joins
            if (c2==null) {
                send(c1.sa(),"JOINED WAIT You are first. Waiting for second client...");
            } else {
                startChat(); // immediately start if both present
            }
        } else {
            c2=new Client(from.getAddress(),from.getPort(),name);
            lastSeen2 = System.currentTimeMillis();
            System.out.println("[SERVER] JOIN client_2="+c2);
            startChat();
        }
    }

    // Initializes a chat session between two clients
    private void startChat() throws IOException{
        turn=0;
        send(c1.sa(),"PAIRED_WITH "+c2.name);
        send(c2.sa(),"PAIRED_WITH "+c1.name);
        send(c1.sa(),"YOUR_TURN");
        send(c2.sa(),"WAIT_FOR "+c1.name);
        System.out.println("[SERVER] START "+c1+" vs "+c2);
    }

    // Handles message exchange in turn-based mode
    private void onChat(InetSocketAddress from,String text) throws IOException{
        // Both clients must be connected
        if (c1==null || c2==null){
            send(from,"WAIT");
            return;
        }

        Client cur = (turn==0)?c1:c2;
        Client oth = (turn==0)?c2:c1;

        // Block sending if it's not user's turn
        if (!cur.same(from)){
            send(from,"NOT_YOUR_TURN");
            return;
        }

        // Relay message and switch turn
        send(oth.sa(),"MSG "+cur.name+": "+text);
        turn = 1 - turn;
        pushTurnHints();
    }

    // Handles END command – closes session or resets room
    private void onEnd(InetSocketAddress from) throws IOException{
        Client me = who(from);
        if (me==null) {
            send(from,"INFO Not in chat.");
            return;
        }
        Client other = (me==c1)?c2:c1;

        send(me.sa(),"END");
        System.out.println("[SERVER] END by "+me);

        // Notify remaining peer and keep room open
        if (other!=null) {
            send(other.sa(),"PEER_LEFT "+me.name);
            if (me==c1) { c1=null; lastSeen1=0; } else { c2=null; lastSeen2=0; }
            turn = 0;
            send(other.sa(),"JOINED WAIT");
        } else {
            reset(); // both disconnected
        }
    }

    // Notifies clients whose turn it is
    private void pushTurnHints() throws IOException {
        if (c1==null || c2==null) return;
        Client cur=(turn==0)?c1:c2, oth=(turn==0)?c2:c1;
        send(cur.sa(),"YOUR_TURN");
        send(oth.sa(),"WAIT_FOR "+cur.name);
        System.out.println("[SERVER] TURN -> "+cur.name);
    }

    // Clears all session data
    private void reset() {
        c1=null; c2=null; turn=0; lastSeen1=lastSeen2=0;
        System.out.println("[SERVER] ROOM RESET");
    }

    // Checks if client is part of current session
    private boolean isPart(InetSocketAddress from) {
        return (c1!=null && c1.same(from)) || (c2!=null && c2.same(from));
    }

    // Finds client by address
    private Client who(InetSocketAddress from) {
        if (c1!=null && c1.same(from)) return c1;
        if (c2!=null && c2.same(from)) return c2;
        return null;
    }

    // Sends message to specific socket
    private void send(SocketAddress to,String s) throws IOException {
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        sock.send(new DatagramPacket(data,data.length,to));
        System.out.println("[SERVER] TX "+to+" :: \""+s+"\"");
    }

    // Updates last active timestamp for timeout tracking
    private void touch(InetSocketAddress from) {
        if (c1!=null && c1.same(from)) { lastSeen1 = System.currentTimeMillis(); return; }
        if (c2!=null && c2.same(from)) { lastSeen2 = System.currentTimeMillis(); }
    }

    // Removes inactive clients (timeout)
    private void reapStale() throws IOException {
        long now = System.currentTimeMillis();

        // Timeout client 1
        if (c1!=null && now - lastSeen1 > TIMEOUT_MS) {
            Client stale = c1; c1=null; lastSeen1=0;
            System.out.println("[SERVER] TIMEOUT "+stale);
            if (c2!=null) {
                send(c2.sa(),"PEER_LEFT "+stale.name);
                send(c2.sa(),"JOINED WAIT");
                turn = 0;
            }
        }

        // Timeout client 2
        if (c2!=null && now - lastSeen2 > TIMEOUT_MS) {
            Client stale = c2; c2=null; lastSeen2=0;
            System.out.println("[SERVER] TIMEOUT "+stale);
            if (c1!=null) {
                send(c1.sa(),"PEER_LEFT "+stale.name);
                send(c1.sa(),"JOINED WAIT");
                turn = 0;
            }
        }
    }
}
