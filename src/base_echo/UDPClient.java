package base_echo;

import java.net.*;
import java.io.*;

public class UDPClient extends Thread {
	// Here are object properties
    private DatagramSocket mysocket;
    private InetAddress hostAddress;
    private byte[] inbuf = new byte[1000];
    private byte[] outbuf;
    private DatagramPacket dp = new DatagramPacket(inbuf, inbuf.length);
	
    public UDPClient() {
        try {
            mysocket = new DatagramSocket();
            hostAddress = InetAddress.getByName("localhost");
        } catch(UnknownHostException e) {
            System.err.println("Unable to locate this server!");
            System.exit(1);
        } catch(SocketException e) {
            System.err.println("Unable to open the socket");
            e.printStackTrace();
            System.exit(1);
        }
        System.out.println("The UDP client is up");
        String outMessage = "Hello from the client!";
        try {
            // Here we connect wioth the server
            mysocket.connect(hostAddress, base_echo.UDPServer.INPORT);
            if (mysocket.isConnected())
                System.out.println("Successfully connected to " + mysocket.getRemoteSocketAddress());
            outbuf = outMessage.getBytes();
            mysocket.send(new DatagramPacket(outbuf, outbuf.length, hostAddress, base_echo.UDPServer.INPORT));
            mysocket.receive(dp);
            String message = new String(dp.getData(), 0, dp.getLength());
            String rcvd = "Received from the address: " + dp.getAddress() + ", port: " + dp.getPort() + ": " + message;        		
            System.out.println(rcvd);
            mysocket.disconnect();
            mysocket.close();
	} catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
   
    public static void main(String[] args) {
			// Here we start the client - object constructor
            new UDPClient();
    }
}

