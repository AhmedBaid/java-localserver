import java.util.List;

import utils.RouteConfig;

public class Router {

    public static RouteConfig matchRoute(String requestPath, List<RouteConfig> routes) {
        RouteConfig bestMatch = null;
        int longestMatch = -1;

        for (RouteConfig route : routes) {
            String routePath = route.getPath();
            System.out.println(">>>>>>>>>" + routePath);
            if (requestPath.startsWith(routePath)) {
                System.out.println("??????? request path: " + requestPath );
                System.out.println("??????? route path" + route);
                if (routePath.length() > longestMatch) {
                    longestMatch = routePath.length();
                    bestMatch = route;
                }
            }
        }

        return bestMatch;
    }
}