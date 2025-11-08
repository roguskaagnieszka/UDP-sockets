package two_clients_chat;

import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class UDPChatClient {
    private static final int SERVER_PORT = 6666;
    private static final int BUF = 2048;

    public static void main(String[] args){
        String name = (args.length>0)?args[0]:"User";
        String host = (args.length>1)?args[1]:"localhost";
        try(DatagramSocket sock = new DatagramSocket()){
            InetAddress srv = InetAddress.getByName(host);
            System.out.println("Client started as \""+name+"\" -> "+host+":"+SERVER_PORT);

            // HELLO
            send(sock, srv, "HELLO "+name);

            // RX thread
            Thread rx = new Thread(() -> {
                byte[] b=new byte[BUF];
                DatagramPacket dp=new DatagramPacket(b,b.length);
                try{
                    while(true){
                        sock.receive(dp);
                        String msg=new String(dp.getData(),0,dp.getLength(),StandardCharsets.UTF_8).trim();
                        System.out.println("[SERVER] "+msg);
                        if (msg.startsWith("END ")) { System.out.println("Chat closed."); System.exit(0); }
                    }
                }catch(IOException ignored){}
            });
            rx.setDaemon(true); rx.start();

            System.out.println("Type messages. Use END to finish.");
            Scanner sc=new Scanner(System.in, StandardCharsets.UTF_8);
            while(true){
                String line=sc.nextLine();
                if(line==null) break;
                line=line.trim();
                if(line.isEmpty()) continue;
                send(sock, srv, line);
            }
        }catch(Exception e){ e.printStackTrace(); }
    }

    private static void send(DatagramSocket sock, InetAddress srv, String s) throws IOException{
        byte[] d=s.getBytes(StandardCharsets.UTF_8);
        sock.send(new DatagramPacket(d,d.length,srv,SERVER_PORT));
        System.out.println("[YOU] -> \""+s+"\"");
    }
}
