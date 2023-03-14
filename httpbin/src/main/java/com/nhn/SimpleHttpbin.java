package com.nhn;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Date;
import java.util.StringTokenizer;

public class SimpleHttpbin implements Runnable {
    private ServerSocket serverSocket;
    private static final String root = System.getProperty("user.dir");
    private static State state;
    private static long start, end;
    private static ArrayList<String> requestHeader;
    private static String origin, hostName;

    public SimpleHttpbin() {
        try {
            serverSocket = new ServerSocket(80);
            new Thread(this).start();
            System.out.println("HTTP Server Start (port : " + 80 + ")");
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
                origin = client.getInetAddress().getHostAddress();

                BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));

                String line = br.readLine();
                start = System.currentTimeMillis();

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

                requestHeader = new ArrayList<>();

                System.out.println("< 시간: " + new Date());
                System.out.println("< 메소드: " + method);
                System.out.println("< 경로: " + request);

                while ((line = br.readLine()) != null) {
                    if (line.startsWith("Content-Type")) {
                        contentType = line.split(":")[1].trim();
                    } else if (line.startsWith("Content-Length")) {
                        contentLength = Integer.parseInt(line.split(":")[1].trim());
                    } else if (line.startsWith("Host")) {
                        hostName = line.split(":")[1].trim();
                    }
                    if (line.equals("")) {
                        break;
                    } else {
                        requestHeader.add(line);
                    }
                }

                ArrayList<String> requestBody = new ArrayList<>();

                if ("POST".equalsIgnoreCase(method) && contentLength > 0) {
                    String bodyLine = "";
                    for (int i = 0; i < contentLength; i++) {
                        char ch = (char) br.read();

                        if (ch!='\n') {
                            bodyLine += ch;
                        } else {
                            requestBody.add(bodyLine);
                            bodyLine = "";
                        }
                    }
                    if (!bodyLine.equals("")) {
                        requestBody.add(bodyLine);
                    }
                }

                if ("POST".equalsIgnoreCase(method) && contentType.contains("multipart/form-data")) {
                    state = State.STATE200;
                    createFile(client, request, requestBody);
                } else {
                    state = State.STATE200;
                    if ("POST".equalsIgnoreCase(method)) {
                        fileService(client, request, requestBody.get(0));
                    } else {
                        fileService(client, request, "");
                    }
                }

