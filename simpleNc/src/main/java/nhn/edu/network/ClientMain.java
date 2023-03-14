package nhn.edu.network;

import java.io.*;
import java.net.Socket;
import java.util.StringTokenizer;

public class ClientMain {
    public static void main(String[] args) throws IOException {
        try {
            String clientCommand = args[0];
            String connectIp = args[1];
            int clientPort = Integer.parseInt(args[2]);

            if (!clientCommand.equals("scrul")) {
                throw new RuntimeException();
            }

            Client client = new Client(connectIp, clientPort);

            Thread clientThread = new Thread(client);
            clientThread.start();

        }catch (RuntimeException e){
            System.out.println("명령어 입력 오류");
        }

    }
}

class Client implements Runnable {
    private Socket client = null;
    BufferedReader br;
    BufferedWriter bw;
    BufferedReader clientInput;
    String connectIp;
    int clientPort;

    public Client(String connectIp, int clientPort) {
        this.connectIp = connectIp;
        this.clientPort = clientPort;
    }

    @Override
    public void run() {
        try {
            client = new Socket(connectIp, clientPort);

            bw = new BufferedWriter(new OutputStreamWriter(client.getOutputStream()));

            clientInput = new BufferedReader(new InputStreamReader(System.in));

            br = new BufferedReader(new InputStreamReader(client.getInputStream()));

            ReadThread rt = new ReadThread();
            Thread read = new Thread(rt);
            read.start();


            while (true) {
                String message = clientInput.readLine();
                bw.write(message + "\n");
                bw.flush();
                System.out.println("서버에서 응답 : "+ message);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    class ReadThread implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    String msg = br.readLine();
                    if(msg==null){
                        System.exit(0);
                    }
                    System.out.println("서버 : " + msg);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
