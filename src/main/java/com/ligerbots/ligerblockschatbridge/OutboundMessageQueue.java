package com.ligerbots.ligerblockschatbridge;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

final class OutboundMessageQueue implements AutoCloseable {
    private static final System.Logger LOGGER = System.getLogger(OutboundMessageQueue.class.getName());
    private static final int MAX_DELIVERY_ATTEMPTS = 3;
    private static final long INITIAL_RETRY_WAIT_MILLIS = 1_000;
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
        LOGGER.log(System.Logger.Level.INFO, "Queued outbound Slack message to channel "
                + message.channelId() + ": " + message.text());
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
                if (!running && queue.isEmpty()) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void deliver(OutboundMessage message) {
        long retryWait = INITIAL_RETRY_WAIT_MILLIS;
        for (int attempt = 1; attempt <= MAX_DELIVERY_ATTEMPTS; attempt++) {
            try {
                if (message.isEphemeral()) {
                    client.sendEphemeralResponse(message.responseUrl(), message.text());
                    LOGGER.log(System.Logger.Level.INFO, "Sent ephemeral Slack command response"
                            + (attempt == 1 ? "" : " after retry") + ": " + message.text());
                } else {
                    client.sendMessage(message.channelId(), message.text());
                    LOGGER.log(System.Logger.Level.INFO, "Sent outbound Slack message to channel "
                            + message.channelId() + (attempt == 1 ? "" : " after retry") + ": " + message.text());
                }
                return;
            } catch (SlackApiException apiFailure) {
                LOGGER.log(System.Logger.Level.ERROR, "Slack delivery attempt " + attempt + " failed: "
                        + apiFailure.getMessage());
                retryWait = apiFailure.retryAfterMillis() > 0
                        ? Math.min(apiFailure.retryAfterMillis(), MAX_RETRY_WAIT_MILLIS)
                        : retryWait;
            } catch (IOException | RuntimeException failure) {
                LOGGER.log(System.Logger.Level.ERROR, "Slack delivery attempt " + attempt + " failed: "
                        + failure.getMessage());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }

            if (attempt < MAX_DELIVERY_ATTEMPTS) {
                try {
                    LOGGER.log(System.Logger.Level.WARNING, "Retrying Slack delivery in " + retryWait + " ms");
                    Thread.sleep(retryWait);
                    retryWait = Math.min(retryWait * 2, MAX_RETRY_WAIT_MILLIS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            } else {
                LOGGER.log(System.Logger.Level.ERROR, "Slack delivery abandoned after "
                        + MAX_DELIVERY_ATTEMPTS + " attempts");
            }
        }
    }

    @Override
    public synchronized void close() {
        running = false;
        queue.clear();
        stopWorker();
    }

    synchronized void closeWithFinalMessage(OutboundMessage message) {
        if (!running) {
            close();
            return;
        }
        queue.clear();
        running = false;
        stopWorker();
        Thread finalDelivery = new Thread(() -> deliver(message), "ligerblocks-slack-shutdown");
        finalDelivery.setDaemon(true);
        finalDelivery.start();
        try {
            finalDelivery.join(5_000);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        queue.clear();
    }

    private void stopWorker() {
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(5_000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
    }
}
