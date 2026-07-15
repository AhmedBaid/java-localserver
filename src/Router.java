import java.util.List;

import utils.RouteConfig;

public class Router {

    public static RouteConfig matchRoute(String requestPath, List<RouteConfig> routes) {
        RouteConfig bestMatch = null;
        int longestMatch = -1;

        for (RouteConfig route : routes) {
            String routePath = route.getPath();

            // Check if the request path starts with the route path
            if (requestPath.startsWith(routePath)) {
                if (routePath.length() > longestMatch) {
                    longestMatch = routePath.length();
                    bestMatch = route;
                }
            }
        }

        return bestMatch;
    }
}