package com.nhnacademy;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

public class SimpleHttpServer implements Runnable {
    private final int port;
    private ServerSocket serverSocket;
    private static final String root = System.getProperty("user.dir");
    private static State state;
    private static long start, end;

    public SimpleHttpServer(int port) {
        this.port = port;
        try {
            serverSocket = new ServerSocket(this.port);
            new Thread(this).start();
            System.out.println("HTTP Server Start (port : " + this.port + ")");
            System.out.println();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                Socket client = serverSocket.accept();

                BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));
                start = System.currentTimeMillis();
                PrintWriter writer = new PrintWriter(client.getOutputStream());
                BufferedOutputStream binaryWriter = new BufferedOutputStream(client.getOutputStream());

                String line = br.readLine();

                StringTokenizer st;
                try {
                    st = new StringTokenizer(line);
                } catch (NullPointerException e) {
                    continue;
                }

                String method = st.nextToken();
                String request = st.nextToken();
                String contentType = "nope";
                int contentLength = 0;

                System.out.println("< 시간: " + new Date());
                System.out.println("< 메소드: " + method);
                System.out.println("< 경로: " + request);

                while ((line = br.readLine()) != null) {
                    if (line.startsWith("Content-Type")) {
                        contentType = line.split(":")[1].trim();
                    }

                    if (line.startsWith("Content-Length")) {
                        contentLength = Integer.parseInt(line.split(":")[1].trim());
                    }

                    if (line.equals("")) {
                        break;
                    }
                }

                ArrayList<String> requestBody = new ArrayList<>();
                StringBuilder fileContent = new StringBuilder();
                String fileName = null;

                if ("POST".equalsIgnoreCase(method) && contentLength > 0) {
                    String bodyLine = "";
                    for (int i = 0; i < contentLength; i++) {
                        char ch = (char) br.read();
                        if (ch != '\n') {
                            bodyLine += ch;
                        } else {
                            requestBody.add(bodyLine);
                            bodyLine = "";
                        }
                    }
                    for (int i = 0; i < requestBody.size(); i++) {
                        if (requestBody.get(i).startsWith("Content-Disposition")) {
                            String name = requestBody.get(i).split(" ")[2];
                            fileName = name.split("=")[1];
                        }
                        if (requestBody.get(i).startsWith("Content-Type")) {
                            for (int j = i + 2; j < requestBody.size() - 1; j++) {
                                if (j != requestBody.size() - 2) {
                                    fileContent.append(requestBody.get(j)).append("\n");
                                } else {
                                    fileContent.append(requestBody.get(j));
                                }
                            }
                            break;
                        }
                    }
                    request = request.substring(0, request.length() - 5);
                }

                if ("POST".equalsIgnoreCase(method) && !contentType.contains("multipart/form-data")) {
                    fileService(writer, binaryWriter, request, true, false);
                } else if ("POST".equalsIgnoreCase(method) && contentType.contains("multipart/form-data")) {
                    createFile(request, fileName, fileContent, writer, binaryWriter);
                } else if ("DELETE".equalsIgnoreCase(method)){
                    fileService(writer, binaryWriter, request, false, true);
                }else{
                    fileService(writer, binaryWriter, request, false, false);
                }

