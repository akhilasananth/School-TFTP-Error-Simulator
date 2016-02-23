/**
 * 
 */
package client;

import java.io.File;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Scanner;

import helpers.BufferPrinter;
import helpers.FileStorageService;
import helpers.Keyboard;
import networking.TFTPNetworking;
import packet.*;
import resource.*;
import testbed.ErrorChecker;
import testbed.TFTPErrorMessage;
import types.*;

/**
 * @author Team 3
 *
 *         This class represents a TFTP console application for interfacing with
 */
public class TFTPClient {

	private DatagramSocket sendReceiveSocket;
	private boolean isClientAlive = true;
	private final String CLASS_TAG = "<TFTP Client>";
	private int mPortToSendTo;

	private int mode;

	// by default the logger is set to VERBOSE level
	private Logger logger = Logger.VERBOSE;

	// Error checker
	ErrorChecker errorChecker = null;

	public static void main(String[] args) {
		TFTPClient vClient = new TFTPClient();
		vClient.initialize();
	}

	/**
	 * This function initializes the client's functionality and block the rest
	 * of the program from running until a exit command was given.
	 */
	public void initialize() {
		logger.setClassTag(this.CLASS_TAG);
		Scanner scan = new Scanner(System.in);
		TFTPNetworking net = null;
		try {
			mode = getSendPort();
			if (mode == 1) {
				this.mPortToSendTo = Configurations.SERVER_LISTEN_PORT;
			} else {
				this.mPortToSendTo = Configurations.ERROR_SIM_LISTEN_PORT;
			}
			setLogLevel();

			int optionSelected = 0;

			while (isClientAlive) {
				System.out.println(UIStrings.MENU_CLIENT_SELECTION);

				try {
					optionSelected = Keyboard.getInteger();
				} catch (NumberFormatException e) {
					optionSelected = 0;
				}
				errorChecker = null;
				switch (optionSelected) {
				case 1:
					// Read file
					net = new TFTPNetworking();
					logger.print(logger, Strings.PROMPT_ENTER_FILE_NAME);
					String readFileName = Keyboard.getString();
					try {
						//TFTPErrorMessage result = readRequestHandler(readFileName);
						net.generateInitRRQ(readFileName, this.mPortToSendTo);
						TFTPErrorMessage result = net.receiveFile();
						if (result.getType() != ErrorType.NO_ERROR) {
							logger.print(Logger.ERROR, Strings.TRANSFER_FAILED);
							logger.print(Logger.ERROR, result.getString());
							if (!sendReceiveSocket.isClosed()) {
								sendReceiveSocket.close();
							}
						} else {
							logger.print(Logger.VERBOSE, Strings.TRANSFER_SUCCESSFUL);
						}
					} catch (Exception e) {
						if (logger == Logger.VERBOSE)
							e.printStackTrace();

						logger.print(Logger.ERROR, Strings.TRANSFER_FAILED);
					}
					break;
				case 2:
					// Write file
					net = new TFTPNetworking();
					logger.print(logger, Strings.PROMPT_FILE_NAME_PATH);
					String writeFileNameOrFilePath = Keyboard.getString();
					TFTPErrorMessage result = null;
					File f = new File(writeFileNameOrFilePath);
					if (!f.exists() || f.isDirectory()) {
						logger.print(logger, Strings.FILE_NOT_EXIST);
						break;
					}
					DatagramPacket packet = net.generateInitWRQ(writeFileNameOrFilePath, this.mPortToSendTo);
					if (packet != null)
						result = net.sendFile();
					else System.exit(1);
					if (!(result.getType() == ErrorType.NO_ERROR)) {
						logger.print(Logger.ERROR, Strings.TRANSFER_FAILED);
						logger.print(Logger.ERROR, result.getString());
					} else {
						logger.print(Logger.VERBOSE, Strings.TRANSFER_SUCCESSFUL);
					}
					break;
				case 3:
					// shutdown client
					isClientAlive = !isClientAlive;
					logger.print(Logger.VERBOSE, Strings.EXIT_BYE);
					break;

				default:
					logger.print(Logger.ERROR, Strings.ERROR_INPUT);
					break;
				}
			}
		} finally {
			scan.close();
		}
	}

	/**
	 * This function sets the client to use the error simulator or not
	 * 
	 * @return mode 1 is do not use and 2 is use.
	 */
	private int getSendPort() {
		while (true) {
			System.out.println(UIStrings.CLIENT_MODE);

			int mode = Keyboard.getInteger();

			if (mode == 1) {
				return mode;
			} else if (mode == 2) {
				return mode;
			} else {
				logger.print(Logger.ERROR, Strings.ERROR_INPUT);
			}

		}
	}

	/**
	 * This function only prints the client side selection menu
	 */
	private void setLogLevel() {

		int optionSelected;

		while (true) {
			System.out.println(UIStrings.CLIENT_LOG_LEVEL_SELECTION);

			try {
				optionSelected = Keyboard.getInteger();
			} catch (NumberFormatException e) {
				optionSelected = 0;
			}

			if (optionSelected == 1) {
				this.logger = Logger.VERBOSE;
				break;
			} else if (optionSelected == 2) {
				this.logger = Logger.SILENT;
				break;
			} else {
				logger.print(Logger.ERROR, Strings.ERROR_INPUT);
			}
		}
	}
}
