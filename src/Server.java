import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.List;
import utils.ServerConfig;

public class Server {
    private final List<ServerConfig> serverConfigs;
    private Selector selector;

    public Server(List<ServerConfig> serverConfigs) {
        this.serverConfigs = serverConfigs;
    }

    public void start() throws IOException {
        // 1. Open the grand traffic controller
        this.selector = Selector.open();

        // 2. Loop through every server block in your config
        for (ServerConfig config : serverConfigs) {
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

        // This is your single thread holding the entire server alive
        while (true) {
            selector.select(1000);
            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();

                keys.remove();

                if (!key.isValid()) {
                    continue;
                }

                if (key.isAcceptable()) {
                    acceptConnection(key);  
                } else if (key.isReadable()) {
                    // A browser is sending us an HTTP request (GET, POST, etc.)
                    readRequest(key, buffer);
                } else if (key.isWritable()) {
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

        // 1. استخراج السطر الأول من الطلب (مثال: GET / HTTP/1.1)
        String[] lines = requestString.split("\r\n");
        if (lines.length > 0) {
            String[] requestLine = lines[0].split(" ");
            // التأكد من أن السطر يحتوي على Method و Path و Protocol
            if (requestLine.length == 3) {
                String method = requestLine[0]; // GET
                String path = requestLine[1]; // / أو /about

                // 2. توجيه الطلب (Routing)
                handleRoute(client, path);
            }
        }
    }

    private void sendResponse(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        ByteBuffer buffer = (ByteBuffer) key.attachment();

        // Attempt to write the remaining bytes to the socket
        client.write(buffer);

        // Check if there is still data to be sent (e.g., if the socket buffer got full)
        if (!buffer.hasRemaining()) {
            buffer.clear();
            // Switch interest back to reading new requests from this client
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void handleRoute(SocketChannel client, String path) throws IOException {
        String responseBody = "";
        int statusCode = 200;
        String statusMessage = "OK";

        // تحديد المحتوى بناءً على المسار
        if (path.equals("/")) {
            // هنا يمكنك قراءة محتوى ملف index.html من المسار المحدد في config.json
            responseBody = "<html><body><h1>Welcome Home!</h1><p>This is the default index page.</p></body></html>";
        } else if (path.equals("/about")) {
            responseBody = "<html><body><h1>About Us</h1><p>ahmed's Custom Java Server</p></body></html>";
        } else {
            statusCode = 404;
            statusMessage = "Not Found";
            responseBody = "<html><body><h1>404 - Page Not Found</h1></body></html>";
        }

        // 3. بناء استجابة HTTP قياسية (HTTP Response Format)
        // يجب أن تفصل بـ \r\n بين الأسطر، وبسطرين فارغين \r\n\r\n قبل محتوى الـ Body
        String httpResponse = "HTTP/1.1 " + statusCode + " " + statusMessage + "\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: " + responseBody.getBytes().length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                responseBody;

        // إرسال الاستجابة للمتصفح
        ByteBuffer responseBuffer = ByteBuffer.wrap(httpResponse.getBytes());
        while (responseBuffer.hasRemaining()) {
            client.write(responseBuffer);
        }

        // إغلاق الاتصال بعد إرسال الرد
        client.close();
    }
}