package utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MultipartHandler {

    public static List<String> extractUploadedFiles(HttpRequest request, String destinationDirectory) {
        List<String> uploadedFiles = new ArrayList<>();

        String tempFilePathStr = request.getHeader("Temp-File-Path");
        if (tempFilePathStr == null)
            return uploadedFiles;

        Path tempFilePath = Paths.get(tempFilePathStr);
        if (!Files.exists(tempFilePath))
            return uploadedFiles;

        String contentType = request.getHeader("Content-Type");
        if (contentType == null || !contentType.contains("boundary="))
            return uploadedFiles;

        String boundaryStr = "--" + contentType.split("boundary=")[1];
        byte[] boundary = boundaryStr.getBytes();
        byte[] nextBoundary = ("\r\n" + boundaryStr).getBytes();
        byte[] doubleCrLf = "\r\n\r\n".getBytes();

        try (FileChannel sourceChannel = FileChannel.open(tempFilePath, StandardOpenOption.READ)) {
            long pos = findBytes(sourceChannel, boundary, 0);
            if (pos == -1)
                return uploadedFiles;

            pos += boundary.length;

            while (true) {
                ByteBuffer checkBuf = ByteBuffer.allocate(2);
                sourceChannel.position(pos);
                int read = sourceChannel.read(checkBuf);
                if (read < 2)
                    break;

                if (checkBuf.array()[0] == '-' && checkBuf.array()[1] == '-') {
                    break;
                }
                pos += 2;

                long headerEndPos = findBytes(sourceChannel, doubleCrLf, pos);
                if (headerEndPos == -1)
                    break;

                int headerSize = (int) (headerEndPos - pos);
                ByteBuffer headerBuf = ByteBuffer.allocate(headerSize);
                sourceChannel.position(pos);
                sourceChannel.read(headerBuf);
                String headers = new String(headerBuf.array());

                String originalFileName = extractFilename(headers);

                long dataStart = headerEndPos + 4;

                long nextBoundPos = findBytes(sourceChannel, nextBoundary, dataStart);
                if (nextBoundPos == -1)
                    break;

                long dataSize = nextBoundPos - dataStart;

                if (originalFileName != null && !originalFileName.isEmpty()) {
                    String uniqueFileName = UUID.randomUUID().toString() + "_" + originalFileName;
                    Path finalPath = Paths.get(destinationDirectory, uniqueFileName);
                    
                    Path partTemp = Files.createTempFile(
                            Paths.get(destinationDirectory), "part_", ".tmp");
                    try (FileChannel destChannel = FileChannel.open(partTemp,
                            StandardOpenOption.WRITE)) {
                        long remaining = dataSize;
                        long currentPos = dataStart;
                        while (remaining > 0) {
                            long transferred = sourceChannel.transferTo(currentPos, remaining, destChannel);
                            if (transferred == 0) break;
                            currentPos += transferred;
                            remaining -= transferred;
                        }
                    }
                    try {
                        Files.move(partTemp, finalPath,
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        Files.move(partTemp, finalPath,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    System.out.println("Saved large file: " + uniqueFileName);
                    uploadedFiles.add(uniqueFileName);
                }

                pos = nextBoundPos + nextBoundary.length;
            }

        } catch (Exception e) {
            System.err.println("Upload Extraction Failed: " + e.getMessage());
        }

        return uploadedFiles;
    }

    private static String extractFilename(String headers) {
        if (headers.contains("filename=\"")) {
            return headers.split("filename=\"")[1].split("\"")[0];
        }
        return null;
    }

    private static long findBytes(FileChannel channel, byte[] pattern, long startPos) throws IOException {
        int bufferSize = 8192; // 8KB
        ByteBuffer buffer = ByteBuffer.allocate(bufferSize);
        long pos = startPos;
        int overlap = pattern.length - 1;

        while (true) {
            channel.position(pos);
            int bytesRead = channel.read(buffer);
            if (bytesRead < pattern.length)
                return -1;

            byte[] array = buffer.array();
            for (int i = 0; i <= bytesRead - pattern.length; i++) {
                boolean match = true;
                for (int j = 0; j < pattern.length; j++) {
                    if (array[i + j] != pattern[j]) {
                        match = false;
                        break;
                    }
                }
                if (match)
                    return pos + i;
            }
            if (bytesRead < bufferSize)
                return -1;
            pos += (bytesRead - overlap);
            buffer.clear();
        }
    }
}