                br.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }


    public static void fileService(Socket client, String request, String requestBody) throws IOException {
        PrintWriter writer = new PrintWriter(client.getOutputStream());
        BufferedOutputStream binaryWriter = new BufferedOutputStream(client.getOutputStream());

        try {
            StringBuilder sb;

            if (request.equals("/ip")) {
                sb = new StringBuilder();
                sb.append("{\n");
                sb.append("  ").append("\"origin\": ").append("\"" + origin + "\"\n");
                sb.append("}");
            } else {
                sb = makeResponse(request, requestBody, "", new StringBuilder());  // responseBody 형식에 맞춰 제작
            }

            byte[] content = byteFile(sb);  // responseBody를 바이트로 변환
            String stateMessage = setState(); // 응답 상태코드 설정

            sendResponse(writer, stateMessage, content); // responseHeader 전송

            binaryWriter.write(content);
            binaryWriter.flush();  //responseBody 전송

            end = System.currentTimeMillis();
            printResponse(stateMessage, content);

            writer.close();
            binaryWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void createFile(Socket client, String request, ArrayList<String> requestBody) throws IOException {
        PrintWriter writer = new PrintWriter(client.getOutputStream());
        BufferedOutputStream binaryWriter = new BufferedOutputStream(client.getOutputStream());
        byte[] content;

        StringBuilder fileContent = new StringBuilder();
        String name = null;
        String fileName = null;

        for (int i = 0; i < requestBody.size(); i++) {
            if (requestBody.get(i).startsWith("Content-Disposition")) {
                String[] disposition = requestBody.get(i).split(";")[1].split(" ");
                for (int j = 0; j < disposition.length; j++) {
                    if (disposition[j].startsWith("name")) {
                        name = disposition[j].split("=")[1];
                    } else if (disposition[j].startsWith("filename")) {
                        fileName = disposition[j].split("=")[1];
                    }
                }
            }
            if (requestBody.get(i).startsWith("Content-Type")) {
                for (int j = i + 2; j < requestBody.size() - 1; j++) {
                    if (j != requestBody.size() - 2) {
                        fileContent.append(requestBody.get(j) + " ");
                        System.out.println(fileContent);
                    } else {
                        fileContent.append(requestBody.get(j));
                    }
                }
                request = request.substring(0, request.length() - 5);
                break;
            }
        }
        fileName = fileName.substring(0, fileName.length() - 2);
        File file = new File(root + request + "/" + fileName);
        try {
            if (file.createNewFile()) {
                FileWriter fileWriter = new FileWriter(root + request + "/" + fileName);
                BufferedWriter bw = new BufferedWriter(fileWriter);
                bw.write(fileContent.toString());
                bw.close();

                content = byteFile(makeResponse(request, "", name, fileContent));
            } else {
                state = State.ERR409;
                StringBuilder sb = new StringBuilder();
                sb.append("{").append("\n");
                sb.append("  \"Error code\": \"409\"\n");
                sb.append("  \"Message\": \"Conflict\".");
                sb.append("  \"Error code explanation\": \"HTTPStatus.Conflict - Already exist file name.\" \n");
                sb.append("}");
                content = byteFile(sb);
            }
        } catch (IOException e) {
            state = State.ERR403;
            StringBuilder sb = new StringBuilder();
            sb.append("{").append("\n");
            sb.append("  \"Error code\": \"403\"\n");
            sb.append("  \"Message\": \"Forbidden\".");
            sb.append("  \"Error code explanation\": \"HTTPStatus.Forbidden - You don't have write permission.\" \n");
            sb.append("}");
            content = byteFile(sb);
        }
        String stateMessage = setState();
        sendResponse(writer, stateMessage, content);

        try {
            binaryWriter.write(content);
            binaryWriter.flush();

            end = System.currentTimeMillis();
            printResponse(stateMessage, content);

            writer.close();
            binaryWriter.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public static byte[] byteFile(StringBuilder sb) {
        ByteBuffer bb = Charset.forName("UTF-8").encode(sb.toString());

        int contentLength = bb.limit();
        byte[] content = new byte[contentLength];
        bb.get(content, 0, contentLength);

        return content;
    }


    public static void sendResponse(PrintWriter writer, String stateMessage, byte[] content) {
        writer.println("HTTP/1.1 " + stateMessage);
        writer.println("Date: " + new Date());
        writer.println("Content-type: application/json");
        writer.println("Content-length: " + content.length);
        writer.println("Connection: keep-alive");
        writer.println("Server: Java HTTP Server from taewon : 1.0");
        writer.println("Access-Control-Allow-Origin: *");
        writer.println("Access-Control-Allow-Credentials: true");
        writer.println();
        writer.flush();
    }

    public static void printResponse(String stateMessage, byte[] content) {
        System.out.println("> 응답 코드: " + stateMessage);
        System.out.println("> 응답 크기: " + content.length);
        System.out.println("> 응답에 걸린 시간: " + (end - start) + "ms");
        System.out.println();
    }

    public static String setState() {
        String stateMessage;
        if (state == State.STATE204) stateMessage = "204 OK";
        else if (state == State.ERR405) stateMessage = "405 ERR";
        else if (state == State.ERR403) stateMessage = "403 ERR";
        else if (state == State.ERR404) stateMessage = "404 ERR";
        else if (state == State.ERR409) stateMessage = "409 ERR";
        else stateMessage = "200 OK";

        return stateMessage;
    }

    public static StringBuilder makeResponse(String request, String requestBody, String name, StringBuilder fileContent) {
        StringBuilder sb = new StringBuilder();

        StringBuilder responseData = new StringBuilder();
        StringBuilder responseArgs = new StringBuilder();
        StringBuilder responseHeaders = new StringBuilder();
        StringBuilder responseFile = new StringBuilder();

        if (!requestBody.equals("")) {
            String splitBody = requestBody.substring(2, requestBody.length() - 1);
            String[] beforeParsingJson = splitBody.split(",");

            for (int i = 0; i < beforeParsingJson.length; i++) {
                String key = beforeParsingJson[i].split(":")[0].trim();
                String value = beforeParsingJson[i].split(":")[1].trim();
                if (i == beforeParsingJson.length - 1) {
                    responseData.append("\t").append("\"" + key + "\": " + "\"" + value + "\"").append("\n");
                } else {
                    responseData.append("\t").append("\"" + key + "\": " + "\"" + value + "\",").append("\n");
                }
            }
        }

        if (request.contains("?")) {
            String query = request.split("\\?")[1];
            String[] beforeParsingArgs = query.split("&");

            for (int i = 0; i < beforeParsingArgs.length; i++) {
                String key = beforeParsingArgs[i].split("=")[0].trim();
                String value = beforeParsingArgs[i].split("=")[1].trim();
                if (i == beforeParsingArgs.length - 1) {
                    responseArgs.append("\t").append("\"" + key + "\": " + "\"" + value + "\"").append("\n");
                } else {
                    responseArgs.append("\t").append("\"" + key + "\": " + "\"" + value + "\",").append("\n");
                }
            }
        }

        for (int i = 0; i < requestHeader.size(); i++) {
            String key = requestHeader.get(i).split(":")[0].trim();
            String value = requestHeader.get(i).split(":")[1].trim();
            if (i == requestHeader.size() - 1) {
                responseHeaders.append("\t").append("\"" + key + "\": " + "\"" + value + "\"").append("\n");
            } else {
                responseHeaders.append("\t").append("\"" + key + "\": " + "\"" + value + "\",").append("\n");
            }
        }

        if (fileContent.length() != 0) {
            responseFile.append("\t").append("" + name + ": \"");
            responseFile.append(fileContent).append("\"");
        }


        sb.append("{").append("\n");
        sb.append("  ").append("\"args\": {");
        if (responseArgs.length() != 0) {
            sb.append("\n").append(responseArgs).append("  }\n");
        } else {
            sb.append("},").append("\n");
        }
        if (responseData.length() != 0) {
            sb.append("  ").append("\"data\": \"").append(requestBody).append("\",\n");
        }
        if (responseFile.length() != 0) {
            sb.append("  ").append("\"files\": {\n").append(responseFile).append("\n").append("  },");
        }

        sb.append("  ").append("\"headers\": {").append("\n");
        sb.append(responseHeaders).append("  },").append("\n");
        if (responseData.length() != 0) {
            sb.append("  ").append("\"json\": {\n").append(responseData).append("  },\n");
        }
        if (responseFile.length() != 0) {
            sb.append("  ").append("\"json\": null,\n");
        }
        sb.append("  ").append("\"origin\": ").append("\"" + origin + "\",\n");
        sb.append("  ").append("\"url\": ").append("\"https://" + (hostName + request) + "\",\n");
        sb.append("}");

        return sb;
    }

    public static void main(String[] args) {
        new SimpleHttpbin();
    }
}
