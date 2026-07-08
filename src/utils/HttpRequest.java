package utils;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HttpRequest {
    private String method;
    private String path;
    private String version;

    private Map<String, String> headers;

    private byte[] body;

    private Map<String, byte[]> uploadedFiles;

    private Map<String, String> formFields;

    public HttpRequest() {
        this.headers = new HashMap<>();
        this.uploadedFiles = new HashMap<>();
        this.formFields = new HashMap<>();
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void addHeader(String key, String value) {
        this.headers.put(key, value);
    }

    public String getHeader(String key) {
        // Case-insensitive lookup (HTTP headers are case-insensitive)
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public byte[] getBody() {
        return body;
    }

    public void setBody(byte[] body) {
        this.body = body;
    }

    public Map<String, byte[]> getUploadedFiles() {
        return uploadedFiles;
    }

    public Map<String, String> getFormFields() {
        return formFields;
    }
    /**}
     * Parse the "Cookie" request header into a list of Cookie objects.
     */
    public List<Cookie> getCookies() {
        List<Cookie> cookies = new ArrayList<>();
        String cookieHeader = getHeader("Cookie");
        if (cookieHeader == null || cookieHeader.isEmpty()) {
            return cookies;
        }
        String[] parts = cookieHeader.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            int eqIdx = trimmed.indexOf('=');
            if (eqIdx == -1) continue;
            String name = trimmed.substring(0, eqIdx).trim();
            String value = trimmed.substring(eqIdx + 1).trim();
            cookies.add(new Cookie(name, value));
        }
        return cookies;
    }

    /**
     * Get a single cookie by name from the request, or null if not found.
     */
    public Cookie getCookie(String name) {
        for (Cookie c : getCookies()) {
            if (c.getName().equals(name)) {
                return c;
            }
        }
        return null;
    }

    public HttpRequest(String method, String path, String version, Map<String, String> headers, byte[] body) {
        this.method = method;
        this.path = path;
        this.version = version;
        this.headers = headers != null ? headers : new HashMap<>();
        this.body = body;
        this.uploadedFiles = new HashMap<>();
        this.formFields = new HashMap<>();
    }

    public void parseMultipartBody() {
        String contentType = this.getHeader("Content-Type");
        if (contentType == null || !contentType.toLowerCase().contains("multipart/form-data")) {
            return;
        }

        String boundary = extractBoundary(contentType);
        if (boundary == null || body == null || body.length == 0) {
            return;
        }

        byte[] boundaryBytes = ("--" + boundary).getBytes();
        byte[] headerEnd = "\r\n\r\n".getBytes();

        int pos = 0;
        while (pos < body.length) {
            int boundaryPos = findBytes(body, boundaryBytes, pos);
            if (boundaryPos == -1) {
                break;
            }

            int contentStart = boundaryPos + boundaryBytes.length;

            if (contentStart + 1 < body.length
                    && body[contentStart] == '-'
                    && body[contentStart + 1] == '-') {
                break;
            }

            if (contentStart + 1 < body.length
                    && body[contentStart] == '\r'
                    && body[contentStart + 1] == '\n') {
                contentStart += 2;
            }

            int nextBoundary = findBytes(body, boundaryBytes, contentStart);
            int contentEnd = (nextBoundary != -1) ? nextBoundary : body.length;

            if (contentEnd >= 2
                    && body[contentEnd - 2] == '\r'
                    && body[contentEnd - 1] == '\n') {
                contentEnd -= 2;
            }

            if (contentEnd <= contentStart) {
                pos = (nextBoundary != -1) ? nextBoundary : body.length;
                continue;
            }

            byte[] part = Arrays.copyOfRange(body, contentStart, contentEnd);

            int partHeaderEnd = findBytes(part, headerEnd, 0);
            if (partHeaderEnd != -1) {
                byte[] partHeaderBytes = Arrays.copyOfRange(part, 0, partHeaderEnd);
                String partHeaders = new String(partHeaderBytes);

                String filename = extractContentDispositionParam(partHeaders, "filename");
                String fieldName = extractContentDispositionParam(partHeaders, "name");

                int partBodyStart = partHeaderEnd + 4;
                byte[] partContent = Arrays.copyOfRange(part, partBodyStart, part.length);

                if (filename != null && !filename.isEmpty()) {
                    uploadedFiles.put(filename, partContent);
                } else if (fieldName != null && !fieldName.isEmpty()) {
                    formFields.put(fieldName, new String(partContent));
                }
            }

            pos = (nextBoundary != -1) ? nextBoundary : body.length;
        }
    }

    private String extractBoundary(String contentType) {
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.toLowerCase().startsWith("boundary=")) {
                String boundary = part.substring("boundary=".length());
                if (boundary.startsWith("\"") && boundary.endsWith("\"") && boundary.length() >= 2) {
                    boundary = boundary.substring(1, boundary.length() - 1);
                }
                return boundary;
            }
        }
        return null;
    }

    private String extractContentDispositionParam(String headers, String paramName) {
        for (String line : headers.split("\r\n")) {
            if (line.toLowerCase().startsWith("content-disposition:")) {
                for (String part : line.split(";")) {
                    part = part.trim();
                    int eqIdx = part.indexOf('=');
                    if (eqIdx == -1) continue;

                    String key = part.substring(0, eqIdx).trim().toLowerCase();
                    if (key.startsWith("content-disposition:")) {
                        key = key.substring("content-disposition:".length()).trim();
                    }

                    if (key.equals(paramName)) {
                        String value = part.substring(eqIdx + 1).trim();
                        if (value.startsWith("\"") && value.endsWith("\"") && value.length() >= 2) {
                            value = value.substring(1, value.length() - 1);
                        }
                        return value;
                    }
                }
            }
        }
        return null;
    }

    private static int findBytes(byte[] source, byte[] pattern, int offset) {
        if (source == null || pattern == null || pattern.length == 0) {
            return -1;
        }
        for (int i = offset; i <= source.length - pattern.length; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                if (source[i + j] != pattern[j]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Compact, single-line, human-readable summary of the request.
     * e.g. GET /cgi/test.py?name=Ahmed HTTP/1.1 | headers=3 | body=0B
     * Meant for logging -- use {@link #toDebugString()} when you need the
     * full header dump.
     */
    @Override
    public String toString() {
        int bodyLen = body != null ? body.length : 0;
        return String.format("%s %s %s | headers=%d | body=%dB",
                method, path, version, headers.size(), bodyLen);
    }

    /**
     * Multi-line dump of the request, including every header on its own
     * line, indented like a raw HTTP request. Useful when {@link #toString()}
     * isn't enough detail (e.g. debugging a specific header value).
     */
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append(method).append(' ').append(path).append(' ').append(version).append('\n');

        headers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .forEach(e -> sb.append("  ").append(e.getKey()).append(": ").append(e.getValue()).append('\n'));

        int bodyLen = body != null ? body.length : 0;
        sb.append("  [body: ").append(bodyLen).append(" bytes]");

        if (!uploadedFiles.isEmpty()) {
            sb.append('\n').append("  [uploadedFiles: ").append(uploadedFiles.keySet()).append(']');
        }
        if (!formFields.isEmpty()) {
            sb.append('\n').append("  [formFields: ").append(formFields.keySet()).append(']');
        }

        return sb.toString();
    }

}
