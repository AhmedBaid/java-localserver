package utils;

import java.io.File;

public interface CgiExecutor {
    HttpResponse handle(File scriptFile, String queryString) throws Exception;
}