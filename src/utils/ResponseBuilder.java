package utils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class ResponseBuilder {

    public static HttpResponse build(HttpRequest request, RouteConfig route) {
        HttpResponse response = new HttpResponse();

        // 1. If no route matched, return 404
        if (route == null) {
            return buildErrorResponse(404, "Not Found");
        }

        // 2. Check if the method (GET, POST) is allowed in this route
        if (route.getMethods() != null && !route.getMethods().contains(request.getMethod())) {
            return buildErrorResponse(405, "Method Not Allowed");
        }

        // 3. Resolve the actual file path
        // Example: root = "./www", route.path = "/", request.path = "/index.html"
        // Target file = "./www/index.html"
        String relativePath = request.getPath().substring(route.getPath().length());
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }
        
        File targetFile = new File(route.getRoot() + relativePath);

        // 4. Handle Directory requests (Serve default_file)
        if (targetFile.isDirectory()) {
            if (route.getDefaultFile() != null) {
                targetFile = new File(targetFile, route.getDefaultFile());
            } else if (route.getDirectoryListing()) {
                // Here you would generate an HTML page listing directory contents
                response.setBody("<html><body>Directory Listing Enabled (WIP)</body></html>".getBytes());
                response.setHeader("Content-Type", "text/html");
                return response;
            } else {
                return buildErrorResponse(403, "Forbidden");
            }
        }

        // 5. Read the file and create the 200 OK response
        if (targetFile.exists() && targetFile.isFile()) {
            try {
                byte[] fileContent = Files.readAllBytes(targetFile.toPath());
                response.setBody(fileContent);
                // Hint: You should add a method here to detect content type based on extension (e.g., .css -> text/css)
                response.setHeader("Content-Type", "text/html"); 
                return response;
            } catch (IOException e) {
                return buildErrorResponse(500, "Internal Server Error");
            }
        } else {
            return buildErrorResponse(404, "Not Found");
        }
    }

    private static HttpResponse buildErrorResponse(int code, String message) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(code, message);
        String body = "<html><body><h1>" + code + " - " + message + "</h1></body></html>";
        response.setBody(body.getBytes());
        response.setHeader("Content-Type", "text/html");
        return response;
    }
}