                br.close();
                writer.close();
                binaryWriter.close();

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    public static void fileService(PrintWriter writer, BufferedOutputStream binaryWriter,
                                   String request, boolean err405, boolean delete) {
        StringBuilder sb;
        byte[] content;

        if (err405) {
            state = State.ERR405;
            content = byteFile(fileStream("405Error.html"));
        } else if (delete) {
            File file = new File(root + request);
            if (file.exists()) {
                file.delete();
            }
            state = State.STATE204;
            content = byteFile(fileStream("State204.html"));
        } else {
            state = State.STATE200;
            if (request.endsWith("/")) {
                sb = makeHtml(request);
                content = byteFile(sb);
            } else {
                File file = new File(root + request);
                int fileLength = (int) file.length();

                FileInputStream fileStream;
                byte[] buffer = new byte[fileLength];
                try {
                    fileStream = new FileInputStream(file);
                    fileStream.read(buffer);
                    fileStream.close();
                } catch (IOException e) {
                    if (file.exists()) {
                        buffer = byteFile(forbidden());
                    } else {
                        buffer = byteFile(fileNotFound());
                    }
                }
                content = buffer;
            }
        }
        String stateMessage = setState();
        sendResponse(writer,stateMessage,content);

        try {
            binaryWriter.write(content);
            binaryWriter.flush();

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        end = System.currentTimeMillis();
        printResponse(stateMessage,content);
    }

    public static byte[] byteFile(StringBuilder sb) {
        ByteBuffer bb = Charset.forName("UTF-8").encode(sb.toString());

        int contentLength = bb.limit();
        byte[] content = new byte[contentLength];
        bb.get(content, 0, contentLength);

        return content;
    }

    public static StringBuilder fileStream(String path){
        Resource resource = null;
        try {
            resource = new ClassPathResource(path);
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(resource.getInputStream()))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                return builder;
            }
        } catch (IOException e) {
            if(resource.exists()){
                state = State.ERR403;
                return forbidden();
            }else {
                state = State.ERR404;
                return fileNotFound();
            }
        }
    }

    public static void createFile(String request, String fileName, StringBuilder fileContent,
                                PrintWriter writer, BufferedOutputStream binaryWriter) {
        state = State.STATE200;

        StringBuilder sb;
        byte[] content;

        fileName = fileName.substring(1, fileName.length() - 2);
        File file = new File(root + request+ "/" + fileName);

        try {
            if (file.createNewFile()) {

                FileWriter fileWriter = new FileWriter(root+request+"/"+fileName);
                BufferedWriter bw = new BufferedWriter(fileWriter);
                bw.write(fileContent.toString());
                bw.close();

                sb = makeHtml(request);
                content = byteFile(sb);
            } else {
                state = State.ERR409;
                content =byteFile(fileStream("409Error.html"));
            }
        } catch (IOException e) {
            content = byteFile(forbidden());
        }

        String stateMessage = setState();

        sendResponse(writer,stateMessage,content);

        try {
            binaryWriter.write(content);
            binaryWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        end = System.currentTimeMillis();
        printResponse(stateMessage,content);
    }

    public static void sendResponse(PrintWriter writer, String stateMessage, byte[] content){
        writer.println("HTTP/1.1 " + stateMessage);
        writer.println("Server: Java HTTP Server from taewon : 1.0");
        writer.println("Date: " + new Date());
        writer.println("Content-type: text/html");
        writer.println("Content-length: " + content.length);
        writer.println();
        writer.flush();
    }

    public static void printResponse(String stateMessage, byte[] content){
        System.out.println("> 응답 코드: " + stateMessage);
        System.out.println("> 응답 크기: " + content.length);
        System.out.println("> 응답에 걸린 시간: " + (end - start) + "ms");
        System.out.println();
    }

    public static StringBuilder makeHtml(String request) {
        StringBuilder sb = new StringBuilder();

        String s1 = "<!DOCTYPE html>";String s2 = "<html>";String s3 = "<head>";
        String s4 = "    <meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">";
        String s5 = "    <title>taewon SimpleHttpServer/</title>";
        String s6 = "</head>";String s7 = "<body>";String s8 = "<h1>Directory listing for /</h1>";
        String s9 = "<hr>";String s10 = "<ul>";
        sb.append(s1);sb.append(s2);sb.append(s3);sb.append(s4);sb.append(s5);
        sb.append(s6);sb.append(s7);sb.append(s8);sb.append(s9);sb.append(s10);

        String path = root+ request;
        File dir = new File(path);
        String[] dirList = dir.list();

        for (int i = 0; i < dirList.length; i++) {
            File directoryOrFile = new File(path + dirList[i]);
            String fileName = dirList[i];
            if (directoryOrFile.isDirectory()) {
                fileName = dirList[i] + "/";
            }
            sb.append("    <li><a href=" + fileName + ">" + fileName + "</a></li>");
        }
        String s11 = "</ul>";String s12 = "<hr>";String s13 = "</body>";String s14 = "</html>";
        sb.append(s11);sb.append(s12);sb.append(s13);sb.append(s14);

        return sb;
    }

    public static StringBuilder fileNotFound()  {
        return fileStream("404Error.html");
    }

    public static StringBuilder forbidden() {
        return fileStream("403Error.html");
    }

    public static String setState() {
        String stateMessage;
        switch (state) {
            case ERR403:
                stateMessage = "403 ERR";
                break;
            case ERR404:
                stateMessage = "404 ERR";
                break;
            case ERR405:
                stateMessage = "405 ERR";
                break;
            case ERR409:
                stateMessage = "409 ERR";
                break;
            case STATE204:
                stateMessage = "204 OK";
                break;
            default:
                stateMessage = "200 OK";
        }

        return stateMessage;
    }


    public static void main(String[] args) {
        new SimpleHttpServer(Integer.parseInt(args[0]));
    }
}
