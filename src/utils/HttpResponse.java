package utils;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class HttpResponse {
    private int statusCode = 200;
    private String statusMessage = "OK";
    private Map<String, String> headers = new HashMap<>();
    private byte[] body = new byte[0];

    public void setStatusCode(int statusCode, String statusMessage) {
        this.statusCode = statusCode;
        this.statusMessage = statusMessage;
    }

    public void setHeader(String key, String value) {
        headers.put(key, value);
    }

    public void setBody(byte[] body) {
        this.body = body;
        setHeader("Content-Length", String.valueOf(body.length));
    }

    // Converts the entire HTTP response into a ByteBuffer ready to be sent via NIO
    public ByteBuffer toByteBuffer() {
        StringBuilder responseString = new StringBuilder();
        responseString.append("HTTP/1.1 ").append(statusCode).append(" ").append(statusMessage).append("\r\n");
        
        for (Map.Entry<String, String> header : headers.entrySet()) {
            responseString.append(header.getKey()).append(": ").append(header.getValue()).append("\r\n");
        }
        
        responseString.append("\r\n"); // Empty line separating headers and body

        byte[] headerBytes = responseString.toString().getBytes();
        ByteBuffer buffer = ByteBuffer.allocate(headerBytes.length + body.length);
        buffer.put(headerBytes);
        buffer.put(body);
        buffer.flip(); // Prepare for reading by the SocketChannel
        
        return buffer;
    }
}