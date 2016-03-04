/**
 * 
 */
package testbed;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedList;

import helpers.BufferPrinter;
import packet.Packet;
import packet.PacketBuilder;
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
	private int mTransmissionRetries;

	/* Section of uninitialized Error Producers */
	private ErrorCodeFour mEPFour = null;
	private ErrorCodeFive mEPFive = null;
	private RequestType mPacketOpCode = null;
	private int mPacketBlock = 0;
	private boolean mLostPacketPerformed = false; // if already lost packet
	private boolean mDelayPacketPerformed = false; // has this been performed?
	private boolean mDuplicatePacketPerformed = false; // just need to happen
														// once
	
	/* Special flags */
	private boolean END_THREAD = false;

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
		logger.print(Logger.VERBOSE,
				"Initalized error sim service on port " + this.mSendReceiveSocket.getLocalPort() + "\n");
		this.mPacketSendQueue = new LinkedList<>();
		mTransmissionRetries = 0;
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
		// Initialize useful members
		boolean isTransfering = true;
		boolean errorSentToClient = false;
		boolean errorSendToServer = false;
		DatagramPacket receivedPacket = null;
		
		try {
			// Facilitate the first WRQ/RRQ request and set server thread port
			this.mPacketSendQueue.addLast(this.mLastPacket);
			if(this.mMessUpThisTransfer == InstanceType.SERVER)
				this.simulateError(this.mLastPacket); // only can mess with server packs on first go.
			if(this.mLastPacket == null || this.END_THREAD) {
				this.mSendReceiveSocket.close();
				logger.print(Logger.ERROR, "Ending thread for a lost initiating (RRQ/WRQ) packet."); 
				return;
			}
			this.mLastPacket.setPort(this.mForwardPort); // Always 69 at this point
			this.mLastPacket.setAddress(this.mServerHostAddress);
			this.mSendReceiveSocket.setSoTimeout(0);
			while(this.mPacketSendQueue.size() == 0) {}
			this.forwardPacketToSocket(this.mPacketSendQueue.pop());
			receivedPacket = this.retrievePacketFromSocket();
			
			
			if (packetIsError(receivedPacket)) {
				isTransfering = false;
				receivedPacket.setAddress(this.mClientHostAddress);
				receivedPacket.setPort(this.mClientPort);
				this.mLastPacket = receivedPacket;
				this.mPacketSendQueue.addLast(this.mLastPacket);
			} else {
				this.mForwardPort = receivedPacket.getPort();
				this.mServerHostAddress = receivedPacket.getAddress();
				this.mLastPacket = receivedPacket;
				this.mPacketSendQueue.addLast(this.mLastPacket);
			}

		} catch (IOException e) {
			System.err.println("Sending the first RRQ and WRQ was an issue!");
		}

		// Main while loop to facilitate transfer and create error
		// First packet will be from client
		while (isTransfering) {
			try {
				// The following function adds the packet into the work Q
				isTransfering = continueHandlingPacket(this.mPacketSendQueue.peek());
				if (this.mPacketSendQueue.size() > 0) {
					directPacketToDestination();
					forwardPacketToSocket(this.mPacketSendQueue.pop());
				}
				this.mLastPacket = retrievePacketFromSocket();
				this.mPacketSendQueue.addLast(this.mLastPacket);

			} catch (IOException e) {
				System.err.println("Something bad happened while transfering files");
			}
		}
		// Possibly want to mess with the last packet
		continueHandlingPacket(this.mPacketSendQueue.peek());
		// End ACK based on request type.
		try {
			// Wait on last ACK in case of the last data was lost.
			System.out.println("Preparing to handle last ACK");
			this.mSendReceiveSocket.setSoTimeout(Configurations.TRANMISSION_TIMEOUT * 6);
			while(true) {
				try{	
					if (this.mInitialRequestType == RequestType.WRQ) {
						// Send the last ACK to client
						this.mLastPacket.setPort(this.mClientPort);
						this.mLastPacket.setAddress(this.mClientHostAddress);
						this.forwardPacketToSocket(this.mLastPacket);
					} else if (this.mInitialRequestType == RequestType.RRQ) {
						// Send the last ACK to server
						this.mLastPacket.setPort(this.mForwardPort);
						this.mLastPacket.setAddress(this.mServerHostAddress);
						logger.print(Logger.VERBOSE, "Preparing to send packet to server at port " + this.mForwardPort);
						this.forwardPacketToSocket(this.mLastPacket);
					}
					byte[] data = new byte[Configurations.MAX_BUFFER];
					DatagramPacket receivePacket = new DatagramPacket(data, data.length);
					this.mSendReceiveSocket.receive(receivePacket);
					this.mLastPacket = receivePacket;
					break;
				} catch (SocketTimeoutException e) {
					if(++this.mTransmissionRetries == Configurations.RETRANMISSION_TRY - 1) {
						logger.print(Logger.ERROR, String.format("Retransmission retried %d times, send file considered done.",
								this.mTransmissionRetries));
						break;
					}
				}
			}
			this.mSendReceiveSocket.setSoTimeout(0);
		} catch(NullPointerException e) {
			System.err.println("Null pointer on zombie thread. Shutting down.");
		}catch (IOException e) {
		
			System.err.println("Something bad happened while transfering files.");
		}
		if (errorSentToClient || errorSendToServer) {
			logger.print(Logger.ERROR, Strings.ES_TRANSFER_ERROR);
		} else {
			logger.print(Logger.VERBOSE, Strings.ES_TRANSFER_SUCCESS);
		}

		// Closing Logic
		this.mSendReceiveSocket.close();
		this.mCallback.callback(Thread.currentThread().getId());
	}

	/**
	 * This function will provide packet handling and error creation
	 * 
	 * @param inPacket
	 * @return true if we continue to listen for packets, false otherwise
	 */
	private boolean continueHandlingPacket(DatagramPacket inPacket) {
		if (inPacket == null)
			return true;
		this.mLastPacket = inPacket;
		if (inPacket.getPort() == this.mClientPort) {
			// From Client
			if (packetIsError(inPacket)) {
				inPacket.setAddress(this.mServerHostAddress);
				inPacket.setPort(this.mForwardPort);
				this.mPacketSendQueue.addFirst(inPacket); // May cause issue to
															// be determined
															// later
				logger.print(Logger.VERBOSE,
						String.format("Client sent a error packet, now forwarding it to the server!"));
				return false;
			}
			if (this.mMessUpThisTransfer == InstanceType.SERVER) {
				this.simulateError(inPacket); // Adds packet into Q
			}
			if (this.mInitialRequestType == RequestType.RRQ) {
				logger.print(Logger.VERBOSE,
						String.format("An ack packet was received from the client, forwarding to server!"));
				return true; // This will be an ACK
			} else {
				return inPacket.getLength() == Configurations.MAX_MESSAGE_SIZE;
			}
		} else {
			// From Server
			if (packetIsError(inPacket)) {
				inPacket.setAddress(this.mClientHostAddress);
				inPacket.setPort(this.mClientPort);
				this.mPacketSendQueue.addFirst(inPacket); // May cause issue to
															// be determined
															// later
				logger.print(Logger.VERBOSE,
						String.format("Server sent a error packet, now forwarding it to the client!"));
				return false;
			}
			if (this.mMessUpThisTransfer == InstanceType.CLIENT) {
				this.simulateError(inPacket); // Adds packet into Q
			}
			if (this.mInitialRequestType == RequestType.RRQ) {
				return inPacket.getLength() == Configurations.MAX_MESSAGE_SIZE;
			} else {
				logger.print(Logger.VERBOSE,
						String.format("An ack packet was received from the server, forwarding it to client!"));
				return true; // This will be an ACK
			}
		}
	}

	/**
	 * This function is used to apply the rules of the simulator to make sure
	 * arbitrary packets get forwarded to the correct destination
	 */
	private void directPacketToDestination() {
		switch (this.mInitialRequestType) {
		case RRQ:
			if (this.mLastPacket.getData()[1] == 4) {
				// This is an ACK, an ACK always go to the server
				this.mLastPacket.setPort(this.mForwardPort);
				this.mLastPacket.setAddress(this.mServerHostAddress);
			} else if (this.mLastPacket.getData()[1] == 3) {
				// This is a DATA packet, a DATA packet always goes to the
				// client
				this.mLastPacket.setPort(this.mClientPort);
				this.mLastPacket.setAddress(this.mClientHostAddress);
			} else {
				// Possible Error Packet
				if (this.mLastPacket.getPort() == this.mServerListenPort) {
					// It is from the server, so we send it to the client
					this.mLastPacket.setPort(this.mClientPort);
					this.mLastPacket.setAddress(this.mClientHostAddress);
				} else {
					// It is from the client, so we send it to the server
					this.mLastPacket.setPort(this.mForwardPort);
					this.mLastPacket.setAddress(this.mServerHostAddress);
				}
				logger.print(logger, "Unable to determine which entity to forward the packet on RRQ.");
			}
			break;
		case WRQ:
			if (this.mLastPacket.getData()[1] == 4) {
				// This is an ACK, an ACK always go to the client
				this.mLastPacket.setPort(this.mClientPort);
				this.mLastPacket.setAddress(this.mClientHostAddress);
			} else if (this.mLastPacket.getData()[1] == 3) {
				// This is a DATA, a DATA always go to the server
				this.mLastPacket.setPort(this.mForwardPort);
				this.mLastPacket.setAddress(this.mServerHostAddress);
			} else {
				// Possible Error Packet
				if (this.mLastPacket.getPort() == this.mServerListenPort) {
					// It is from the server, so we send it to the client
					this.mLastPacket.setPort(this.mClientPort);
					this.mLastPacket.setAddress(this.mClientHostAddress);
				} else {
					// It is from the client, so we send it to the server
					this.mLastPacket.setPort(this.mForwardPort);
					this.mLastPacket.setAddress(this.mServerHostAddress);
				}
				logger.print(logger, "Unable to determine which entity to forward the packet on WRQ.");
			}

			break;
		default:
			logger.print(logger, "The packet forwarded was not a RRQ or WRQ.");
		}
	}

	/**
	 * Determines where or not corrupt a datagram packet
	 * 
	 * @param inPacket
	 *            - a packet to corrupt or not
	 */
	private void simulateError(DatagramPacket inPacket) {
		
		if (inPacket == null) {
			System.err.println("Simulate error called on null packet!");
			return;
		}

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
				this.mEPFour.setReceivePacket(inPacket);
			}
			this.mLastPacket = mEPFour.errorPacketCreator();
			this.mPacketSendQueue.pop();
			this.mPacketSendQueue.addLast(this.mLastPacket);
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
			if (this.mErrorSettings.getTransmissionErrorType() != RequestType.NONE
					&& this.mErrorSettings.getTransmissionErrorType().getOptCode() != inPacket.getData()[1]) {
				logger.print(Logger.ERROR,
						String.format(
								"Not making delay error because the type we want is %s and header we compare is %s",
								this.mErrorSettings.getTransmissionErrorType() != RequestType.NONE,
								this.mErrorSettings.getTransmissionErrorType().getOptCode() != inPacket.getData()[1]));
				break;
			}
			Packet mInPacket;
			switch (this.mErrorSettings.getSubErrorFromFamily()) {
			case 1:
				// Lose a packet
				System.err.println("Testing to lose.");
				this.mPacketBlock = this.mErrorSettings.getTransmissionErrorOccurences();
				this.mPacketOpCode = this.mErrorSettings.getTransmissionErrorType();
				mInPacket = (new PacketBuilder()).constructPacket(mLastPacket);
				
				if (mInPacket.getBlockNumber() != this.mPacketBlock || mInPacket.getRequestType() != this.mPacketOpCode
						|| this.mLostPacketPerformed) {
					System.err.println(String.format("%d =? %d %d =? %d", mInPacket.getBlockNumber(),this.mPacketBlock, 
							mInPacket.getRequestType().getOptCode(), this.mPacketOpCode.getOptCode() ));
					return;
				}
				System.err.println("Attempting to lose packet.");
				this.mPacketSendQueue.pop();
				
				if (this.mPacketOpCode == RequestType.ERROR) {
					byte[] data = this.mLastPacket.getData();
					this.directPacketToDestination();
					data[1]+=10;
					this.mLastPacket.setData(data);
					try {
						
						forwardPacketToSocket(this.mLastPacket);
						this.mLastPacket = this.retrievePacketFromSocket();
						return;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				if (mInPacket.getBlockNumber() == -1 && (mInPacket.getRequestType() == this.mInitialRequestType)) {
					logger.print(Logger.VERBOSE, "Lost first request, unable to satisfy client on this thread due it's connection was from port 68.");
					this.END_THREAD = true;
					return;
				}
				this.mLostPacketPerformed = true;
				try {
					this.mSendReceiveSocket.setSoTimeout(Configurations.TRANMISSION_TIMEOUT * 2);
					this.mLastPacket = this.retrievePacketFromSocket();
					this.mSendReceiveSocket.setSoTimeout(0);
					if(this.mLastPacket == null) {
						logger.print(Logger.ERROR, "After losing a packet, the thread had no replies after 3 retries. Shutting down thread.");
						break;
					}
				} catch (SocketException e1) {
					e1.printStackTrace();
				}
			

				directPacketToDestination();
				try {
					forwardPacketToSocket(this.mLastPacket);
				} catch (IOException e) {
					System.err.println("Error catch entity timeouts form both sides during a delay.");
				}

				break;
			case 2:
				// We check this condition since this type packet.
				// mPacketsProcessed is always ahead of ErrorOccurrences by 1
				// only gets incremented one way -> messing with client or
				// server bound packets (set in ES)
				System.err.println("Testing to delay.");
				mInPacket = (new PacketBuilder()).constructPacket(mLastPacket);
				if (mInPacket.getBlockNumber() != this.mErrorSettings.getTransmissionErrorOccurences()
						|| mInPacket.getRequestType() != this.mErrorSettings.getTransmissionErrorType()
						|| this.mDelayPacketPerformed) {
					System.err.println(String.format("%d =? %d %d =? %d", mInPacket.getBlockNumber(),this.mPacketBlock, 
							mInPacket.getRequestType().getOptCode(), this.mErrorSettings.getTransmissionErrorType().getOptCode() ));
					return;
				}
				logger.print(Logger.ERROR,
						String.format("Attempting to delay a packet with op code %d.", inPacket.getData()[1]));
				// Delay a packet
				if (this.mPacketOpCode == RequestType.ERROR) {
					byte[] data = this.mLastPacket.getData();
					this.directPacketToDestination();
					data[1]+=10;
					this.mLastPacket.setData(data);
					try {
						forwardPacketToSocket(this.mLastPacket);
						this.mLastPacket = this.retrievePacketFromSocket();
						System.out.println("Delaying error packet.");
						TransmissionError transmissionError = new TransmissionError(this.mPacketSendQueue.pop(),
								this.mErrorSettings.getTransmissionErrorFrequency(), this);
						Thread delayPacketThread = new Thread(transmissionError);
						delayPacketThread.start();
						return;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				TransmissionError transmissionError = new TransmissionError(this.mPacketSendQueue.pop(),
						this.mErrorSettings.getTransmissionErrorFrequency(), this);
				Thread delayPacketThread = new Thread(transmissionError);
				delayPacketThread.start();
				System.out.println("Retreiving delayed packet?");
				this.mLastPacket = this.retrievePacketFromSocket();
				directPacketToDestination();
				System.out.println("Redirected delayed packet?");
				try {
					forwardPacketToSocket(this.mLastPacket);
					System.out.println("Forwwarded delayed packet?");
				} catch (IOException e) {
					System.err.println("Error catch entity timeouts form both sides during a delay.");
				}
				this.mDelayPacketPerformed = true;
				break;
			case 3:
				System.err.println("Testing to duplicate.");
				mInPacket = (new PacketBuilder()).constructPacket(this.mLastPacket);
				if (mInPacket.getBlockNumber() != this.mErrorSettings.getTransmissionErrorOccurences()
						|| mInPacket.getRequestType() != this.mErrorSettings.getTransmissionErrorType()
						|| this.mDuplicatePacketPerformed)
					return;
				logger.print(Logger.ERROR,
						String.format("Attempting to duplicate a packet with op code %d.", inPacket.getData()[1]));
				directPacketToDestination();
				
				if (this.mPacketOpCode == RequestType.ERROR) {
					byte[] data = this.mLastPacket.getData();
					this.directPacketToDestination();
					data[1]+=10;
					this.mLastPacket.setData(data);
					try {
						forwardPacketToSocket(this.mLastPacket);
						this.mLastPacket = this.retrievePacketFromSocket();
						this.directPacketToDestination();
						//DatagramPacket newPacket = new DatagramPacket(this.mLastPacket.getData(), this.mLastPacket.getLength(), 
						//		this.mLastPacket.getAddress(), this.mLastPacket.getPort());
						forwardPacketToSocket(this.mLastPacket);
						forwardPacketToSocket(this.mLastPacket);
						return;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
				
				try {
					directPacketToDestination();
					this.mLastPacket = this.mPacketSendQueue.peek();
					DatagramPacket duplicatePacket = new DatagramPacket(this.mLastPacket.getData(),
							this.mLastPacket.getLength(), this.mLastPacket.getAddress(), this.mLastPacket.getPort());
					this.forwardPacketToSocket(duplicatePacket);
					// This one is the correct one we want to save. We will take
					// care of this one and add to Q
					this.mLastPacket = this.retrievePacketFromSocket();
					directPacketToDestination();
					this.mPacketSendQueue.addLast(this.mLastPacket);

					// This one is a duplicate Packet, we don't need to forward
					// this one over.
					this.forwardPacketToSocket(this.mPacketSendQueue.pop());
					// This one is the duplicated packet response, we will just
					// forget about this one lol
					this.mLastPacket = this.retrievePacketFromSocket();
					// Set our correct ref to the one first correct response.
					this.mLastPacket = this.mPacketSendQueue.peek();
					this.mDuplicatePacketPerformed = true;
				} catch (IOException e) {
					e.printStackTrace();
				}

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
	 * A quick function to check if the current packet is an error
	 * 
	 * @param inPacket
	 *            - the packet to check
	 * @return true if it is an error, false otherwise
	 */
	private boolean packetIsError(DatagramPacket inPacket) {
		return inPacket.getData()[1] == 5;
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
		if (inUDPPacket.getPort() == this.mClientPort) {
			logger.print(Logger.VERBOSE, Strings.ES_SEND_PACKET_CLIENT);
		} else {
			logger.print(Logger.VERBOSE, Strings.ES_SEND_PACKET_SERVER);
		}
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
	private DatagramPacket retrievePacketFromSocket() {
		mBuffer = new byte[Configurations.MAX_BUFFER];
		DatagramPacket receivePacket = new DatagramPacket(mBuffer, mBuffer.length);
		while (true) {
			try {
				this.mSendReceiveSocket.receive(receivePacket);
				break;
			} catch (SocketTimeoutException e) {
				if (++this.mTransmissionRetries == Configurations.RETRANMISSION_TRY) {
					logger.print(Logger.ERROR, String.format(
							"Retransmission retried %d times, simulator operation considered done.", this.mTransmissionRetries));
					return null;
				}
				System.out.println("Time out caught.");
			} catch (IOException e) {
				logger.print(Logger.ERROR, "IOException during receive of packet");
			}
		}

		int realPacketSize = receivePacket.getLength();
		byte[] packetBuffer = new byte[realPacketSize];
		System.arraycopy(receivePacket.getData(), 0, packetBuffer, 0, realPacketSize);
		receivePacket.setData(packetBuffer);
		if (receivePacket.getPort() == this.mClientPort) {
			logger.print(Logger.VERBOSE, Strings.ES_RETRIEVE_PACKET_CLIENT);
		} else {
			logger.print(Logger.VERBOSE, Strings.ES_RETRIEVE_PACKET_SERVER);
		}
		BufferPrinter.printBuffer(receivePacket.getData(), CLASS_TAG, logger);
		return receivePacket;
	}

	/**
	 * This function is used to synchronize with the delay thread so that we use
	 * the same socket to send the packet
	 * 
	 * @param inPacket
	 *            - the packet to synchronize and send
	 */
	public void addWorkToFrontOfQueue(DatagramPacket inPacket) {
		logger.print(Logger.ERROR, "Inject delayed packet back into work queue!");
		this.mLastPacket = inPacket;
		directPacketToDestination();
		try {
			forwardPacketToSocket(this.mLastPacket);
		} catch (IOException e) {
			logger.print(Logger.ERROR,
					String.format(
							"Oops, you might have set your delay time for too long."
									+ " Current time out is %dms and you choose to delay for %dms.",
							Configurations.TRANMISSION_TIMEOUT, this.mErrorSettings.getTransmissionErrorFrequency()));
		}
	}

}
