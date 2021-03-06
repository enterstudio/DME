/*******************************************************************************
 * Copyright (c) 2016 AT&T Intellectual Property. All rights reserved.
 *******************************************************************************/
package com.att.aft.dme2.test.jms.server;

import java.io.File;
import java.util.Hashtable;
import java.util.Properties;

import javax.jms.Queue;
import javax.jms.QueueConnection;
import javax.jms.QueueConnectionFactory;
import javax.jms.QueueSession;
import javax.naming.InitialContext;

import com.att.aft.dme2.jms.DME2JMSLocalQueue;
import com.att.aft.dme2.test.jms.util.FastFailoverMessageListener;
import com.att.aft.dme2.test.jms.util.Locations;
import com.att.aft.dme2.test.jms.util.RegistryFsSetup;
import com.att.aft.dme2.test.jms.util.TestConstants;

public class FastFailJMSServer {

	private InitialContext context;
	private QueueConnectionFactory factory;
	private QueueConnection connection;
	private QueueSession session;
	private Queue requestQueue;

	private String city = null;
	private String killFile = null;
	private String port = null;
	public boolean servletThreadFailure = false;
	public boolean socketAcceptorFailure = false;
//	public boolean servletThreadFailure = true;
//	public boolean socketAcceptorFailure = true;	

	public FastFailJMSServer() throws Exception {
	}

	public void setCity(String city) {
		this.city = city;
	}

	public String getCity() {
		return this.city;
	}

	public void setKillFile(String killFile) {
		this.killFile = killFile;
	}

	public String getKillFile() {
		return this.killFile;
	}

	public void setPort(String port) {
		this.port = port;
	}
	public void init() throws Exception{
		start();
	}

	public void start() throws Exception {
		if (city.equals("BHAM"))
			Locations.BHAM.set();
		else if (city.equals("CHAR"))
			Locations.CHAR.set();
		else if (city.equals("JACK"))
			Locations.JACK.set();

		int sPort = 0;
		if (port != null) {
			try {
				sPort = Integer.parseInt(port);
			} catch (Exception e) {

			}
		}
		Properties props = RegistryFsSetup.init();
		if (sPort > 0) {
			props.setProperty("AFT_DME2_PORT", "" + sPort);
		} else {
			sPort = 9595;
			props.setProperty("AFT_DME2_PORT", "" + sPort);
		}
		if (this.servletThreadFailure) {
			props.setProperty("AFT_DME2_CORE_POOL_SIZE", "9");
			props.setProperty("AFT_DME2_MAX_POOL_SIZE", "10");
			props.setProperty("AFT_DME2_SOCKET_ACCEPTOR_THREADS", "4");
		}
		if (this.socketAcceptorFailure) {
			props.setProperty("AFT_DME2_CORE_POOL_SIZE", "3");
			props.setProperty("AFT_DME2_MAX_POOL_SIZE", "6");
			props.setProperty("AFT_DME2_SOCKET_ACCEPTOR_THREADS", "1");
		}
		Hashtable<String, Object> table = new Hashtable<String, Object>();
		for (Object key : props.keySet()) {
			table.put((String) key, props.get(key));
		}
		table.put("java.naming.factory.initial", TestConstants.jndiClass);
		table.put("java.naming.provider.url", TestConstants.jndiUrl);

		context = new InitialContext(table);
		factory = (QueueConnectionFactory) context.lookup(TestConstants.clientConn);
		connection = factory.createQueueConnection();
		session = connection.createQueueSession(true, 0);
		requestQueue = (Queue) context.lookup(TestConstants.failserviceToRegister);
		int listenerCount = 0;
		if (this.socketAcceptorFailure) {
			listenerCount = 7;
		} else if (this.servletThreadFailure) {
			listenerCount = TestConstants.listenerCount;
		} else
			listenerCount = 1;
		FastFailoverMessageListener[] listeners = new FastFailoverMessageListener[listenerCount];
		for (int i = 0; i < listeners.length; i++) {
			listeners[i] = new FastFailoverMessageListener(connection, requestQueue, sPort);
			listeners[i].start();
		}
		System.out.println("FastFailJMSServer. request queue is: "+requestQueue.getQueueName()+" connection is: "+connection.toString()+" port is: "+sPort+" listener count is: "+listeners.length);
		System.out.println("FastFailJMSServer.init(): requestQueue.getListeners().size()="
				+ ((DME2JMSLocalQueue) requestQueue).getListeners().size());

		System.out.println("FastFailJMSServer started successfully...");

		File f = new File(killFile);
		while (!f.exists()) {
			try {
				Thread.sleep(5000);
			} catch (Exception ex) {
			}
			System.out.println("Sleeping for 5000 and waiting for kill file " + getKillFile());
		}

		f.delete();
		for (int i = 0; i < listeners.length; i++) {
			try {
				listeners[i].stop1();
			} catch (Exception e) {
				// Ignore any error in stopping listener thread
			}
		}
		System.out.println("FastFailJMSServer destroyed.");
		System.exit(0);
	}

	public static void main(String[] args) throws Exception {
		try {
			FastFailJMSServer server = new FastFailJMSServer();
	
			// String city = null;
			if (args.length == 0)
				server.setCity("BHAM");
			else {
				for (int i = 0; i < args.length; i++) {
					if ("-city".equals(args[i]))
						server.setCity(args[i + 1]);
					else if ("-killfile".equals(args[i]))
						server.setKillFile(args[i + 1]);
					else if ("-port".equals(args[i]))
						server.setPort(args[i + 1]);
					else if ("-servlet".equals(args[i]))
						server.servletThreadFailure = true;
					else if ("-socket".equals(args[i]))
						server.socketAcceptorFailure = true;
				}
			}
	
			server.init();
		} catch(Exception ex) {
			//System.err.println(ex);
		}
	}

}
