/**
 * 
 */
package testbed;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.LinkedList;

import helpers.BufferPrinter;
import resource.Configurations;
import resource.Strings;
import server.Callback;
import testbed.errorcode.ErrorCodeFive;
import testbed.errorcode.ErrorCodeFour;
import testbed.errorcode.TransmissionError;
import types.ErrorType;
import types.InstanceType;
import types.Logger;
import types.RequestType;

/**
 * @author Team 3
 *
 *         This class serves as the intermediate host between our client server.
 *         The primary object of this class is to simulate UDP errors in order
 *         to test the soundness of our TFTP system
 */
public class ErrorSimulatorService implements Runnable {

	// by default set the log level to debug
	private Logger logger = Logger.VERBOSE;

	private final String CLASS_TAG = "<Error Simulator Thread>";

	/* Networking Variables */
	private int mForwardPort;
	private final int mClientPort;
	private final InetAddress mClientHostAddress;
	private final int mServerListenPort;
	private InetAddress mServerHostAddress;

	/* Core logic variables */
	private LinkedList<DatagramPacket> mPacketSendQueue;
	private Callback mCallback;
	private ErrorCommand mErrorSettings;
	private DatagramPacket mLastPacket;
	private DatagramSocket mSendReceiveSocket;
	private RequestType mInitialRequestType;
	private InstanceType mMessUpThisTransfer;
	private byte[] mBuffer = null;
	private int mPacketsProcessed;

	/* Section of uninitialized Error Producers */
	private ErrorCodeFour mEPFour = null;
	private ErrorCodeFive mEPFive = null;
	//private TransmissionError mTransmissionError = null;
	/* Lazy initialization for Error Producers */

	/**
	 * This thread manages the facilitation of packets from the client to the
	 * server. It remembers where the packet comes from and also fixes the
	 * destination of the packet. The configurations for this handler is in the
	 * resource/Configurations class.
	 * 
	 * @param inDatagram
	 *            - last packet received, first instance will tell use who to
	 *            reply to
	 * @param cb
	 *            - call back to tell main thread this runnable finished
	 * @param errorSetting
	 *            - configurable to determine which error to produce for this
	 *            thread
	 */
	@SuppressWarnings("unused")
	public ErrorSimulatorService(DatagramPacket inDatagram, Callback cb, ErrorCommand errorSetting,
			InstanceType instance) {
		this.mLastPacket = inDatagram;
		this.mErrorSettings = errorSetting;
		this.mCallback = cb;
		this.mClientHostAddress = inDatagram.getAddress();
		this.mClientPort = inDatagram.getPort();
		this.mServerListenPort = Configurations.SERVER_LISTEN_PORT;
		this.mForwardPort = this.mServerListenPort;
		this.mInitialRequestType = RequestType.matchRequestByNumber((int) this.mLastPacket.getData()[1]);
		try {
			if (Configurations.SERVER_INET_HOST == "localhost") {
				this.mServerHostAddress = InetAddress.getLocalHost();
			} else {
				this.mServerHostAddress = InetAddress.getByName(Configurations.SERVER_INET_HOST);
			}
			this.mSendReceiveSocket = new DatagramSocket();
		} catch (UnknownHostException e) {
			e.printStackTrace();
		} catch (SocketException e) {
			e.printStackTrace();
		}
		this.mMessUpThisTransfer = instance;
		logger.setClassTag(CLASS_TAG);
		logger.print(Logger.SILENT,
				"Initalized error sim service on port " + this.mSendReceiveSocket.getLocalPort() + "\n");
		this.mPacketSendQueue = new LinkedList<>();
		this.mPacketsProcessed = 0;
	}

