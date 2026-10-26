package com.ligerbots.ligerblockschatbridge;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

final class OutboundMessageQueue implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(OutboundMessageQueue.class.getName());
    private static final long MAX_RETRY_WAIT_MILLIS = 60_000;
    private final ArrayBlockingQueue<OutboundMessage> queue;
    private final SlackMessageClient client;
    private volatile boolean running;
    private Thread worker;

    OutboundMessageQueue(int capacity, SlackMessageClient client) {
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.client = client;
    }

    synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        worker = new Thread(this::runWorker, "ligerblocks-slack-sender");
        worker.setDaemon(true);
        worker.start();
    }

    boolean enqueue(OutboundMessage message) {
        if (!running || !queue.offer(message)) {
            LOGGER.log(System.Logger.Level.WARNING, "Dropping outbound Slack message because the relay queue is "
                    + (running ? "full" : "stopped"));
            return false;
        }
        return true;
    }

    int size() {
        return queue.size();
    }

    private void runWorker() {
        while (running || !queue.isEmpty()) {
            try {
                OutboundMessage message = queue.poll(250, TimeUnit.MILLISECONDS);
                if (message == null) {
                    continue;
                }
                deliver(message);
            } catch (InterruptedException interrupted) {
                if (!running) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void deliver(OutboundMessage message) {
        try {
            client.sendMessage(message.channelId(), message.text());
        } catch (SlackApiException apiFailure) {
            long retryAfter = Math.min(apiFailure.retryAfterMillis(), MAX_RETRY_WAIT_MILLIS);
            LOGGER.log(System.Logger.Level.ERROR, "Slack delivery failed: " + apiFailure.getMessage());
            if (retryAfter > 0) {
                try {
                    Thread.sleep(retryAfter);
                    client.sendMessage(message.channelId(), message.text());
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                } catch (IOException | RuntimeException retryFailure) {
                    LOGGER.log(System.Logger.Level.ERROR, "Slack delivery retry failed: " + retryFailure.getMessage());
                }
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.log(System.Logger.Level.ERROR, "Slack delivery failed: " + failure.getMessage());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(5_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
        queue.clear();
    }
}
