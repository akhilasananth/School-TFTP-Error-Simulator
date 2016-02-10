/**
 * 
 */
package client;

import java.io.File;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Scanner;

import helpers.FileStorageService;
import helpers.Keyboard;
import packet.*;
import resource.*;
import testbed.ErrorChecker;
import testbed.TFTPError;
import types.*;

/**
 * @author Team 3
 *
 */
public class TFTPClient {

	private DatagramSocket sendReceiveSocket;
	private boolean isClientAlive = true;
	private final String CLASS_TAG = "<TFTP Client>";
	private int mPortToSendTo;

	// by default the logger is set to DEBUG level
	private Logger logger = Logger.VERBOSE;

	// Error checker
	ErrorChecker errorChecker = null;

	public static void main(String[] args) {
		TFTPClient vClient = new TFTPClient();
		vClient.initialize();
	}

	public TFTPClient() {
		logger.setClassTag(this.CLASS_TAG);
	}

	/**
	 * This function initializes the client's functionality and block the rest
	 * of the program from running until a exit command was given.
	 */
	public void initialize() {
		Scanner scan = new Scanner(System.in);
		try {

			int mode = getSendPort();
			if (mode == 1) {
				this.mPortToSendTo = Configurations.SERVER_LISTEN_PORT;
			} else {
				this.mPortToSendTo = Configurations.ERROR_SIM_LISTEN_PORT;
			}
			sendReceiveSocket = new DatagramSocket();
			int optionSelected = 0;

			while (isClientAlive) {
				printSelectMenu();

				try {
					optionSelected = Keyboard.getInteger();
				} catch (NumberFormatException e) {
					optionSelected = 0;
				}

				switch (optionSelected) {
				case 1:
					// Read file
					logger.print(Logger.DEBUG, Strings.PROMPT_ENTER_FILE_NAME);
					String readFileName = Keyboard.getString();
					try {
						TFTPError result = readRequestHandler(readFileName);
						if (!(result.getType() == ErrorType.NO_ERROR)) {
							logger.print(Logger.ERROR, Strings.TRANSFER_FAILED);
							logger.print(Logger.ERROR, result.getString());
						} else {
							logger.print(Logger.DEBUG, Strings.TRANSFER_SUCCESSFUL);
						}

					} catch (Exception e) {
						if (logger == Logger.VERBOSE)
							e.printStackTrace();

						logger.print(Logger.ERROR, Strings.TRANSFER_FAILED);
					}
					break;
				case 2:
					// Write file
					logger.print(Logger.DEBUG, Strings.PROMPT_FILE_NAME_PATH);
					String writeFileNameOrFilePath = Keyboard.getString();
					File f = new File(writeFileNameOrFilePath);
					if (!f.exists() || f.isDirectory()) {
						logger.print(Logger.DEBUG, Strings.FILE_NOT_EXIST);
						break;
					}
					TFTPError result = writeRequestHandler(writeFileNameOrFilePath);
					if (!(result.getType() == ErrorType.NO_ERROR)) {
						logger.print(Logger.ERROR, Strings.TRANSFER_FAILED);
						logger.print(Logger.ERROR, result.getString());
					} else {
						logger.print(Logger.DEBUG, Strings.TRANSFER_SUCCESSFUL);
					}
					break;
				case 3:
					// shutdown client
					isClientAlive = false;
					logger.print(Logger.DEBUG, Strings.EXIT_BYE);
					break;

				default:
					logger.print(Logger.ERROR, Strings.ERROR_INPUT);
					break;
				}
			}
		} catch (SocketException e) {
			e.printStackTrace();
			scan.close();
		} finally {
			scan.close();
		}
	}

	/**
	 * This function create the write request for a client and transfers the
	 * file from local disc to the server in 512 Byte blocks
	 * 
	 * @param writeFileName
	 *            - the name of the file that the client requests to send to
	 *            server
	 */
	private TFTPError writeRequestHandler(String writeFileNameOrFilePath) {

		ReadWritePacketPacketBuilder wpb;
		FileStorageService writeRequestFileStorageService;
		DataPacketBuilder dataPacket;
		AckPacketBuilder ackPacket;
		DatagramPacket lastPacket;
		byte[] fileData = new byte[Configurations.MAX_BUFFER];
		byte[] packetBuffer;
		try {
			writeRequestFileStorageService = new FileStorageService(writeFileNameOrFilePath, InstanceType.CLIENT);

			String actualFileName;

			actualFileName = writeRequestFileStorageService.getFileName();
			wpb = new WritePacketBuilder(InetAddress.getLocalHost(), this.mPortToSendTo, actualFileName,
					Configurations.DEFAULT_RW_MODE);

			lastPacket = wpb.buildPacket();
			sendReceiveSocket.send(lastPacket);

			while (fileData != null && fileData.length >= Configurations.MAX_BUFFER) {
				// This packet has the block number to start on!
				packetBuffer = new byte[Configurations.MAX_BUFFER];
				lastPacket = new DatagramPacket(packetBuffer, packetBuffer.length);

				// receive a ACK packet
				sendReceiveSocket.receive(lastPacket);

				// get the first block of file to transfer
				fileData = writeRequestFileStorageService.getFileByteBufferFromDisk();

				// Initialize DataPacket with block number n
				ackPacket = new AckPacketBuilder(lastPacket);

				if (errorChecker == null) {
					errorChecker = new ErrorChecker(ackPacket);
					// errorChecker.incrementexpectedBlockNumber();
				}

				TFTPError currErrorType = errorChecker.check(ackPacket, RequestType.ACK);
				if (currErrorType.getType() != ErrorType.NO_ERROR) {
					errorChecker = null;
					return currErrorType;
				}

				// Overwrite last packet
				dataPacket = new DataPacketBuilder(lastPacket);
				lastPacket = dataPacket.buildPacket(fileData);
				sendReceiveSocket.send(lastPacket);
			}
			// Receive the last ACK.
			packetBuffer = new byte[Configurations.LEN_ACK_PACKET_BUFFER];
			lastPacket = new DatagramPacket(packetBuffer, packetBuffer.length);
			sendReceiveSocket.receive(lastPacket);

		} catch (Exception e) {
			e.printStackTrace();
			errorChecker = null;
			return new TFTPError(ErrorType.NOT_DEFINED, "Exception thrown");
		}
		errorChecker = null;
		return new TFTPError(ErrorType.NO_ERROR, "no errors");
	}

