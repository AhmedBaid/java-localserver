package utils;

import java.util.Arrays;

public class RequestParser {
    public static HttpRequest parseRequest(byte[] request) {
        if (request == null || request.length < 4) {
            return null;
        }
        int headerEndIndex = findHeaderEndIndex(request);
        if (headerEndIndex == -1) {
            return null;
        }
        byte[] headerBytes = Arrays.copyOfRange(request, 0, headerEndIndex);

        String headerString = new String(headerBytes);
        HttpRequest httpRequest = new HttpRequest();
        parseHeader(headerString, httpRequest);

        int bodyStartIndex = headerEndIndex;
        if (bodyStartIndex < request.length) {
            byte[] bodyBytes = Arrays.copyOfRange(request, bodyStartIndex, request.length);
            httpRequest.setBody(bodyBytes);
        }

        return httpRequest;
    }

    public static int findHeaderEndIndex(byte[] request) {
        for (int i = 0; i < request.length - 3; i++) {
            if (request[i] == '\r' && request[i + 1] == '\n' && request[i + 2] == '\r' && request[i + 3] == '\n') {
                return i + 4;
            }
        }
        return -1;
    }

    public static void parseHeader(String headerString, HttpRequest httpRequest) {
        String[] lines = headerString.split("\r\n");
        if (lines.length > 0) {
            String[] requestLineParts = lines[0].split(" ");
            if (requestLineParts.length == 3) {
                httpRequest.setMethod(requestLineParts[0]);
                httpRequest.setPath(requestLineParts[1]);
                httpRequest.setVersion(requestLineParts[2]);
            }
        }

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            int colonIndex = line.indexOf(":");
            if (colonIndex != -1) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                httpRequest.addHeader(key, value);
            }
        }
    }
}