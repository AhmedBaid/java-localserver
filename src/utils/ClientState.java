package utils;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

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
}