package utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class ClientState {
    public boolean useDisk = false;
    public ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    public HttpRequest request = null;
    public RouteConfig matchedRoute = null;
    public Session session = null;
    public boolean isHeadersParsed = false;
    public boolean isRequestComplete = false;
    public Path tempFilePath = null;
    public FileChannel fileChannel = null;
    public boolean isError = false;
    public int headerLength = 0;
    public ByteBuffer responseBuffer = null;
    public int errorCode = 0;
    public String errorMessage = "";
    public long lastActivityMillis = System.currentTimeMillis();
    public long bytesWritten = 0;
    public boolean cgiPending = false;
}