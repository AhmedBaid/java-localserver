package utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class ResponseBuilder {

    public static HttpResponse build(HttpRequest request, RouteConfig route) {
        HttpResponse response = new HttpResponse();

        if (route == null) {
            return buildErrorResponse(404, "Not Found", null);
        }

        if (route.getMethods() != null && !route.getMethods().contains(request.getMethod())) {
            return buildErrorResponse(405, "Method Not Allowed", route.getErrorPages());
        }

        // Handle redirect
        if (route.getRedirect() != null) {
            response.setStatusCode(301, "Moved Permanently");
            response.setHeader("Location", route.getRedirect());
            response.setBody(new byte[0]);
            return response;
        }

        String fullPath = request.getPath();
        String queryString = "";
        int qIdx = fullPath.indexOf('?');
        if (qIdx != -1) {
            queryString = fullPath.substring(qIdx + 1);
            fullPath = fullPath.substring(0, qIdx);
        }

        String relativePath = fullPath.substring(route.getPath().length());
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }

        File targetFile = new File(route.getRoot() + relativePath);

        try {
            File rootDir = new File(route.getRoot()).getCanonicalFile();
            File resolved = targetFile.getCanonicalFile();
            if (!resolved.getPath().startsWith(rootDir.getPath())) {
                return buildErrorResponse(403, "Forbidden", route.getErrorPages());
            }
            targetFile = resolved;
        } catch (IOException e) {
            return buildErrorResponse(500, "Internal Server Error", route.getErrorPages());
        }

        if (isCgiRequest(route, targetFile)) {
            if (!"GET".equals(request.getMethod())) {
                return buildErrorResponse(405, "Method Not Allowed", route.getErrorPages());
            }
            return null;
        }

        if ("POST".equals(request.getMethod())) {
            return handlePost(request, route, targetFile);
        }

        if ("DELETE".equals(request.getMethod())) {
            return handleDelete(route, targetFile);
        }

        return handleGet(route, targetFile, relativePath);
    }

    public static File resolveTargetFile(HttpRequest request, RouteConfig route) {
        String fullPath = request.getPath();
        int qIdx = fullPath.indexOf('?');
        if (qIdx != -1) {
            fullPath = fullPath.substring(0, qIdx);
        }
        String relativePath = fullPath.substring(route.getPath().length());
        if (!relativePath.startsWith("/")) {
            relativePath = "/" + relativePath;
        }
        File targetFile = new File(route.getRoot() + relativePath);
        try {
            File rootDir = new File(route.getRoot()).getCanonicalFile();
            File resolved = targetFile.getCanonicalFile();
            if (!resolved.getPath().startsWith(rootDir.getPath())) {
                return null;
            }
            return resolved;
        } catch (java.io.IOException e) {
            return null;
        }
    }

    public static String extractQueryString(HttpRequest request) {
        String path = request.getPath();
        int qIdx = path.indexOf('?');
        return qIdx != -1 ? path.substring(qIdx + 1) : "";
    }

    public static String extractScriptPath(HttpRequest request) {
        String path = request.getPath();
        int qIdx = path.indexOf('?');
        return qIdx != -1 ? path.substring(0, qIdx) : path;
    }

    private static boolean isCgiRequest(RouteConfig route, File targetFile) {
        if (route.getCgiExtensions() == null || route.getCgiExtensions().isEmpty()) {
            return false;
        }
        if (!targetFile.isFile()) {
            return false;
        }
        String name = targetFile.getName();
        int dot = name.lastIndexOf('.');
        if (dot == -1) {
            return false;
        }
        String ext = name.substring(dot);
        return route.getCgiExtensions().contains(ext);
    }

    private static HttpResponse handleGet(RouteConfig route, File targetFile, String relativePath) {
        if (targetFile.isDirectory()) {
            if (route.getDefaultFile() != null) {
                File defaultFile = new File(targetFile, route.getDefaultFile());
                if (defaultFile.exists() && defaultFile.isFile()) {
                    return serveFile(defaultFile);
                }
                return buildErrorResponse(404, "Not Found", route.getErrorPages());
            }

            if (route.getDirectoryListing() != null && route.getDirectoryListing()) {
                return generateDirectoryListing(targetFile, relativePath, route);
            }

            return buildErrorResponse(403, "Forbidden", route.getErrorPages());
        }

        if (targetFile.exists() && targetFile.isFile()) {
            return serveFile(targetFile);
        }

        return buildErrorResponse(404, "Not Found", route.getErrorPages());
    }

    private static HttpResponse serveFile(File file) {
        HttpResponse response = new HttpResponse();
        try {
            byte[] fileContent = Files.readAllBytes(file.toPath());
            response.setBody(fileContent);

            String mimeType = MimeTypeUtil.getMimeType(file.getName());
            response.setHeader("Content-Type", mimeType);

            return response;
        } catch (IOException e) {
            return buildErrorResponse(500, "Internal Server Error", null);
        }
    }

    private static HttpResponse generateDirectoryListing(File dir, String relativePath, RouteConfig route) {
        HttpResponse response = new HttpResponse();
        response.setHeader("Content-Type", "text/html; charset=UTF-8");
        String basePath = route.getPath();
        if (basePath == null)
            basePath = "";

        if (basePath.endsWith("/") && relativePath.startsWith("/")) {
            basePath += relativePath.substring(1);
        } else if (!basePath.endsWith("/") && !relativePath.startsWith("/")) {
            basePath += "/" + relativePath;
        } else {
            basePath += relativePath;
        }

        if (!basePath.endsWith("/")) {
            basePath += "/";
        }

        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n<html>\n<head>\n");
        html.append("<meta charset=\"UTF-8\">\n");
        html.append("<title>Index of ").append(escapeHtml(basePath)).append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: monospace; background: #1e1e1e; color: #d4d4d4; padding: 20px; }\n");
        html.append("a { color: #569cd6; text-decoration: none; display: block; padding: 4px 8px; }\n");
        html.append("a:hover { background: #2a2a2a; }\n");
        html.append(".dir::before { content: \"📁 \"; }\n");
        html.append(".file::before { content: \"📄 \"; }\n");
        html.append("pre { line-height: 1.6; }\n");
        html.append("</style>\n</head>\n<body>\n");

        html.append("<h1>Page Upload</h1>\n");
        html.append("<hr>\n<pre>\n");

        if (!relativePath.equals("/") && !relativePath.isEmpty()) {
            int lastSlashPos = basePath.lastIndexOf('/', basePath.length() - 2);
            String parentPath = (lastSlashPos >= 0) ? basePath.substring(0, lastSlashPos + 1) : "/";
            html.append("<a href=\"").append(escapeHtml(parentPath)).append("\" class=\"dir\">../</a>\n");
        }

        File[] files = dir.listFiles();
        if (files != null) {
            java.util.Arrays.sort(files, (a, b) -> {
                if (a.isDirectory() && !b.isDirectory())
                    return -1;
                if (!a.isDirectory() && b.isDirectory())
                    return 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });

            for (File f : files) {
                String name = f.getName();
                String cssClass = f.isDirectory() ? "dir" : "file";
                String suffix = f.isDirectory() ? "/" : "";

                html.append("<a href=\"").append(escapeHtml(basePath)).append(escapeHtml(name)).append(suffix)
                        .append("\" class=\"").append(cssClass).append("\">");
                html.append(escapeHtml(name)).append(suffix).append("</a>\n");
            }
        }

        html.append("</body>\n</html>");

        response.setBody(html.toString().getBytes());
        return response;
    }

    private static String escapeHtml(String input) {
        if (input == null)
            return "";
        return input
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static HttpResponse handlePost(HttpRequest request, RouteConfig route, File targetFile) {
        File uploadDir = new File(route.getRoot());
        if (!uploadDir.exists()) {
            try {
                Files.createDirectories(uploadDir.toPath());
            } catch (IOException e) {
                System.err.println("Failed to create upload directory: " + e.getMessage());
                return buildErrorResponse(500, "Internal Server Error - Cannot create upload directory",
                        route.getErrorPages());
            }
        }

        String contentType = request.getHeader("Content-Type");
        boolean isMultipart = contentType != null
                && contentType.toLowerCase().contains("multipart/form-data");

        try {
            if (isMultipart) {
                String tempFilePath = request.getHeader("Temp-File-Path");

                if (tempFilePath != null) {
                    List<String> uploadedNames = MultipartHandler.extractUploadedFiles(request,
                            uploadDir.getAbsolutePath());

                    if (!uploadedNames.isEmpty()) {
                        String responseMessage = "Large File(s) uploaded successfully: "
                                + String.join(", ", uploadedNames);

                        HttpResponse response = new HttpResponse();
                        response.setStatusCode(201, "Created");
                        response.setHeader("Content-Type", "text/plain; charset=UTF-8");
                        response.setBody(responseMessage.getBytes());
                        return response;
                    } else {
                        return buildErrorResponse(400, "Bad Request - Upload extraction failed or no files found",
                                route.getErrorPages());
                    }

                } else {
                    request.parseMultipartBody();
                    Map<String, byte[]> uploadedFiles = request.getUploadedFiles();

                    if (uploadedFiles.isEmpty()) {
                        System.err.println("Upload failed: No files found in multipart body");
                        return buildErrorResponse(400, "Bad Request - No files found in upload", route.getErrorPages());
                    }

                    StringBuilder savedFiles = new StringBuilder();
                    for (Map.Entry<String, byte[]> entry : uploadedFiles.entrySet()) {
                        String filename = entry.getKey();
                        byte[] fileContent = entry.getValue();

                        String safeName = new File(filename).getName();

                        String uniqueName = java.util.UUID.randomUUID().toString() + "_" + safeName;
                        File outFile = new File(uploadDir, uniqueName);

                        Files.write(outFile.toPath(), fileContent);

                        if (savedFiles.length() > 0) {
                            savedFiles.append(", ");
                        }
                        savedFiles.append(uniqueName);
                    }

                    HttpResponse response = new HttpResponse();
                    response.setStatusCode(201, "Created");
                    response.setHeader("Content-Type", "text/plain; charset=UTF-8");
                    response.setBody(("File(s) uploaded successfully: " + savedFiles.toString()).getBytes());
                    return response;
                }

            } else {
                if (targetFile.isDirectory()) {
                    System.err.println("Upload failed: Cannot upload to a directory path without a filename");
                    return buildErrorResponse(400,
                            "Bad Request - Cannot upload to a directory path. Use multipart/form-data for file uploads.",
                            route.getErrorPages());
                }

                File parentDir = targetFile.getParentFile();
                if (parentDir != null && !parentDir.exists()) {
                    Files.createDirectories(parentDir.toPath());
                }

                byte[] body = request.getBody();
                if (body == null) {
                    body = new byte[0];
                }

                Files.write(targetFile.toPath(), body);

                HttpResponse response = new HttpResponse();
                response.setStatusCode(201, "Created");
                response.setHeader("Content-Type", "text/plain; charset=UTF-8");
                response.setBody(("File uploaded successfully: " + targetFile.getName()).getBytes());
                return response;
            }

        } catch (IOException e) {
            System.err.println("Upload failed: " + e.getMessage());
            return buildErrorResponse(500, "Internal Server Error - Upload failed", route.getErrorPages());
        }
    }

    private static HttpResponse handleDelete(RouteConfig route, File targetFile) {
        File rootDir = new File(route.getRoot());
        try {
            rootDir = rootDir.getCanonicalFile();
            targetFile = targetFile.getCanonicalFile();

            if (!targetFile.getPath().startsWith(rootDir.getPath())) {
                return buildErrorResponse(403, "Forbidden", route.getErrorPages());
            }
        } catch (IOException e) {
            return buildErrorResponse(500, "Internal Server Error", route.getErrorPages());
        }

        if (!targetFile.exists()) {
            return buildErrorResponse(404, "Not Found - File '" + targetFile.getName()
                    + "' does not exist", route.getErrorPages());
        }

        if (targetFile.isDirectory()) {
            return buildErrorResponse(400, "Bad Request - Cannot delete a directory", route.getErrorPages());
        }

        try {
            String filename = targetFile.getName();
            Files.delete(targetFile.toPath());

            HttpResponse response = new HttpResponse();
            response.setStatusCode(200, "OK");
            response.setHeader("Content-Type", "text/plain; charset=UTF-8");
            response.setBody(("File deleted successfully: " + filename).getBytes());
            return response;

        } catch (IOException e) {
            System.err.println("Delete failed: " + e.getMessage());
            return buildErrorResponse(500, "Internal Server Error - Delete failed", route.getErrorPages());
        }
    }

    public static HttpResponse buildErrorResponse(int code, String message, Map<String, String> errorPages) {
        HttpResponse response = new HttpResponse();
        response.setStatusCode(code, message);
        response.setHeader("Content-Type", "text/html; charset=UTF-8");

        if (errorPages != null && errorPages.containsKey(String.valueOf(code))) {
            String errorPagePath = errorPages.get(String.valueOf(code));
            File errorFile = new File(errorPagePath);
            if (errorFile.exists() && errorFile.isFile()) {
                try {
                    byte[] fileContent = Files.readAllBytes(errorFile.toPath());
                    response.setBody(fileContent);
                    return response;
                } catch (IOException e) {
                    System.err.println("Warning: Failed to read custom error page: " + errorPagePath);
                }
            }
        }

        File errorFile = new File("./error_pages/" + code + ".html");
        if (errorFile.exists() && errorFile.isFile()) {
            try {
                byte[] fileContent = Files.readAllBytes(errorFile.toPath());
                response.setBody(fileContent);
                return response;
            } catch (IOException e) {
                System.err.println("Warning: Failed to read custom error page for " + code);
            }
        }

        String fallbackBody = "<html><body><center><h1>" + code + " - " + message
                + "</h1></center><hr><center>Custom Java Server</center></body></html>";
        response.setBody(fallbackBody.getBytes());
        return response;
    }
}