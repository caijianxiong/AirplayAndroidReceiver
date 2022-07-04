package com.github.serezhka.jap2server.internal.handler.control;

import com.github.serezhka.jap2server.internal.handler.session.Session;
import com.github.serezhka.jap2server.internal.handler.session.SessionManager;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.ByteBufOutputStream;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;

@ChannelHandler.Sharable
public class PairingHandler extends ControlHandler {

    public PairingHandler(SessionManager sessionManager) {
        super(sessionManager);
    }

    @Override
    protected boolean handleRequest(ChannelHandlerContext ctx, Session session, FullHttpRequest request) throws Exception {
        String uri = request.uri();
        switch (uri) {
            case "/info": {
                DefaultFullHttpResponse response = createResponseForRequest(request);
                session.getAirPlay().info(new ByteBufOutputStream(response.content()));
                return sendResponse(ctx, request, response);
            }
            case "/pair-setup": {
                DefaultFullHttpResponse response = createResponseForRequest(request);
                session.getAirPlay().pairSetup(new ByteBufOutputStream(response.content()));
                return sendResponse(ctx, request, response);
            }
            case "/pair-verify": {
                DefaultFullHttpResponse response = createResponseForRequest(request);
                session.getAirPlay().pairVerify(new ByteBufInputStream(request.content()),
                        new ByteBufOutputStream(response.content()));
                return sendResponse(ctx, request, response);
            }
        }
        return false;
    }

}
