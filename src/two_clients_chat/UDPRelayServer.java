package two_clients_chat;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public class UDPRelayServer {
    public static final int PORT = 6666;
    private static final int BUF = 2048;

    private static class Client {
        final InetAddress addr; final int port; final String name;
        Client(InetAddress a,int p,String n){addr=a;port=p;name=n;}
        boolean same(SocketAddress sa){ if(!(sa instanceof InetSocketAddress s))return false;
            return Objects.equals(addr,s.getAddress()) && port==s.getPort(); }
        SocketAddress sa(){ return new InetSocketAddress(addr,port); }
        public String toString(){return name+"@"+addr.getHostAddress()+":"+port;}
    }

    private DatagramSocket sock;
    private final byte[] buf = new byte[BUF];
    private Client c1=null,c2=null;
    private int turn=0; // 0->c1, 1->c2

    public static void main(String[] args){ new UDPRelayServer().run(); }

    private void run(){
        try{
            sock = new DatagramSocket(PORT);
            System.out.println("[SERVER] UP on "+PORT);
            while(true){
                DatagramPacket dp = new DatagramPacket(buf, buf.length);
                sock.receive(dp);
                String msg = new String(dp.getData(),0,dp.getLength(),StandardCharsets.UTF_8).trim();
                InetSocketAddress from = (InetSocketAddress) dp.getSocketAddress();
                System.out.println("[SERVER] RX "+from+" :: \""+msg+"\"");
                if (msg.startsWith("HELLO"))      onHello(from, msg);
                else if (msg.equalsIgnoreCase("END")) onEnd(from);
                else                                  onChat(from, msg);
            }
        }catch(Exception e){ e.printStackTrace(); }
    }

    private void onHello(InetSocketAddress from,String msg) throws IOException{
        String name = msg.length()>5 ? msg.substring(6).trim() : "";
        if (name.isEmpty()) name = (c1==null) ? "A" : "B";
        if (isPart(from)){ send(from,"INFO already joined as "+who(from).name); return; }

        if (c1==null){
            c1=new Client(from.getAddress(),from.getPort(),name);
            System.out.println("[SERVER] JOIN client_1="+c1);
            send(from,"JOINED WAIT You are first. Waiting for second client...");
        } else if (c2==null){
            c2=new Client(from.getAddress(),from.getPort(),name);
            System.out.println("[SERVER] JOIN client_2="+c2);
            startChat();
        } else {
            send(from,"FULL Room full. Try later.");
            System.out.println("[SERVER] FULL refused "+from);
        }
    }

    private void startChat() throws IOException{
        turn=0;
        send(c1.sa(),"READY You start.");
        send(c2.sa(),"READY Wait for your turn.");
        pushTurnHints();
        System.out.println("[SERVER] START "+c1+" vs "+c2);
    }

    private void onChat(InetSocketAddress from,String text) throws IOException{
        if (c1==null || c2==null){ send(from,"WAIT Need two clients. Send HELLO <name>."); return; }
        Client cur = (turn==0)?c1:c2;
        Client oth = (turn==0)?c2:c1;
        if (!cur.same(from)){ send(from,"NOT_YOUR_TURN Now "+cur.name+" types."); return; }
        send(oth.sa(),"MSG "+cur.name+": "+text);
        turn = 1-turn;
        pushTurnHints();
    }

    private void onEnd(InetSocketAddress from) throws IOException{
        Client me = who(from);
        if (me==null) { send(from,"INFO not in room."); return; }
        Client other = (me==c1)?c2:c1;
        if (other!=null) send(other.sa(),"END "+me.name+" ended the chat.");
        send(me.sa(),"END Bye.");
        System.out.println("[SERVER] END by "+me);
        reset();
    }

    private void pushTurnHints() throws IOException{
        if (c1==null || c2==null) return;
        Client cur=(turn==0)?c1:c2, oth=(turn==0)?c2:c1;
        send(cur.sa(),"YOUR_TURN");
        send(oth.sa(),"PEER_TURN "+cur.name);
        System.out.println("[SERVER] TURN -> "+cur.name);
    }

    private void reset(){ c1=null; c2=null; turn=0; System.out.println("[SERVER] ROOM RESET"); }

    private boolean isPart(InetSocketAddress from){
        return (c1!=null && c1.same(from)) || (c2!=null && c2.same(from));
    }
    private Client who(InetSocketAddress from){
        if (c1!=null && c1.same(from)) return c1;
        if (c2!=null && c2.same(from)) return c2;
        return null;
    }
    private void send(SocketAddress to,String s) throws IOException{
        byte[] data = s.getBytes(StandardCharsets.UTF_8);
        sock.send(new DatagramPacket(data,data.length,to));
        System.out.println("[SERVER] TX "+to+" :: \""+s+"\"");
    }
}
