// Copyright 2015-2018 The NATS Authors
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package io.nats.connector.plugins.test;

import io.nats.client.Connection;
import io.nats.client.ConnectionFactory;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.connector.plugin.NATSConnector;
import io.nats.connector.plugin.NATSConnectorPlugin;
import io.nats.connector.plugin.NATSEvent;

import org.slf4j.Logger;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NATSTestPlugin implements NATSConnectorPlugin {
    NATSConnector connector = null;
    Logger logger = null;

    public NATSTestPlugin() {}

    class PeriodicSender implements Runnable {
        @Override
        public void run() {
            String s;
            int count = 2;

            // an override for debugging
            String countStr = System.getProperty("test.msgcount");
            if (countStr != null && countStr.isEmpty() == false) {
                count = Integer.parseInt(countStr);
            }

            Message m = new Message();

            m.setSubject("foo");
            m.setReplyTo("bar");

            for (int i = 0; i < count; i++) {
                s = new String("Message-" + Integer.toString(i));

                byte[] payload = s.getBytes();

                m.setData(payload, 0, payload.length);

                connector.publish(m);

                try {
                    connector.flush();
                } catch (Exception e) {
                    logger.error("Error with flush:  ", e);
                }

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                }
            }

            // test the shutdown command.
            connector.shutdown();
            logger.info("Shutdown the NATS connect from the plugin.");
        }
    }

    @Override
    public boolean onStartup(Logger logger, ConnectionFactory factory) {
        this.logger = logger;
        return true;
    }

    private void testConnectorAPIs() throws Exception {
        MessageHandler mh = new MessageHandler() {
            @Override
            public void onMessage(Message message) {
                ;;
            }
        };

        // test various combinatitions of subscribes and unsubscribes.
        connector.subscribe("foo1");
        connector.subscribe("foo1");

        connector.subscribe("foo2", mh);
        connector.subscribe("foo2", mh);

        connector.subscribe("foo3", "qg1");
        connector.subscribe("foo3", "qg1");

        connector.subscribe("foo4", "qg1", mh);
        connector.unsubscribe("foo1");
        connector.unsubscribe("foo2");
        connector.unsubscribe("foo3");

        connector.unsubscribe("foo4");
        connector.unsubscribe("foo4");

        connector.unsubscribe("unknown");
        connector.unsubscribe(null);

        connector.flush();

        Connection c = connector.getConnection();
        if (c == null)
            throw new Exception("Expected a valid connection.");

        ConnectionFactory cf = connector.getConnectionFactory();
        if (cf == null)
            throw new Exception("Expected a valid connection.");
    }

    @Override
    public boolean onNatsInitialized(NATSConnector connector) {
        this.connector = connector;

        try {
            testConnectorAPIs();
        } catch (Exception ex) {
            return false;
        }

        logger.info("Starting up.");

        try {
            connector.subscribe("foo");
        } catch (Exception ex) {
            logger.error("Unable to subscribe: ", ex);
            return false;
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(new PeriodicSender());

        return true;
    }

    @Override
    public void onShutdown() {
        connector.unsubscribe("foo");
        logger.info("Shutting down.");
    }

    @Override
    public void onNATSMessage(io.nats.client.Message msg) {

        logger.info("Received message: " + msg.toString());

        msg.setSubject("baz");

        byte[] reply = "reply".getBytes();

        msg.setData(reply, 0, (int) reply.length);

        connector.publish(msg);

        try {
            connector.flush();
            logger.info("Flushed.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onNATSEvent(NATSEvent event, String message) {
        switch (event) {
            case ASYNC_ERROR:
                logger.error("NATS Event Async error: " + message);
                break;
            case RECONNECTED:
                logger.info("NATS Event Reconnected: " + message);
                break;
            case DISCONNECTED:
                logger.info("NATS Event Disconnected: " + message);
                break;
            case CLOSED:
                logger.info("NATS Event Closed: " + message);
                break;
            default:
                logger.info("NATS Event Unrecognized event: " + message);
        }

        // throw exceptions to ensure the framework handles them.
        throw new RuntimeException("Test framework plugin exception handling.");
    }
}
