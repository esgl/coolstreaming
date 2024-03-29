/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *  
 *    http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License. 
 *  
 */
package simpipe.examples;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Set;

import org.apache.mina.common.IoHandler;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;

/**
 * A {@link IoHandler} implementation which plays a tennis game.
 * 
 * @author The Apache Directory Project (mina-dev@directory.apache.org)
 * @version $Rev: 555855 $, $Date: 2007-07-13 12:19:00 +0900 (금, 13  7월 2007) $
 */
public class TennisPlayer extends IoHandlerAdapter {
    private static int nextId = 0;

    /** Player ID **/
    private final int id = nextId++;

    
    
    @Override
	public void sessionCreated(IoSession session) throws Exception {
    	System.out.println("Player-" + id + ": Created");	
    }

	public void sessionOpened(IoSession session) {
		
		System.out.println("Player-" + id + ": Opened ");
	    Set<String> set =session.getAttributeKeys();
		if (id !=0)
			session.write(new TennisBall(2));
    }

    public void sessionClosed(IoSession session) {
        System.out.println("Player-" + id + ": QUIT");
    }

    public void messageReceived(IoSession session, Object message) {
        System.out.println("Player-" + id + ": RCVD " + message);

        TennisBall ball = (TennisBall) message;

        // Stroke: TTL decreases and PING/PONG state changes.
        ball = ball.stroke();

        if (ball.getTTL() > 0) {
            // If the ball is still alive, pass it back to peer.
            session.write(ball);
        } else {
            // If the ball is dead, this player loses.
            System.out.println("Player-" + id + ": LOSE");
            session.close();
        }
    }

    public void messageSent(IoSession session, Object message) {
        System.out.println("Player-" + id + ": SENT " + message);
    }

    public void exceptionCaught(IoSession session, Throwable cause) {
        cause.printStackTrace();
        session.close();
    }
}