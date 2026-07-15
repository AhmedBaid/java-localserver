import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import utils.RouteConfig;
import utils.ServerConfig;

public class ConfigLoader {
    private List<ServerConfig> servers;
    private final String json;
    private int index = 0;

    public ConfigLoader(String filePath) {
        try {
            this.json = Files.readString(Path.of(filePath));
        } catch (Exception e) {
            throw new RuntimeException("Failed to load server configurations from JSON", e);
        }
    }

    private void throwError(String message) {
        throw new RuntimeException("JSON Parse Error at index " + index + ": " + message);
    }

    private Object resolveValue() {
        skipWhitespace();
        if (index >= json.length())
            throwError("Unexpected end of JSON input");

        char c = json.charAt(index);
        if (c == '{')
            return resolveObject();
        if (c == '[')
            return resolveArray();
        if (c == '"')
            return resolveString();
        return resolvePrimitive();
    }

    public void GlobalFormat() {
        skipWhitespace();
        if (index < json.length()) {
            Object rawJsonData = resolveValue();

            this.servers = buildServers(rawJsonData);
        }
    }

    private void skipWhitespace() {
        while (index < json.length() && Character.isWhitespace(json.charAt(index))) {
            index++;
        }
    }

    public String resolveString() {
        index++;
        StringBuilder sb = new StringBuilder();

        while (index < json.length() && json.charAt(index) != '"') {
            sb.append(json.charAt(index));
            index++;
        }

        index++;
        return sb.toString();
    }

    public Object resolveObject() {
        index++;
        Map<String, Object> obj = new HashMap<>();
        boolean expectComma = false;

        while (index < json.length()) {
            skipWhitespace();
            char c = json.charAt(index);

            if (c == '}') {
                index++;
                return obj;
            }

            if (expectComma) {
                if (c == ',') {
                    index++;
                    expectComma = false;
                    continue;
                } else {
                    throwError("Expected ',' but found '" + c + "'");
                }
            }

            if (c != '"') {
                throwError("Expected string key but found '" + c + "'");
            }
            String key = resolveString();

            skipWhitespace();
            if (index >= json.length() || json.charAt(index) != ':') {
                throwError("Expected ':' after key '" + key + "'");
            }
            index++;

            Object value = resolveValue();
            obj.put(key, value);

            expectComma = true;
        }

        throwError("Unterminated JSON object (missing '}')");
        return null;
    }

    public Object resolveArray() {
        index++;
        List<Object> arr = new ArrayList<>();
        boolean expectComma = false;

        while (index < json.length()) {
            skipWhitespace();
            char c = json.charAt(index);

            if (c == ']') {
                index++;
                return arr;
            }

            if (expectComma) {
                if (c == ',') {
                    index++;
                    expectComma = false;
                    continue;
                } else {
                    throwError("Expected ',' but found '" + c + "'");
                }
            }

            Object value = resolveValue();
            arr.add(value);

            expectComma = true;
        }

        throwError("Unterminated JSON array (missing ']')");
        return null;
    }

    public Object resolvePrimitive() {
        StringBuilder sb = new StringBuilder();

        while (index < json.length()) {
            char c = json.charAt(index);
            if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) {
                break;
            }
            sb.append(c);
            index++;
        }

        String strValue = sb.toString();

        if (strValue.equals("true"))
            return true;
        if (strValue.equals("false"))
            return false;
        if (strValue.equals("null"))
            return null;

        try {
            if (strValue.contains(".")) {
                return Double.parseDouble(strValue);
            } else {
                return Long.parseLong(strValue);
            }
        } catch (NumberFormatException e) {
            return strValue; // Fallback
        }
    }

    public List<ServerConfig> getServers() {
        return servers;
    }

    public void setServers(List<ServerConfig> servers) {
        this.servers = servers;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    @SuppressWarnings("unchecked")
    private List<ServerConfig> buildServers(Object rawJsonData) {
        List<ServerConfig> parsedServers = new ArrayList<>();

        if (!(rawJsonData instanceof Map)) {
            throw new RuntimeException("Invalid config format: Root must be a JSON object");
        }

        Map<String, Object> rootMap = (Map<String, Object>) rawJsonData;

        Object serversArrayObj = rootMap.get("servers");
        if (!(serversArrayObj instanceof List)) {
            throw new RuntimeException("Invalid config format: 'servers' must be an array");
        }

        List<Object> serversArray = (List<Object>) serversArrayObj;

        for (Object serverObj : serversArray) {
            Map<String, Object> serverMap = (Map<String, Object>) serverObj;
            String serverName = (String) serverMap.get("server_name");
            String host = (String) serverMap.get("host");

            Number limitNum = (Number) serverMap.get("client_body_limit");
            Long clientBodyLimit = limitNum != null ? limitNum.longValue() : 1048576L; // Default to 1MB

            List<Object> rawPorts = (List<Object>) serverMap.get("ports");
            List<Integer> ports = new ArrayList<>();
            if (rawPorts != null) {
                for (Object p : rawPorts) {
                    ports.add(((Number) p).intValue());
                }
            }

            Map<String, String> errorPages = (Map<String, String>) serverMap.get("error_pages");

            List<RouteConfig> routes = new ArrayList<>();

            List<Object> rawRoutes = (List<Object>) serverMap.get("routes");
            if (rawRoutes != null) {
                for (Object routeObj : rawRoutes) {
                    Map<String, Object> routeMap = (Map<String, Object>) routeObj;
                    String path = (String) routeMap.get("path");
                    String root = (String) routeMap.get("root");
                    List<String> methods = (List<String>) routeMap.get("methods");

                    Boolean directoryListing = (Boolean) routeMap.get("directory_listing");
                    Long clientBodyLimitRoute = (Long) routeMap.get("client_body_limit");
                    String defaultFile = (String) routeMap.get("default_file");
                    String redirect = (String) routeMap.get("redirect");
                    List<String> cgiExtensions = (List<String>) routeMap.get("cgi_extensions");

                    RouteConfig routeConfig = new RouteConfig(path, root, defaultFile, methods, directoryListing,
                            clientBodyLimitRoute, cgiExtensions, redirect);

                    routes.add(routeConfig);

                }
            }
            ServerConfig config = new ServerConfig(host, ports, serverName, errorPages, clientBodyLimit, routes);

            parsedServers.add(config);
        }
        return parsedServers;
    }
}