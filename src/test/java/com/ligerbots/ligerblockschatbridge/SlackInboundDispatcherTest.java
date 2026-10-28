package com.ligerbots.ligerblockschatbridge;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlackInboundDispatcherTest {
    @Test
    void schedulesBroadcastOnTheProvidedServerExecutor() {
        List<Runnable> scheduled = new ArrayList<>();
        List<String> broadcasts = new ArrayList<>();
        SlackInboundDispatcher dispatcher = new SlackInboundDispatcher(
                "C123", scheduled::add, broadcasts::add, () -> true);

        dispatcher.dispatch(new InboundSlackMessage("C123", "hello", "U123"));

        assertTrue(broadcasts.isEmpty());
        assertEquals(1, scheduled.size());
        scheduled.getFirst().run();
        assertEquals(List.of("[Slack] <U123> hello"), broadcasts);
    }

    @Test
    void rejectsMessagesForAnotherChannelBeforeScheduling() {
        List<Runnable> scheduled = new ArrayList<>();
        SlackInboundDispatcher dispatcher = new SlackInboundDispatcher(
                "C123", scheduled::add, ignored -> {
                }, () -> true);

        dispatcher.dispatch(new InboundSlackMessage("C999", "hello", "U123"));

        assertTrue(scheduled.isEmpty());
    }

    @Test
    void doesNotScheduleWhenInboundRelayIsDisabled() {
        List<Runnable> scheduled = new ArrayList<>();
        SlackInboundDispatcher dispatcher = new SlackInboundDispatcher(
                "C123", scheduled::add, ignored -> {
                }, () -> false);

        dispatcher.dispatch(new InboundSlackMessage("C123", "hello", "U123"));

        assertTrue(scheduled.isEmpty());
    }
}
