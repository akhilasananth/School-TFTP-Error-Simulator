/**
 * 
 */
package client;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Scanner;

import helpers.FileStorageService;
import helpers.Keyboard;
import packet.*;
import resource.*;
import types.*;

/**
 * @author Team 3
 *
 */
public class TFTPClient {

	private DatagramSocket sendReceiveSocket;
	private boolean isClientAlive = true;
	
	public static void main(String[] args) {
		TFTPClient vClient = new TFTPClient();
		vClient.initialize();
	}
	
	public TFTPClient() {
		
	}
	
	public void initialize() {
		Scanner scan = new Scanner(System.in);
		try {
			sendReceiveSocket = new DatagramSocket();
			int optionSelected = 0;
			
			while (isClientAlive) {
				printSelectMenu();
				
				optionSelected = Keyboard.getInteger();
				switch (optionSelected) {
				case 1:
					// Read file
					System.out.println(Strings.PROMPT_ENTER_FILE_NAME);

					String readFileName = Keyboard.getString();
					try {
						readRequestHandler(readFileName);
					} catch (Exception e) {
						e.printStackTrace();
					}
					break;
				case 2:
					// Write file
					System.out.println(Strings.PROMPT_FILE_NAME_PATH);

					String writeFileNameOrFilePath = Keyboard.getString();
					writeRequestHandler(writeFileNameOrFilePath);
					break;
				case 3:
					// shutdown client
					isClientAlive = false;
					System.out.println(Strings.EXIT_BYE);
					break;

				default:
					System.out.println(Strings.ERROR_INPUT);
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
	private void writeRequestHandler(String writeFileNameOrFilePath) {

		ReadWritePacketPacketBuilder wpb;
		FileStorageService writeRequestFileStorageService;
		DataPacketBuilder dataPacket; 
		DatagramPacket lastPacket;
		byte[] fileData;
		byte[] ackBuff = new byte[Configurations.LEN_ACK_PACKET_BUFFET];
		boolean moreData = true;

		try {
			writeRequestFileStorageService = new FileStorageService(writeFileNameOrFilePath,InstanceType.CLIENT);
			
			String actualFileName = writeRequestFileStorageService.getFileName();
			wpb = new WritePacketBuilder(InetAddress.getLocalHost(), Configurations.ERROR_SIM_LISTEN_PORT,
					actualFileName, Configurations.DEFAULT_RW_MODE);
			lastPacket = wpb.buildPacket();
			sendReceiveSocket.send(lastPacket);
			
			while (moreData) {
				// This packet has the block number to start on!
				lastPacket = new DatagramPacket(ackBuff, ackBuff.length);
				
				// receive a ACK packet
				sendReceiveSocket.receive(lastPacket);
				// Check if the receiving packet is an ACK
				
				fileData = new byte[Configurations.MAX_BUFFER];
				// get the first block of file to transfer
				moreData = writeRequestFileStorageService.getFileByteBufferFromDisk(fileData);
				
				// Initialize DataPacket with block number n
				dataPacket = new DataPacketBuilder(lastPacket);
				
				// Overwrite last packet
				lastPacket = dataPacket.buildPacket(fileData);
				lastPacket.setPort(Configurations.ERROR_SIM_LISTEN_PORT);
				sendReceiveSocket.send(lastPacket);
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * This function create a read request for the client and stores the file
	 * retrieved from the server on to the file system
	 * 
	 * @param readFileName
	 *            - the name of the file that the client requests from server
	 */
	private void readRequestHandler(String readFileName) throws Exception {

		AckPacketBuilder ackPacketBuilder;
		DatagramPacket lastPacket;
		DataPacketBuilder dataPacketBuilder;
		boolean morePackets = true;
		FileStorageService readRequestFileStorageService;

		// +4 for the opcode and block#
		byte[] dataBuf = new byte[Configurations.MAX_MESSAGE_SIZE];

		try {
			readRequestFileStorageService = new FileStorageService(readFileName,InstanceType.CLIENT);
			// build read request packet
			ReadPacketBuilder rpb = new ReadPacketBuilder(InetAddress.getLocalHost(), Configurations.SERVER_LISTEN_PORT,
					readFileName, Configurations.DEFAULT_RW_MODE);

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
				
				byte[] fileData = dataPacketBuilder.getDataBuffer();
				// We need trim the byte array
				if(fileData[fileData.length-1] == 0) {
					// Give this a trim. 
					int indexOfZero = fileData.length;
					// Find the first index of occurring 0
					while(--indexOfZero > 0 && fileData[indexOfZero] == 0) {}
					byte[] temp = fileData;
					fileData = new byte[indexOfZero+1];
					System.arraycopy(temp, 0, fileData, 0, indexOfZero+1);
				}
				
				// Save the last packet file buffer
				morePackets = readRequestFileStorageService.saveFileByteBufferToDisk(fileData);

				// Prepare to ACK the data packet
				ackPacketBuilder = new AckPacketBuilder(lastPacket);
				lastPacket = ackPacketBuilder.buildPacket();
				sendReceiveSocket.send(lastPacket);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * 
	 * This function only prints the client side selection menu
	 * 
	 * @param
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
