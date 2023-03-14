package nhn.edu.network;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.StringTokenizer;

public class ServerMain {
    public static void main(String[] args){
        try {
            String serverCommand = args[0];
            String serverCommand2 = args[1];
            Integer port = Integer.parseInt(args[2]);

            if(!serverCommand.equals("snc") || !serverCommand2.equals("-l")){
                throw new RuntimeException();
            }

            ServerSocket serverSocket = new ServerSocket(port);

            System.out.println("server start!!");
            Socket client = serverSocket.accept();
            Server server = new Server(client);
            Thread serverThread = new Thread(server);
            serverThread.start();

        } catch (IOException e) {

        }catch (RuntimeException e){
            System.out.println("명령어 입력 오류");
            System.exit(0);
        }
    }
}


class Server implements Runnable {
    private Socket clientSocket;
    BufferedReader br;
    BufferedWriter bw;
    BufferedReader serverInput;

    public Server(Socket client) {
        this.clientSocket = client;
    }

    @Override
    public void run() {
        try {
            br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            serverInput = new BufferedReader(new InputStreamReader(System.in));
            bw = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

            WriteThread wt = new WriteThread();
            Thread write = new Thread(wt);

            write.start();

            while (true) {
                String message = br.readLine();
                if(message==null){
                    System.exit(0);
                }
                System.out.println("클라이언트 : " + message);

            }

        } catch (IOException e) {

        }
    }

    class WriteThread implements Runnable {
        @Override
        public void run() {
            while (true) {
                try {
                    String message = serverInput.readLine();
                    bw.write(message + "\n");
                    bw.flush();
                    System.out.println("클라이언트에서 응답 : "+ message);

                } catch (Exception e) {

                }
            }
        }
    }
}