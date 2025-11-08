import java.net.*;
import java.io.*;
import java.util.*;

public class UDPServer {
      
    static final int INPORT = 6666;
    private byte[] inbuf = new byte[1000];
    private byte[] outbuf;
    private DatagramPacket dp = new DatagramPacket(inbuf, inbuf.length);
    private DatagramSocket mysocket;
    
    public UDPServer() {
        try {
            mysocket = new DatagramSocket(INPORT);
            System.out.println("The server is up!");
            while(true) {
                mysocket.receive(dp);
				String message = new String(dp.getData(), 0, dp.getLength());
                String rcvd = message + ", from the host: " + dp.getAddress() +
                        ", port: " + dp.getPort();
                System.out.println(rcvd);
                String echoString = "Message received: " + rcvd;
				outbuf = echoString.getBytes();
                DatagramPacket echo = new DatagramPacket(outbuf, outbuf.length, dp.getAddress(), dp.getPort());
                mysocket.send(echo);
            }
        } catch(SocketException e) {
            System.err.println("Unable to open the socket!");
            System.exit(1);
        } catch(IOException e) {
            System.err.println("Communication error!");
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
		// Here we start the server - object constructor
        new UDPServer();
    }   
}