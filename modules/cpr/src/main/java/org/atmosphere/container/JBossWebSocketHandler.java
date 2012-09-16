/*
 * Copyright 2012 Péter Miklós
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.atmosphere.container;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.atmosphere.container.version.JBossWebSocket;
import org.atmosphere.cpr.AtmosphereConfig;
import org.atmosphere.cpr.AtmosphereRequest;
import org.atmosphere.cpr.WebSocketProcessorFactory;
import org.atmosphere.websocket.WebSocketProcessor;
import org.jboss.as.websockets.WebSocket;
import org.jboss.as.websockets.servlet.WebSocketServlet;
import org.jboss.websockets.Frame;
import org.jboss.websockets.frame.BinaryFrame;
import org.jboss.websockets.frame.CloseFrame;
import org.jboss.websockets.frame.TextFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dispatches JBoss websocket events to Atmosphere's {@link WebSocketProcessor}.
 * This websocket handler is based Mike Brock's websockets implementation.
 * 
 * @author Péter Miklós
 * @see https://github.com/mikebrock/jboss-websockets
 */
public class JBossWebSocketHandler extends WebSocketServlet {

    private static final long serialVersionUID = 1L;

    private static final Logger logger = LoggerFactory.getLogger(JBossWebSocketHandler.class);

    private static final String JBOSS_WEB_SOCKET_PROCESSOR = "jboss.webSocketProcessor";
    private final AtmosphereConfig config;

    public JBossWebSocketHandler(AtmosphereConfig config) {
        this.config = config;
    }

    @Override
    protected void onSocketOpened(WebSocket socket) throws IOException {
        logger.trace("WebSocket.onSocketOpened.");

        WebSocketProcessor processor = WebSocketProcessorFactory.getDefault().newWebSocketProcessor(
                new JBossWebSocket(socket, config));

        HttpServletRequest request = socket.getServletRequest();
        processor.open(AtmosphereRequest.wrap(request));
    }

    @Override
    protected void onSocketClosed(WebSocket socket) throws IOException {
        logger.trace("WebSocket.onSocketClosed.");

        if (getWebSocketProcessorFor(socket) != null) {
            getWebSocketProcessorFor(socket).close(0);
        }
    }

    @Override
    protected void onReceivedFrame(WebSocket socket) throws IOException {
        Frame frame = socket.readFrame();
        WebSocketProcessor webSocketProcessor = getWebSocketProcessorFor(socket);

        if (webSocketProcessor != null) {
            if (frame instanceof TextFrame) {
                logger.trace("WebSocket.onReceivedFrame (TextFrame)");
                webSocketProcessor.invokeWebSocketProtocol(((TextFrame) frame).getText());
            } else if (frame instanceof BinaryFrame) {
                logger.trace("WebSocket.onReceivedFrame (BinaryFrame)");
                BinaryFrame binaryFrame = (BinaryFrame) frame;
                webSocketProcessor.invokeWebSocketProtocol(binaryFrame.getByteArray(), 0,
                        binaryFrame.getByteArray().length);
            } else if (frame instanceof CloseFrame) {
                // TODO shall we call this here?
                logger.trace("WebSocket.onReceivedFrame (CloseFrame)");
                webSocketProcessor.close(0);
            } else {
                logger.trace("WebSocket.onReceivedFrame skipping: " + frame);
            }
        } else {
            logger.trace("WebSocket.onReceivedFrame but no atmosphere processor in request, skipping: " + frame);
        }
    }

    private WebSocketProcessor getWebSocketProcessorFor(WebSocket socket) {
        return (WebSocketProcessor) socket.getServletRequest().getAttribute(JBOSS_WEB_SOCKET_PROCESSOR);
    }

}