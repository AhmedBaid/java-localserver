import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import utils.HttpRequest;
import utils.HttpResponse;

public class CGIHandler {

    public static HttpResponse handle(HttpRequest request, File scriptFile, String scriptPath, String queryString)
            throws Exception {
        ProcessBuilder builder = new ProcessBuilder("python3", scriptFile.getAbsolutePath());
        Map<String, String> env = builder.environment();

        env.put("GATEWAY_INTERFACE", "CGI/1.1");
        env.put("SERVER_PROTOCOL", request.getVersion() != null ? request.getVersion() : "HTTP/1.1");
        env.put("REQUEST_METHOD", request.getMethod() != null ? request.getMethod() : "");
        env.put("SCRIPT_NAME", scriptPath);
        // PATH_INFO: this project's convention is the script's full absolute
        // path on disk, so a script can always locate itself regardless of
        // the process's working directory.
        env.put("PATH_INFO", scriptFile.getAbsolutePath());
        env.put("QUERY_STRING", queryString != null ? queryString : "");

        String contentType = request.getHeader("Content-Type");
        if (contentType != null) {
            env.put("CONTENT_TYPE", contentType);
        }
        byte[] body = request.getBody();
        env.put("CONTENT_LENGTH", String.valueOf(body != null ? body.length : 0));

        // Forward every request header as HTTP_<NAME> (CGI/1.1 convention).
        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            String key = "HTTP_" + header.getKey().toUpperCase().replace('-', '_');
            env.put(key, header.getValue());
        }

        Process process = builder.start();

        try (OutputStream stdin = process.getOutputStream()) {
            if (body != null && body.length > 0) {
                stdin.write(body);
            }
            stdin.flush();
        }

        InputStream in = process.getInputStream();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }

        process.waitFor();

        return parseCgiOutput(out.toByteArray());
    }

    private static HttpResponse parseCgiOutput(byte[] output) {
        HttpResponse response = new HttpResponse();

        String raw = new String(output);
        String headerBlock;
        String bodyStr;

        int sepIdx = raw.indexOf("\r\n\r\n");
        int sepLen = 4;
        if (sepIdx == -1) {
            sepIdx = raw.indexOf("\n\n");
            sepLen = 2;
        }

        if (sepIdx == -1) {
            headerBlock = "";
            bodyStr = raw;
        } else {
            headerBlock = raw.substring(0, sepIdx);
            bodyStr = raw.substring(sepIdx + sepLen);
        }

        int statusCode = 200;
        String statusMessage = "OK";
        boolean contentTypeSet = false;

        if (!headerBlock.isEmpty()) {
            for (String line : headerBlock.split("\r?\n")) {
                int colon = line.indexOf(':');
                if (colon == -1) {
                    continue;
                }
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();

                if (key.equalsIgnoreCase("Status")) {
                    String[] parts = value.split(" ", 2);
                    try {
                        statusCode = Integer.parseInt(parts[0]);
                    } catch (NumberFormatException ignored) {
                    }
                    statusMessage = parts.length > 1 ? parts[1] : statusMessage;
                } else {
                    if (key.equalsIgnoreCase("Content-Type")) {
                        contentTypeSet = true;
                    }
                    response.setHeader(key, value);
                }
            }
        }

        if (!contentTypeSet) {
            response.setHeader("Content-Type", "text/html; charset=UTF-8");
        }

        response.setStatusCode(statusCode, statusMessage);
        response.setBody(bodyStr.getBytes());
        return response;
    }
}