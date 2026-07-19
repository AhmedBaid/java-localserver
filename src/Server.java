import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import utils.ClientState;
import utils.HttpRequest;
import utils.HttpResponse;
import utils.RequestParser;
import utils.ResponseBuilder;
import utils.RouteConfig;
import utils.ServerConfig;
import utils.Session;

public class Server {
    private final List<ServerConfig> serverConfigs;
    private List<RouteConfig> routeConfigs;
    private Selector selector;
    private static final int Reqlimit = 5242880; // 5MB
    private static final long IDLE_TIMEOUT_MILLIS = 30000;

    public Server(List<ServerConfig> serverConfigs) {
        this.serverConfigs = serverConfigs;
        this.routeConfigs = new java.util.ArrayList<>();
    }

    public void start() throws IOException {
        this.selector = Selector.open();

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
                    System.err
                            .println("Failed to start virtual server on " + host + ":" + port + " - " + e.getMessage());
                    continue;
                }
            }
        }
        System.out.println("All ports bound. Entering the event loop...");
        runEventLoop();
    }

    private void runEventLoop() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(1024);

        while (true) {

            selector.select(1000);

            closeIdleConnections(selector);

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                if (!key.isValid()) {
                    continue;
                }
                try {
                    if (key.isAcceptable()) {
                        acceptConnection(key);
                    } else if (key.isReadable()) {
                        readRequest(key, buffer);
                    } else if (key.isWritable()) {
                        sendResponse(key);
                    }
                } catch (IOException e) {
                    System.err.println("Client disconnected abruptly: " + e.getMessage());
                    ClientState state = (ClientState) key.attachment();
                    if (state != null) {
                        try {
                            if (state.fileChannel != null)
                                state.fileChannel.close();
                        } catch (Exception ex) {
                        }

                        if (state.tempFile != null && state.tempFile.exists()) {
                            state.tempFile.delete();
                        }
                    }
                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException ex) {
                    }
                }
            }

        }
    }

    private void closeIdleConnections(Selector s) {
        Long now = System.currentTimeMillis();
        try {
            Session.cleanupExpired();
        } catch (Exception ignored) {
        }

        for (SelectionKey key : s.keys()) {
            if (!key.isValid() || !(key.channel() instanceof SocketChannel)) {
                continue;
            }

            ClientState state = (ClientState) key.attachment();
            if (state != null) {
                if (now - state.lastActivityMillis > IDLE_TIMEOUT_MILLIS) {
                    System.out.println("Closing idle connection due to timeout...");
                    try {
                        if (state.fileChannel != null) {
                            state.fileChannel.close();
                        }
                    } catch (Exception e) {
                    }

                    if (state.tempFile != null && state.tempFile.exists()) {
                        state.tempFile.delete();
                    }

                    key.cancel();
                    try {
                        key.channel().close();
                    } catch (IOException ignored) {
                    }
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
        ClientState state = (ClientState) key.attachment();
        if (state == null) {
            state = new ClientState();
            key.attach(state);
        }

        buffer.clear();
        int bytesRead = client.read(buffer);

        if (bytesRead == -1) {
            client.close();
            return;
        }
        if (state != null) {
            state.lastActivityMillis = System.currentTimeMillis();
        }

        buffer.flip();

        if (!state.isHeadersParsed) {
            byte[] chunk = new byte[buffer.limit()];
            buffer.get(chunk);
            state.buffer.write(chunk);
            byte[] dataSoFar = state.buffer.toByteArray();

            int headerEnd = RequestParser.findHeaderEndIndex(dataSoFar);

            if (headerEnd != -1) {
                state.headerLength = headerEnd;
                HttpRequest parsedReq = RequestParser.parseRequest(dataSoFar);

                if (parsedReq != null) {
                    state.request = parsedReq;
                    state.isHeadersParsed = true;
                    state.matchedRoute = Router.matchRoute(state.request.getPath(), routeConfigs);
                    checkBodyLimit(state);

                    if (!state.isError) {
                        java.io.File tempDir = new java.io.File("./temp_uploads");
                        if (!tempDir.exists()) {
                            tempDir.mkdirs();
                        }
                        state.tempFile = java.io.File.createTempFile("upload_", ".tmp", tempDir);
                        state.fileChannel = new java.io.FileOutputStream(state.tempFile).getChannel();
                        state.tempFile.deleteOnExit();
                        int bodyPartLength = dataSoFar.length - headerEnd;
                        if (bodyPartLength > 0) {
                            ByteBuffer bodyPart = ByteBuffer.wrap(dataSoFar, headerEnd, bodyPartLength);
                            state.fileChannel.write(bodyPart);
                            state.bytesWritten += bodyPartLength;
                        }

                        state.buffer.reset();
                    }
                } else {
                    state.isError = true;
                    state.errorCode = 400;
                    state.errorMessage = "Bad Request";
                    state.isHeadersParsed = true;
                    state.isRequestComplete = true;
                }
            }
        } else {
            if (!state.isError && state.fileChannel != null) {
                state.fileChannel.write(buffer);
                state.bytesWritten += bytesRead;
            }
        }

        if (state.isHeadersParsed && !state.isRequestComplete) {
            checkIfBodyIsComplete(state);
        }

        if (state.isRequestComplete) {
            HttpResponse response;

            if (state.isError) {
                Map<String, String> errorPages = state.matchedRoute != null ? state.matchedRoute.getErrorPages() : null;
                response = ResponseBuilder.buildErrorResponse(state.errorCode, state.errorMessage, errorPages);
            } else {
                state.request.addHeader("Temp-File-Path", state.tempFile.getAbsolutePath());
                response = ResponseBuilder.build(state.request, state.matchedRoute);
            }

            // --- session handling ---
            if (state.request != null) {
                state.session = Session.fromRequest(state.request);
                if (state.session == null) {
                    state.session = Session.create();
                }
                response.addSetCookie(state.session.toCookie());
            }

            state.responseBuffer = response.toByteBuffer();
            key.interestOps(SelectionKey.OP_WRITE);
        }
    }

    private void sendResponse(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ClientState state = (ClientState) key.attachment();

        if (state.responseBuffer != null) {
            client.write(state.responseBuffer);
            state.lastActivityMillis = System.currentTimeMillis();
            if (!state.responseBuffer.hasRemaining()) {
                key.attach(new ClientState());
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void checkBodyLimit(ClientState state) {
        String contentLengthStr = state.request.getHeader("Content-Length");

        if (contentLengthStr != null) {
            long contentLength = Long.parseLong(contentLengthStr);
            long limit = Reqlimit;
            if (state.matchedRoute != null) {
                limit = state.matchedRoute.getClientBodyLimit() == null ? Reqlimit
                        : state.matchedRoute.getClientBodyLimit();
            }
            if (contentLength > limit) {
                state.isError = true;
                state.errorCode = 413;
                state.errorMessage = "Payload Too Large";
                state.isRequestComplete = true;
            }
        }
    }

    private void checkIfBodyIsComplete(ClientState state) {
        if (state.isError)
            return;

        String contentLengthStr = state.request.getHeader("Content-Length");
        if (contentLengthStr != null) {
            long expectedBodySize = Long.parseLong(contentLengthStr);

            if (state.bytesWritten >= expectedBodySize) {
                state.isRequestComplete = true;

                try {
                    if (state.fileChannel != null) {
                        state.fileChannel.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else if ("chunked".equals(state.request.getHeader("Transfer-Encoding"))) {

            state.isRequestComplete = true;
            try {
                if (state.fileChannel != null)
                    state.fileChannel.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            state.isRequestComplete = true;
        }
    }

}