package com.nhn;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

public class SimpleCurl {
    static boolean isV = false;
    static boolean isL = false;
    static ArrayList<String> H = new ArrayList<>();
    static String D = "";
    static String X = "GET";
    static String F = null;
    static URL url;
    static int count = 0;
    static final String boundary = "------------------------" + Long.toHexString(System.currentTimeMillis());
    private final static String LINE_FEED = "\r\n";


    public static void main(String[] args) throws MalformedURLException, UnknownHostException {
        parameterSetting(args);
        connect(InetAddress.getByName(url.getHost()));
    }

    private static void parameterSetting(String[] args) throws MalformedURLException {
        if (!args[0].equals("scurl")) {
            throw new RuntimeException();
        }
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-v")) {
                isV = true;
            } else if (args[i].equals("-H")) {
                String header = args[++i];
                H.addAll(Arrays.asList(header.split(",")));
            } else if (args[i].equals(("-d"))) {
                D = args[++i];
            } else if (args[i].equals("-X")) {
                if (args[i + 1].equals("POST") || args[i + 1].equals("GET") || args[i + 1].equals("PUT")) {
                    X = args[++i];
                }
            } else if (args[i].equals("-L")) {
                isL = true;
            } else if (args[i].equals("-F")) {
                F = args[++i];
            } else if (args[i].contains("http")) {
                url = new URL(args[i]);
            }
        }
    }

    public static void connect(InetAddress inetAddress) {
        String ipAddress = inetAddress.getHostAddress();
        try (Socket socket = new Socket(ipAddress, 80)) {
            printer(socket);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void printer(Socket socket) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            OutputStream os = socket.getOutputStream();
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(os));
            StringBuilder sb = null;
            if (F != null) {
                X = "POST";
            }

            writer.println(X + " " + url.getPath() + " HTTP/1.1");
            writer.println("Host: " + url.getHost());
            writer.println("User-Agent: scurl/7.78.0");
            writer.println("Accept: */*");

            for (int i = 0; i < H.size(); i++) {
                writer.println(H.get(i));
            }

            if (D.length() != 0) {
                writer.println("Content-Length: " + D.length());
            }

            if (F == null) {
                writer.println();
                writer.flush();
            }

            if (D.length() != 0) {
                writer.println(D);
            }
            int length = 0;

            if (F != null) {
                String fileName = F.split("@")[1];
                File file = new File(fileName);
                FileInputStream fileStream = new FileInputStream(file);

                BufferedOutputStream bufferWriter = new BufferedOutputStream(socket.getOutputStream());
                sb = new StringBuilder();

                sb.append("--"+boundary);
                sb.append(LINE_FEED);
                sb.append("Content-Disposition: form-data; filename=\"" + file.getName() + "\"");
                sb.append(LINE_FEED);

                String format;
                if(fileName.endsWith(".html")){
                    format = "text/html";
                }else if(fileName.endsWith(".txt")){
                    format = "plain/text";
                }else{
                    format = "application/json";
                }

                sb.append("Content-Type: "+format);
                sb.append(LINE_FEED);
                sb.append(LINE_FEED);

                ByteBuffer bb = Charset.forName("UTF-8").encode(sb.toString());

                int contentLength = bb.limit();
                byte[] content = new byte[contentLength];
                bb.get(content, 0, contentLength);

                bufferWriter.write(content);

                byte[] content2 = new byte[(int) file.length()];
                fileStream.read(content2);
                bufferWriter.write(content2);

                StringBuilder sb2 = new StringBuilder();
                sb2.append(LINE_FEED);
                sb2.append("--"+boundary+"--");

                ByteBuffer bb2 = Charset.forName("UTF-8").encode(sb2.toString());

                int contentLength2 = bb2.limit();
                byte[] content3 = new byte[contentLength2];
                bb2.get(content3, 0, contentLength2);

                bufferWriter.write(content3);

                length = content.length+content2.length+content3.length;

                writer.println("Content-Length: "+length);
                writer.println("Content-Type: multipart/form-data; boundary=" + boundary);
                writer.println();
                writer.flush();

                bufferWriter.flush();
            }
            if (isV) {
                requestPrint(length);
            }
            responsePrint(br);
            writer.close();
        }catch (IOException e){
            System.out.println("IOException error occurred");
            System.exit(1);
        }finally {
            System.exit(0);
        }
    }


    public static void requestPrint(int length){
        System.out.println(X + " " + url.getPath() + " HTTP/1.1");
        System.out.println("Host: " + url.getHost());
        System.out.println("User-Agent: scurl/7.78.0");
        System.out.println("Accept: */*");
        for (int i = 0; i < H.size(); i++) {
            System.out.println(H.get(i));
        }
        if(D.length()!=0) {
            System.out.println("Content-Length: " + D.length());
        }
        if(F!=null){
            System.out.println("Content-Length: "+length);
            System.out.println("Content-Type: multipart/form-data; boundary=" + boundary);
        }
        System.out.println();

    }

    public static void responsePrint(BufferedReader br) throws IOException {
        String line;
        while ((line = br.readLine())!=null) {
            if (isL) {
                checkLocation(line);
            }
            if (!isV) {
                if (!line.equals("{")) {
                    continue;
                } else {
                    isV = true;
                }
            }
            System.out.println(line);
        }

    }

    public static void checkLocation(String line) {
        if (line.contains("location") || line.contains("Location")) {
            String redirect = line.split(" ")[1];
            try {
                url = new URL("https://" + url.getHost() + redirect);
                connect(InetAddress.getByName(url.getHost()));
                count++;
                if (count == 6) {
                    throw new Exception();
                }
            } catch (Exception e) {
                System.out.println("리다이렉트 6번 에러로 시스템 종료");
                System.exit(1);
            }
        }
    }
}