	/**
	 * This function create a read request for the client and stores the file
	 * retrieved from the server on to the file system
	 * 
	 * @param readFileName
	 *            - the name of the file that the client requests from server
	 */
	private TFTPError readRequestHandler(String readFileName) throws Exception {

		AckPacketBuilder ackPacketBuilder;
		DatagramPacket lastPacket;
		DataPacketBuilder dataPacketBuilder;
		boolean morePackets = true;
		FileStorageService readRequestFileStorageService;

		// +4 for the opcode and block#
		byte[] dataBuf = new byte[Configurations.MAX_MESSAGE_SIZE];

		try {
			readRequestFileStorageService = new FileStorageService(readFileName, InstanceType.CLIENT);
			// build read request packet

			ReadPacketBuilder rpb;

			rpb = new ReadPacketBuilder(InetAddress.getLocalHost(), this.mPortToSendTo, readFileName,
					Configurations.DEFAULT_RW_MODE);

			// now get the packet from the ReadPacketBuilder
			lastPacket = rpb.buildPacket();

			// send the read packet over sendReceiveSocket
			sendReceiveSocket.send(lastPacket);

			// loop until no more packets to receive
			while (morePackets) {
				dataBuf = new byte[Configurations.MAX_MESSAGE_SIZE];
				lastPacket = new DatagramPacket(dataBuf, dataBuf.length);

				// receive a data packet
				sendReceiveSocket.receive(lastPacket);
				
				// Use the packet builder class to manage and extract the data
				dataPacketBuilder = new DataPacketBuilder(lastPacket);

				if (errorChecker == null) {
					errorChecker = new ErrorChecker(dataPacketBuilder);
				}

				TFTPError currErrorType = errorChecker.check(dataPacketBuilder, RequestType.DATA);
				if (currErrorType.getType() != ErrorType.NO_ERROR) {
					errorChecker = null;
					return currErrorType;
				}

				byte[] fileData = dataPacketBuilder.getDataBuffer();
				// We need trim the byte array

				// Save the last packet file buffer
				morePackets = readRequestFileStorageService.saveFileByteBufferToDisk(fileData);

				// Prepare to ACK the data packet
				ackPacketBuilder = new AckPacketBuilder(lastPacket);
				// Always send the ACK back to the error sim (BAD)

				lastPacket = ackPacketBuilder.buildPacket();
				sendReceiveSocket.send(lastPacket);
			}
		} catch (Exception e) {
			e.printStackTrace();
			errorChecker = null;
			return new TFTPError(ErrorType.NOT_DEFINED, "Exception thrown");
		}
		errorChecker = null;
		return new TFTPError(ErrorType.NO_ERROR, "no errors");
	}

	
	
	private int getSendPort() {
		while (true) {
			System.out.println("--------------------------------");
			System.out.println("| Select Client operation Mode |");
			System.out.println("--------------------------------");
			System.out.println("Options : ");
			System.out.println("\t 1. Normal (No Error Simulator)");
			System.out.println("\t 2. Test (With Error Simulator)");
			System.out.println("Select option : ");

			int mode = Keyboard.getInteger();

			if (mode == 1) {
				return mode;
			} else if (mode == 2) {
				return mode;
			} else {
				System.out.println("Invalid input !!");
			}

		}
	}

	/**
	 * This function only prints the client side selection menu
	 */
	public void printSelectMenu() {
		System.out.println("----------------------");
		System.out.println("| Client Select Menu |");
		System.out.println("----------------------");
		System.out.println("Options : ");
		System.out.println("\t 1. Read File");
		System.out.println("\t 2. Write File");
		System.out.println("\t 3. Exit File\n\n");
		System.out.println("Select option : ");
	}
}
