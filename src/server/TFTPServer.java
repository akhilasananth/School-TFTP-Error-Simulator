package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/*
 * The Console class will allow someone (presumably an admin) to manage the server
 * from a local machine. Currently its only functionality is to close the server,
 * but this can be expanded later.
 */
class Console implements Runnable {
	//Console() {}

	public void run() {
		try {
			 (new BufferedReader(new InputStreamReader(System.in))).readLine();
		} catch (IOException e) {
			System.out.println("Failed to read line on command console.");
			e.printStackTrace();
		}
		
		TFTPServer.active.set(false);
	}
}


/*
 * 
 */
/**
 * @author Kyle
 *
 */
/**
 * @author Kyle
 *
 */
/**
 * @author Kyle
 *
 */
public class TFTPServer implements Callback {
	
	/**
	 * Main function that starts the server.
	 */
	public static void main(String[] args) {
		TFTPServer listener = new TFTPServer();
		listener.start();
	}
	
	// Some class attributes.
	static AtomicBoolean active = new AtomicBoolean(true);
	Vector<Thread> threads;
	
	final Lock lock = new ReentrantLock();
	final Condition notEmpty = lock.newCondition();

	
	/**
	 * Constructor for TFTPServer that initializes the thread container 'threads'.
	 */
	public TFTPServer() {
		threads = new Vector<Thread>();
	}
	
	
	/**
	 * Handles operation of the server.
	 */
	public void start() {
		
		// Create the socket.
		DatagramSocket serverSock = null;
		try {
			serverSock = new DatagramSocket(5000);
			serverSock.setSoTimeout(30);
		} catch (SocketException e) {
			System.out.println("Failed to make main socket.");
			e.printStackTrace();
			System.exit(1);
		}
		
		// Create and start a thread for the command console.
		Thread console = new Thread(new Console(), "command console");
		console.start();
		
		// Create the packet for receiving.
		byte[] buffer = new byte[1024]; // Temporary. Will be replaced with exact value soon.
		DatagramPacket receivePacket = new DatagramPacket(buffer, buffer.length);
		
		/*
		 * - Receive packets until the admin console gives the shutdown signal.
		 * - Since receiving a packet is a blocking operation, timeouts have been set to loop back
		 *   and check if the signal to close has been given.
		 */
		while (active.get()) {
			try {
				serverSock.receive(receivePacket);
			} catch (SocketTimeoutException e) {
				continue;
			} catch (IOException e) {
				System.out.println("Failed to receive packet on main thread.");
				e.printStackTrace();
			}
			// You are calling this in main(), you can't pass public static void main into a class
			Thread service = new Thread(new TFTPService(receivePacket, this), "Service");
			threads.addElement(service);
			service.start();
		}
		
		serverSock.close();
		
		/*
		 * Wait for all service threads to close before completely exiting.
		 */
		while (!threads.isEmpty()) {
			try {
				notEmpty.await();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	public synchronized void callback(long id) {
		for (Thread t : threads)
			if (t.getId() == id) {
				threads.remove(t);
				notEmpty.signal();
				break;
			}
	}
}
