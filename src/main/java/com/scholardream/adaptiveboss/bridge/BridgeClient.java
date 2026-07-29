package com.scholardream.adaptiveboss.bridge;

import com.scholardream.adaptiveboss.AdaptiveBossMod;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Local TCP client for the Python decision service (NDJSON: one JSON object
 * per line, request and reply).
 *
 * <p>Threading contract: ALL socket IO happens on a single daemon background
 * thread. The game thread only hands off request strings through a bounded
 * queue and waits (bounded by {@code timeoutMs}) on a {@link CompletableFuture};
 * availability and the last reply are plain volatile reads. A slow or dead
 * Python side can therefore never freeze the server — worst case it costs one
 * degraded decision and an automatic reconnect.
 *
 * <p>Reconnect uses exponential backoff (1 s → 2 s → … → 30 s cap). Every
 * degrade / recovery transition is logged.
 */
public class BridgeClient implements AutoCloseable {
    private static final int CONNECT_TIMEOUT_MS = 2000;
    private static final int INITIAL_BACKOFF_MS = 1000;
    private static final int MAX_BACKOFF_MS = 30_000;

    private final String host;
    private final int port;
    private final int timeoutMs;

    private final BlockingQueue<PendingRequest> outbox = new ArrayBlockingQueue<>(8);
    private final AtomicReference<Socket> socketRef = new AtomicReference<>();

    private volatile boolean available = false;
    private volatile boolean running = false;
    private Thread thread;

    private record PendingRequest(String json, CompletableFuture<String> reply) {
    }

    public BridgeClient(String host, int port, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    public void start() {
        if (running) {
            return;
        }
        running = true;
        thread = new Thread(this::runLoop, "AdaptiveBoss-BridgeClient");
        thread.setDaemon(true);
        thread.start();
    }

    public boolean isAvailable() {
        return available;
    }

    /**
     * Non-blocking handoff of one state line. Returns {@code null} when the
     * bridge is down or the outbox is full — the caller degrades immediately.
     */
    public CompletableFuture<String> submit(String jsonLine) {
        if (!available) {
            return null;
        }
        PendingRequest request = new PendingRequest(jsonLine, new CompletableFuture<>());
        if (!outbox.offer(request)) {
            return null;
        }
        return request.reply();
    }

    /**
     * Called when the game thread gave up waiting: the socket state can no
     * longer be trusted, so drop it and let the background thread reconnect.
     */
    public void dropConnection(String why) {
        setAvailable(false, why);
        Socket socket = socketRef.get();
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    @Override
    public void close() {
        running = false;
        dropConnection("client stopped");
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void runLoop() {
        int backoffMs = INITIAL_BACKOFF_MS;
        while (running) {
            try (Socket socket = new Socket()) {
                socketRef.set(socket);
                socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
                socket.setTcpNoDelay(true);
                socket.setSoTimeout(timeoutMs);

                BufferedWriter writer = new BufferedWriter(
                        new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

                setAvailable(true, null);
                backoffMs = INITIAL_BACKOFF_MS;

                while (running) {
                    PendingRequest request = outbox.poll(1, TimeUnit.SECONDS);
                    if (request == null) {
                        continue; // idle heartbeat: re-check running
                    }
                    writer.write(request.json());
                    writer.write('\n');
                    writer.flush();
                    String line = reader.readLine(); // SocketTimeoutException after timeoutMs
                    if (line == null) {
                        throw new IOException("python side closed the connection");
                    }
                    request.reply().complete(line);
                }
            } catch (Exception e) {
                if (running) {
                    setAvailable(false, String.valueOf(e.getMessage()));
                    drainOutbox();
                    AdaptiveBossMod.LOGGER.info("[AdaptiveBoss] decision bridge reconnecting in {}s", backoffMs / 1000);
                    try {
                        Thread.sleep(backoffMs);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                    backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
                }
            } finally {
                socketRef.set(null);
            }
        }
        available = false;
        drainOutbox();
    }

    private void drainOutbox() {
        PendingRequest request;
        while ((request = outbox.poll()) != null) {
            request.reply().complete(null);
        }
    }

    /** Logs exactly once per degrade / recovery transition. */
    private void setAvailable(boolean value, String why) {
        if (available == value) {
            return;
        }
        available = value;
        if (value) {
            AdaptiveBossMod.LOGGER.info(
                    "[AdaptiveBoss] decision bridge connected ({}:{}) — AI policy online", host, port);
        } else {
            AdaptiveBossMod.LOGGER.warn(
                    "[AdaptiveBoss] decision bridge unavailable ({}) — degrading to RandomPolicy", why);
        }
    }
}
