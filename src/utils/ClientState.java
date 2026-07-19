package utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class ClientState {
    public ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    public HttpRequest request = null;
    public RouteConfig matchedRoute = null;
    public Session session = null;
    public boolean isHeadersParsed = false;
    public boolean isRequestComplete = false;
    public boolean isError = false;
    public int headerLength = 0;
    public ByteBuffer responseBuffer = null;
    public int errorCode = 0;
    public String errorMessage = "";
public long lastActivityMillis = System.currentTimeMillis();
    public File tempFile = null;
    public FileChannel fileChannel = null;
    public long bytesWritten = 0;
}