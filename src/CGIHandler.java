import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.SelectionKey;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import utils.CgiState;
import utils.ClientState;
import utils.HttpRequest;
import utils.HttpResponse;

public class CGIHandler {

    static final long TIMEOUT_MILLIS = 10_000L;

    private static final Map<String, String> INTERPRETERS = new HashMap<>();
    static {
        INTERPRETERS.put(".py", "python3");
        INTERPRETERS.put(".js", "node");
    }
    private static final String TEMP_FILE_HEADER = "Temp-File-Path";


    public static CgiState startCgi(HttpRequest request, File scriptFile,
            String scriptPath, String queryString,
            SelectionKey key, ClientState clientState) throws IOException {

        String interpreter = interpreterFor(scriptFile.getName());
        if (interpreter == null) {
            System.err.println("No CGI interpreter configured for: " + scriptFile.getName());
            return null;
        }

        ProcessBuilder builder = new ProcessBuilder(interpreter, scriptFile.getAbsolutePath());
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Map<String, String> env = builder.environment();

        env.put("GATEWAY_INTERFACE", "CGI/1.1");
        env.put("SERVER_PROTOCOL", request.getVersion() != null ? request.getVersion() : "HTTP/1.1");
        env.put("REQUEST_METHOD", request.getMethod() != null ? request.getMethod() : "");
        env.put("SCRIPT_NAME", scriptPath);
        env.put("PATH_INFO", scriptFile.getAbsolutePath());
        env.put("QUERY_STRING", queryString != null ? queryString : "");

        String contentType = request.getHeader("Content-Type");
        if (contentType != null) {
            env.put("CONTENT_TYPE", contentType);
        }

        String tempFilePathHeader = request.getHeader(TEMP_FILE_HEADER);
        File stdinSourceFile;
        boolean ownsStdinFile;
        long contentLength;

        if (tempFilePathHeader != null) {
            stdinSourceFile = new File(tempFilePathHeader);
            contentLength = stdinSourceFile.exists() ? stdinSourceFile.length() : 0;
            ownsStdinFile = false;
        } else {
            byte[] body = request.getBody();
            stdinSourceFile = File.createTempFile("cgi-stdin-", ".tmp");
            if (body != null && body.length > 0) {
                Files.write(stdinSourceFile.toPath(), body);
            }
            contentLength = body != null ? body.length : 0;
            ownsStdinFile = true;
        }
        env.put("CONTENT_LENGTH", String.valueOf(contentLength));

        for (Map.Entry<String, String> header : request.getHeaders().entrySet()) {
            if (header.getKey().equalsIgnoreCase(TEMP_FILE_HEADER)) {
                continue;
            }
            String k = "HTTP_" + header.getKey().toUpperCase().replace('-', '_');
            env.put(k, header.getValue());
        }

        builder.redirectInput(stdinSourceFile);

        Process process = builder.start();
        long deadline = System.currentTimeMillis() + TIMEOUT_MILLIS;
        return new CgiState(process, process.getInputStream(), deadline,
                key, clientState, stdinSourceFile, ownsStdinFile);
    }
    public static HttpResponse pollCgi(CgiState state) throws IOException {
        InputStream in = state.stdout;
        byte[] buf = new byte[4096];

        if (System.currentTimeMillis() > state.deadlineMillis) {
            state.process.destroyForcibly();
            cleanup(state);
            System.err.println("CGI script timed out: " + state.process);
            return buildErrorResponse(500, "Internal Server Error - CGI Timeout");
        }

        int available = in.available();
        if (available > 0) {
            int n = in.read(buf, 0, Math.min(available, buf.length));
            if (n > 0) {
                state.outputBuffer.write(buf, 0, n);
            }
            return null; // more to come, check back next tick
        }

        if (!state.process.isAlive()) {
            int n;
            while ((n = in.read(buf)) != -1) {
                state.outputBuffer.write(buf, 0, n);
            }

            int exitCode = state.process.exitValue();
            cleanup(state);

            if (exitCode != 0) {
                System.err.println("CGI script exited with code " + exitCode);
                return buildErrorResponse(500, "Internal Server Error - CGI Script Error");
            }

            return parseCgiOutput(state.outputBuffer.toByteArray());
        }

        return null;
    }

    private static void cleanup(CgiState state) {
        if (state.ownsStdinFile && state.stdinSourceFile != null) {
            state.stdinSourceFile.delete();
        }
    }

    private static HttpResponse buildErrorResponse(int code, String message) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(code, message);
        response.setHeader("Content-Type", "text/html; charset=UTF-8");
        response.setBody(("<html><body><center><h1>" + code + " " + message
                + "</h1></center></body></html>").getBytes());
        return response;
    }

    private static String interpreterFor(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot == -1) {
            return null;
        }
        return INTERPRETERS.get(fileName.substring(dot).toLowerCase());
    }

    public static HttpResponse parseCgiOutput(byte[] output) {
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