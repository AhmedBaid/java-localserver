import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;

import utils.HttpRequest;
import utils.HttpResponse;
import utils.RequestParser;
import utils.ResponseBuilder;
import utils.RouteConfig;
import utils.ServerConfig;

public class Server {
    private final List<ServerConfig> serverConfigs;
    private List<RouteConfig> routeConfigs;
    private Selector selector;

    public Server(List<ServerConfig> serverConfigs) {
        this.serverConfigs = serverConfigs;
        this.routeConfigs = new java.util.ArrayList<>();
    }

    public void start() throws IOException {
        // 1. Open the grand traffic controller
        this.selector = Selector.open();

        // 2. Loop through every server block in your config
        for (ServerConfig config : serverConfigs) {
            this.routeConfigs.addAll(config.getRoutes());
            String host = config.getHost();

            for (int port : config.getPorts()) {

                try {
                    ServerSocketChannel serverChannel = ServerSocketChannel.open();

                    serverChannel.configureBlocking(false);

                    serverChannel.bind(new InetSocketAddress(host, port));

                    serverChannel.register(selector, SelectionKey.OP_ACCEPT);

                    System.out.println("Started virtual server on " + host + ":" + port);
                } catch (Exception e) {
                    System.err.println("Failed to start virtual server on " + host + ":" + port);
                    continue;
                }
            }
        }
        System.out.println("All ports bound. Entering the event loop...");
        runEventLoop();
    }

    private void runEventLoop() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        // This is your single thread holding the entire server alive
        while (true) {

            // This method blocks until at least one event (red flag) happens on a channel
            selector.select();

            // Get the list of channels that are ready for action
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();

                // CRITICAL: You must remove the key from the iterator,
                // otherwise the selector will try to process the same event again on the next
                // loop!
                keys.remove();

                if (!key.isValid()) {
                    continue;
                }

                // Check what kind of event woke us up
                if (key.isAcceptable()) {
                    // A new browser is knocking on the door
                    acceptConnection(key);
                } else if (key.isReadable()) {
                    // A browser is sending us an HTTP request (GET, POST, etc.)
                    readRequest(key, buffer);
                } else if (key.isWritable()) {
                    // We are ready to send our HTTP response back to the browser
                    sendResponse(key);
                }
            }
        }
    }

    private void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
        System.out.println("Accepted new connection from: " + client.getRemoteAddress());
    }

    private void readRequest(SelectionKey key, ByteBuffer buffer) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        buffer.clear();
        int bytesRead = client.read(buffer);

        if (bytesRead == -1) {
            client.close();
            return;
        }

        buffer.flip();
        byte[] data = new byte[buffer.limit()];
        buffer.get(data);
        String requestString = new String(data).trim();

        System.out.println("Received:\n" + requestString);
        HttpRequest httpRequest = RequestParser.parseRequest(data);
        RouteConfig matchedRoute = Router.matchRoute(httpRequest.getPath(), routeConfigs);
        HttpResponse response = ResponseBuilder.build(httpRequest, matchedRoute);

        // HttpRequestParser.parseRequest(client, requestString);
        ByteBuffer responseBuffer = response.toByteBuffer();
        key.attach(responseBuffer);

        key.interestOps(SelectionKey.OP_WRITE);
    }

    private void sendResponse(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        client.write(buffer);

        if (!buffer.hasRemaining()) {
            buffer.clear();
            key.interestOps(SelectionKey.OP_READ);
        }
    }
}