	/**
	 * This function will mediate traffic between the client and the server In
	 * coming packets from the client will be repackaged into a new
	 * DatagramPacket and sent to the server. In coming response packets from
	 * the server will be directly forwarded back to the client
	 * 
	 * @throws IOException
	 */
	public void run() {
		DatagramPacket serverPacket = null;
		boolean transferNotFinished = true;
		boolean ackServerOnLastBlockByClient = false;
		boolean errorSentToClient = false;
		boolean errorSendToServer = false;
		int wrqPacketSize = -1; // Used to determine whether to forward last ACK
								// packet

		// Add the first packet to the queue
		this.mPacketSendQueue.addFirst(this.mLastPacket);
		// Always forward the first packet to port 69
		this.mLastPacket.setPort(this.mForwardPort);
		this.mLastPacket.setAddress(this.mServerHostAddress);

		while (transferNotFinished) {
			try {
				logger.print(Logger.SILENT, "Preparing to send packet to server at port " + this.mForwardPort);
				// Send off this that is directed to the server
				if (this.mMessUpThisTransfer == InstanceType.SERVER) {
					this.createSpecifiedError(this.mPacketSendQueue.peekFirst());
				}
				if (this.mPacketSendQueue.peekFirst() != null &&
						this.mPacketSendQueue.peekFirst().getPort() == this.mForwardPort) {
					// This "if" block is for sending to the server

					// Send the next packet in the work queue
					forwardPacketToSocket(this.mPacketSendQueue.pop());

					if (!transferNotFinished) {
						// Break here is for the last ACK packet from client WRQ
						break;
					}
					logger.print(Logger.SILENT, Strings.ES_RETRIEVE_PACKET_SERVER);
					serverPacket = retrievePacketFromSocket();
					this.mPacketSendQueue.addLast(serverPacket);
					// This following block, checks if we are on the last packet
					if (this.mInitialRequestType == RequestType.RRQ) {
						if (serverPacket.getLength() < Configurations.MAX_MESSAGE_SIZE) {
							// Coming into this block means that on a RRQ, the
							// last data block
							transferNotFinished = false;
							ackServerOnLastBlockByClient = true;
						}
					} else if (this.mInitialRequestType == RequestType.WRQ && wrqPacketSize > 0) {
						// Must test if this was the first transfer
						// wrqPacketSize = 0
						if (wrqPacketSize < Configurations.MAX_MESSAGE_SIZE) {
							// We have finished the transfer
							logger.print(Logger.SILENT, Strings.ES_GOT_LAST_PACKET_WRQ);
							transferNotFinished = false;
						}
					}

					this.mLastPacket = serverPacket;
					// Set the mForwardPort to the Server's Thread Port
					this.mForwardPort = serverPacket.getPort();
					// Redirect the packet back to the client address
					this.mLastPacket.setPort(this.mClientPort);
					this.mLastPacket.setAddress(this.mClientHostAddress);
					
				}

				if (this.mMessUpThisTransfer == InstanceType.CLIENT) {
					this.createSpecifiedError(this.mPacketSendQueue.peekFirst());
				}
				if (this.mPacketSendQueue.peekFirst() != null &&
						this.mPacketSendQueue.peekFirst().getPort() == this.mClientPort) {
					logger.print(Logger.SILENT, Strings.ES_SEND_PACKET_CLIENT);

					// Send the next packet in the work queue
					forwardPacketToSocket(this.mPacketSendQueue.pop());

					if (this.mLastPacket.getData()[1] == 5) {
						errorSentToClient = true;
						break;
					}

					logger.print(Logger.SILENT, Strings.ES_RETRIEVE_PACKET_CLIENT);
					// Receiving from client
					this.mLastPacket = retrievePacketFromSocket();
					this.mPacketSendQueue.addLast(this.mLastPacket);
					// Set the write packet size in order to determine the end
					wrqPacketSize = this.mLastPacket.getLength();
					this.mLastPacket.setPort(this.mForwardPort);
					this.mLastPacket.setAddress(this.mServerHostAddress);

					if (ackServerOnLastBlockByClient) {
						// This extra process is needed on a read request to
						// send the last ACK to the server
						logger.print(Logger.SILENT, "Sending last ACK packet to server (RRQ) " + this.mClientPort);
						this.mLastPacket.setPort(this.mForwardPort);
						this.mLastPacket.setAddress(this.mServerHostAddress);
						forwardPacketToSocket(this.mLastPacket);
					}
					
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (errorSentToClient || errorSendToServer) {
			logger.print(Logger.ERROR, Strings.ES_TRANSFER_ERROR);
		} else {
			logger.print(Logger.SILENT, Strings.ES_TRANSFER_SUCCESS);
		}

		this.mSendReceiveSocket.close();
		this.mCallback.callback(Thread.currentThread().getId());
	}

	/**
	 * Determines where or not corrupt a datagram packet
	 * 
	 * @param inPacket
	 *            - a packet to corrupt or not
	 */
	private void createSpecifiedError(DatagramPacket inPacket) {
		if(inPacket == null) {
			return;
		}
		++this.mPacketsProcessed;
		ErrorType vErrType = this.mErrorSettings.getMainErrorFamily();
		int subOpt = this.mErrorSettings.getSubErrorFromFamily();
		switch (vErrType) {
		case FILE_NOT_FOUND:
			// error code 1
			break;
		case ACCESS_VIOLATION:
			// error code 2
			break;
		case ALLOCATION_EXCEED:
			// error code 3
			break;
		case ILLEGAL_OPERATION:
			// error code 4
			if (this.mEPFour == null) {
				this.mEPFour = new ErrorCodeFour(inPacket, subOpt);
			} else {
				this.mEPFour.constructPacketBuilder(inPacket);
			}
			this.mLastPacket = mEPFour.errorPacketCreator();
			break;
		case UNKNOWN_TRANSFER:
			// Thread codeFiveThread = new Thread(new ErrorCodeFive(inPacket),
			// "Error Code 5 thread");
			// codeFiveThread.start();
			if (this.mEPFive == null) {
				this.mEPFive = new ErrorCodeFive(inPacket);
			}
			this.mEPFive.run();
			// error code 5 thread
			break;
		case FILE_EXISTS:
			// error code 6
			break;
		case NO_SUCH_USER:
			// error code 5
			break;
		case TRANSMISSION_ERROR:
			if(this.mErrorSettings.getTransmissionErrorType() != RequestType.NONE &&
			   this.mErrorSettings.getTransmissionErrorType().getOptCode() != inPacket.getData()[1]) {
				System.out.println(String.format("type %s and compare header %s", 
						this.mErrorSettings.getTransmissionErrorType() != RequestType.NONE,
						this.mErrorSettings.getTransmissionErrorType().getOptCode() != inPacket.getData()[1]));
				return;
			}
			if((this.mPacketsProcessed % (this.mErrorSettings.getTransmissionErrorFrequency()+1)) != 0 && 
			    this.mErrorSettings.getTransmissionErrorOccurences() < this.mPacketsProcessed/2) {
				System.err.println(String.format("Dont panic! We stoped making transmission errors. Heres why frequency hop: %d and %d/%d occurence/processed",
						this.mPacketsProcessed % (this.mErrorSettings.getTransmissionErrorFrequency()+1),this.mErrorSettings.getTransmissionErrorOccurences(),
						 this.mPacketsProcessed/2));
				return;
			}
			switch(this.mErrorSettings.getSubErrorFromFamily())
			{
			case 1:
				// Lose a packet
				break;
			case 2:
				// Delay a packet
				TransmissionError transmissionError = new TransmissionError(inPacket, this.mErrorSettings.getTransmissionErrorFrequency(), this);
				Thread delayPacketThread = new Thread(transmissionError);
				delayPacketThread.start();
				// We passed that packet to the thread, so lets pop it out now
				this.mPacketSendQueue.pop();
				break;
			case 3:
				// Duplicate a packet
				break;
			default:
				System.err.println("WRONG Transmission suberror");
			}
			
			break;
		default:
			// Don't create an error
			break;
		}
	}

	/**
	 * This function takes care of sending the packet to any address that is
	 * identifies by the DatagramPacket
	 * 
	 * @param inUDPPacket
	 *            describes a packet that requires to be sent
	 * @throws IOException
	 */
	private void forwardPacketToSocket(DatagramPacket inUDPPacket) throws IOException {
		sendPacket(new DatagramPacket(inUDPPacket.getData(), inUDPPacket.getLength(), inUDPPacket.getAddress(),
				inUDPPacket.getPort()));
	}

	/**
	 * This function takes care of sending the packet to any address that is
	 * identifies by the DatagramPacket and uses the DatagramSocket parameter to
	 * send to the host
	 * 
	 * @param inUDPPacket
	 *            describes a packet that requires to be sent
	 * @param socket
	 *            describes a socket that the packet is sent on
	 * @throws IOException
	 */
	private void forwardPacketToSocket(DatagramPacket inUDPPacket, DatagramSocket socket) throws IOException {
		sendPacket(new DatagramPacket(inUDPPacket.getData(), inUDPPacket.getLength(), inUDPPacket.getAddress(),
				inUDPPacket.getPort()), socket);
	}

	/**
	 * This function will use the initialized DatagramSocket to send off the
	 * incoming packet and print the packet buffer to console. This method will
	 * not close the socket it is meant to send on
	 * 
	 * @param packet
	 *            represents the DatagramPacket that requires to be sent
	 * @param sendSocket
	 *            represents the DatagramSocket to use
	 * @throws IOException
	 */
	private void sendPacket(DatagramPacket packet, DatagramSocket sendSocket) throws IOException {
		sendSocket.send(packet);
		BufferPrinter.printBuffer(packet.getData(), CLASS_TAG, logger);
	}

	/**
	 * Deprecated. Cannot be used to get a reply as the socket will be closed
	 * right after a send happens. This function will use the initialized
	 * DatagramSocket to send off the incoming packet and print the packet
	 * buffer to console
	 * 
	 * @param packet
	 *            represents the DatagramPacket that requires to be sent
	 * @throws IOException
	 */
	private void sendPacket(DatagramPacket packet) throws IOException {
		this.mSendReceiveSocket.send(packet);
		BufferPrinter.printBuffer(packet.getData(), CLASS_TAG, logger);
	}

	/**
	 * This function handles the retrieval of a response, sent back to the
	 * client. This function also trims the packet received from any unwanted
	 * trailing zeros.
	 * 
	 * @param socket
	 *            to receive from
	 * @return returns the DatagramPacket that the socket as received
	 * @throws IOException
	 */
	private DatagramPacket retrievePacketFromSocket() throws IOException {
		mBuffer = new byte[Configurations.MAX_BUFFER];
		DatagramPacket receivePacket = new DatagramPacket(mBuffer, mBuffer.length);
		this.mSendReceiveSocket.receive(receivePacket);

		int realPacketSize = receivePacket.getLength();
		byte[] packetBuffer = new byte[realPacketSize];
		System.arraycopy(receivePacket.getData(), 0, packetBuffer, 0, realPacketSize);
		receivePacket.setData(packetBuffer);

		BufferPrinter.printBuffer(receivePacket.getData(), CLASS_TAG, logger);
		return receivePacket;
	}

	public void addWorkToFrontOfQueue(DatagramPacket inPacket) {
		this.mPacketSendQueue.addFirst(inPacket);
	}
}
