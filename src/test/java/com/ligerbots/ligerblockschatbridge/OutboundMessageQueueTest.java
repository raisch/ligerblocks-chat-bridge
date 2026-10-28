package com.ligerbots.ligerblockschatbridge;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OutboundMessageQueueTest {
    @Test
    void rejectsMessagesWhenQueueIsStopped() {
        OutboundMessageQueue queue = new OutboundMessageQueue(1, (channel, text) -> {
        });

        assertFalse(queue.enqueue(new OutboundMessage("C123", "message")));
        assertEquals(0, queue.size());
    }

    @Test
    void sendsAsynchronouslyAndStopsCleanly() throws InterruptedException {
        AtomicInteger sends = new AtomicInteger();
        OutboundMessageQueue queue = new OutboundMessageQueue(1, (channel, text) -> sends.incrementAndGet());
        queue.start();

        assertTrue(queue.enqueue(new OutboundMessage("C123", "message")));
        for (int attempt = 0; attempt < 20 && sends.get() == 0; attempt++) {
            Thread.sleep(10);
        }

        queue.close();
        assertEquals(1, sends.get());
    }

    @Test
    void sendsEphemeralResponsesToTheSlashCommandResponseUrl() throws InterruptedException {
        AtomicReference<String> responseUrl = new AtomicReference<>();
        AtomicReference<String> responseText = new AtomicReference<>();
        SlackMessageClient client = new SlackMessageClient() {
            @Override
            public void sendMessage(String channelId, String text) {
                throw new AssertionError("ephemeral response must not use channel delivery");
            }

            @Override
            public void sendEphemeralResponse(String url, String text) {
                responseUrl.set(url);
                responseText.set(text);
            }
        };
        OutboundMessageQueue queue = new OutboundMessageQueue(1, client);
        queue.start();

        assertTrue(queue.enqueue(OutboundMessage.ephemeral(
                "https://hooks.slack.com/commands/example",
                "Command completed")));
        for (int attempt = 0; attempt < 20 && responseText.get() == null; attempt++) {
            Thread.sleep(10);
        }

        queue.close();
        assertEquals("https://hooks.slack.com/commands/example", responseUrl.get());
        assertEquals("Command completed", responseText.get());
    }

    @Test
    void rejectsMessagesWhenCapacityIsReached() throws InterruptedException {
        CountDownLatch deliveryStarted = new CountDownLatch(1);
        CountDownLatch releaseDelivery = new CountDownLatch(1);
        OutboundMessageQueue queue = new OutboundMessageQueue(1, (channel, text) -> {
            deliveryStarted.countDown();
            releaseDelivery.await();
        });
        queue.start();

        assertTrue(queue.enqueue(new OutboundMessage("C123", "in-flight")));
        deliveryStarted.await();
        assertTrue(queue.enqueue(new OutboundMessage("C123", "queued")));
        assertFalse(queue.enqueue(new OutboundMessage("C123", "rejected")));

        releaseDelivery.countDown();
        queue.close();
    }

    @Test
    void exposesRateLimitRetryDelay() {
        SlackApiException exception = new SlackApiException("rate limited", 1_500);

        assertEquals(1_500, exception.retryAfterMillis());
    }

    @Test
    void retriesTransientSlackFailuresWithBoundedAttempts() {
        AtomicInteger sends = new AtomicInteger();
        OutboundMessageQueue queue = new OutboundMessageQueue(1, (channel, text) -> {
            if (sends.getAndIncrement() < 2) {
                throw new SlackApiException("temporary failure", 0);
            }
        });
        queue.start();
        assertTrue(queue.enqueue(new OutboundMessage("C123", "message")));

        for (int attempt = 0; attempt < 400 && sends.get() < 3; attempt++) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        queue.close();
        assertEquals(3, sends.get());
    }

    @Test
    void deliversFinalMessageBeforeClosing() {
        AtomicInteger sends = new AtomicInteger();
        OutboundMessageQueue queue = new OutboundMessageQueue(1, (channel, text) -> sends.incrementAndGet());
        queue.start();

        queue.closeWithFinalMessage(new OutboundMessage("C123", "server stopping"));

        assertEquals(1, sends.get());
    }
}
