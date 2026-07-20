import java.util.List;
import utils.ServerConfig;

public class Main {
    public static void main(String[] args) {
        ConfigLoader configLoader = new ConfigLoader("config.json");
        configLoader.GlobalFormat();
        try {
            List<ServerConfig> serverConfig = configLoader.getServers();
            new Server(serverConfig).start();
        } catch (Exception e) {
            System.out.println("Error loading server configuration: " + e.getMessage());
            System.exit(1);
        }
    }
}