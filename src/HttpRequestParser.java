

import java.nio.channels.SocketChannel;
import java.util.HashMap;
import java.util.Map;

public class HttpRequestParser {

    public static void parseRequest(SocketChannel client, String rawRequest) {
        if (rawRequest == null || rawRequest.isEmpty()) {
            return;
        }

        try {
            // Split the raw request by the standard HTTP line break (CRLF)
            String[] lines = rawRequest.split("\r\n");

            // 1. Parse the initial Request Line (e.g., "GET /index.html HTTP/1.1")
            String requestLine = lines[0];
            String[] requestLineParts = requestLine.split(" ");

            if (requestLineParts.length < 3) {
                System.out.println("Invalid HTTP request format.");
                return;
            }

            String method = requestLineParts[0]; // GET, POST, etc.
            String path = requestLineParts[1]; // /index.html
            String version = requestLineParts[2]; // HTTP/1.1

            System.out.println("Method: " + method);
            System.out.println("Path: " + path);
            System.out.println("Version: " + version);

            // 2. Parse headers
            Map<String, String> headers = new HashMap<>();
            int lineIndex = 1;
            int contentLength = 0;

            // Read lines until we hit an empty line (which separates headers from body)
            while (lineIndex < lines.length && !lines[lineIndex].isEmpty()) {
                String headerLine = lines[lineIndex];
                int colonIndex = headerLine.indexOf(":");

                if (colonIndex != -1) {
                    String headerName = headerLine.substring(0, colonIndex).trim();
                    String headerValue = headerLine.substring(colonIndex + 1).trim();
                    headers.put(headerName, headerValue);

                    if (headerName.equalsIgnoreCase("Content-Length")) {
                        contentLength = Integer.parseInt(headerValue);
                    }
                }
                lineIndex++;
            }

            System.out.println("Headers parsed: " + headers.size() + " headers found");

            // 3. Parse the body (if there is one, typically for POST/PUT requests)
            String body = "";
            // The body starts after the empty line
            lineIndex++;

            if (lineIndex < lines.length && contentLength > 0) {
                // Reconstruct the body from the remaining lines
                StringBuilder bodyBuilder = new StringBuilder();
                for (int i = lineIndex; i < lines.length; i++) {
                    bodyBuilder.append(lines[i]);
                    if (i < lines.length - 1) {
                        bodyBuilder.append("\r\n");
                    }
                }
                body = bodyBuilder.toString();
                System.out.println("Body parsed:\n" + body);
            } else {
                System.out.println("Body parsed: [Empty]");
            }

            // Router.handleRoute(client, path);

        } catch (Exception e) {
            System.err.println("Error parsing request: " + e.getMessage());
        }
    }
}