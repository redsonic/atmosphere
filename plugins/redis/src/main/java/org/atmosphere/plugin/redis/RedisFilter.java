/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 *
 */
package org.atmosphere.plugin.redis;

import org.atmosphere.cpr.BroadcastFilter;
import org.atmosphere.cpr.Broadcaster;
import org.atmosphere.cpr.ClusterBroadcastFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Support for Redis
 *
 * @author Jeanfrancois Arcand
 */
public class RedisFilter implements ClusterBroadcastFilter {

    private static final Logger logger = LoggerFactory.getLogger(RedisFilter.class);

    private Broadcaster bc;
    private final ExecutorService listener = Executors.newSingleThreadExecutor();
    private final ConcurrentLinkedQueue<String> receivedMessages = new ConcurrentLinkedQueue<String>();
    private Jedis jedisSubscriber;
    private Jedis jedisPublisher;
    private URI uri;
    private String auth = "atmosphere";

    public RedisFilter() {
        this(RedisFilter.class.getSimpleName(), URI.create("http://localhost:6379"));
    }

    public RedisFilter(String id) {
        this(id, URI.create("http://localhost:6379"));
    }

    public RedisFilter(URI uri) {
        this(RedisFilter.class.getSimpleName(), uri);
    }

    public RedisFilter(String id, URI uri) {
        this.uri = uri;
    }

    public RedisFilter(Broadcaster bc, String address) {

        this.bc = bc;
        uri = URI.create(address);

        if (uri == null) return;

        jedisSubscriber = new Jedis(uri.getHost(), uri.getPort());
        try {
            jedisSubscriber.connect();
        } catch (IOException e) {
            logger.error("failed to connect to subscriber: " + jedisSubscriber, e);
        }

        jedisSubscriber.auth(auth);
        jedisSubscriber.flushAll();

        jedisPublisher = new Jedis(uri.getHost(), uri.getPort());
        try {
            jedisPublisher.connect();
        } catch (IOException e) {
            logger.error("failed to connect to publisher: " + jedisPublisher, e);
        }
        jedisPublisher.auth(auth);
        jedisPublisher.flushAll();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setUri(String address) {
        uri = URI.create(address);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init() {
        logger.info("Starting Atmosphere Redis Clustering support");

        final Broadcaster broadcaster = bc;
        listener.submit(new Runnable() {
            public void run() {
                jedisSubscriber.subscribe(new JedisPubSub() {
                    public void onMessage(String channel, String message) {
                        receivedMessages.offer(message);
                        broadcaster.broadcast(message);
                    }

                    public void onSubscribe(String channel, int subscribedChannels) {
                        logger.debug("onSubscribe(): channel: {}", channel);
                    }

                    public void onUnsubscribe(String channel, int subscribedChannels) {
                        logger.debug("onUnsubscribe(): channel: {}", channel);
                    }

                    public void onPSubscribe(String pattern, int subscribedChannels) {
                        logger.debug("onPSubscribe(): pattern: {}", pattern);
                    }

                    public void onPUnsubscribe(String pattern, int subscribedChannels) {
                        logger.debug("onPUnsubscribe(): pattern: {}", pattern);
                    }

                    public void onPMessage(String pattern, String channel, String message) {
                        logger.debug("onPMessage: pattern: {}, channel: {}, message: {}",
                                new Object[]{pattern, channel, message});

                    }
                }, bc.getID());
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroy() {
        listener.shutdownNow();
        try {
            jedisPublisher.disconnect();
            jedisSubscriber.disconnect();
        }
        catch (IOException e) {
            logger.error("failure encountered during destroy", e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BroadcastFilter.BroadcastAction filter(Object originalMessage, Object o) {
        if (!(receivedMessages.remove(originalMessage.toString()))) {
            jedisPublisher.publish(bc.getID(), originalMessage.toString());
        }
        return new BroadcastFilter.BroadcastAction(BroadcastAction.ACTION.CONTINUE, o);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Broadcaster getBroadcaster() {
        return bc;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBroadcaster(Broadcaster bc) {
        this.bc = bc;
    }
}
