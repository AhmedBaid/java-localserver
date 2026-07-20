package utils;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.channels.SelectionKey;

/**
 * Tracks one in-flight CGI process across multiple event loop ticks.
 */
public class CgiState {

    public final Process process;
    public final InputStream stdout;
    public final ByteArrayOutputStream outputBuffer;
    public final long deadlineMillis;
    public final SelectionKey key;
    public final ClientState clientState;
    public final File stdinSourceFile;
    public final boolean ownsStdinFile;

    public CgiState(Process process, InputStream stdout, long deadlineMillis,
            SelectionKey key, ClientState clientState,
            File stdinSourceFile, boolean ownsStdinFile) {
        this.process = process;
        this.stdout = stdout;
        this.outputBuffer = new ByteArrayOutputStream();
        this.deadlineMillis = deadlineMillis;
        this.key = key;
        this.clientState = clientState;
        this.stdinSourceFile = stdinSourceFile;
        this.ownsStdinFile = ownsStdinFile;
    }
}