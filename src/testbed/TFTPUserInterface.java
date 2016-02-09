package testbed;

import java.util.Scanner;

import helpers.Keyboard;
import resource.Strings;
import resource.UIStrings;
import types.ErrorType;
import types.Logger;
import resource.Tuple;

public class TFTPUserInterface {

	//by default set the log level to debug
	private static Logger logger = Logger.VERBOSE;
	private Scanner mScan;
	private int mUserErrorOption = 0;
	private int mUserErrorSubOption = 0;
	
	public TFTPUserInterface() {
		this.mScan = new Scanner(System.in);
		this.mUserErrorOption = 0;
		this.mUserErrorSubOption = 0;
	}
	
	/**
	 * This function prints out error selections for client
	 */
	private void printErrorSelectMenu() {
		logger.print(Logger.VERBOSE, UIStrings.MENU_ERROR_SIMULATOR_ERROR_SELECTION);
	}
	
	private void printIllegalTFTPOperation() {
		logger.print(Logger.VERBOSE, UIStrings.MENU_ERROR_SIMULATOR_ILLEGAL_TFTP_OPERATION);
	}
	
	public Tuple<ErrorType,Integer> getErrorCodeFromUser() {
		int optionSelected = 0;
		boolean validInput = false;
		
		while(!validInput){
			System.out.println(UIStrings.MENU_ERROR_SIMULATOR_ERROR_SELECTION);
			try {
				optionSelected = Keyboard.getInteger();
			} catch (NumberFormatException e) {
				optionSelected = 0;
			}
			
			switch (optionSelected) {
			case 1:
				// file not found
				logger.print(Logger.DEBUG,Strings.OPERATION_NOT_SUPPORTED);
				validInput = true;
				this.printLoggerSelection();
				break;
			case 2:
				// Access violation
				logger.print(Logger.DEBUG,Strings.OPERATION_NOT_SUPPORTED);
				validInput = true;
				this.printLoggerSelection();
				break;
			case 3:
				// Disk full or allocation exceeded
				logger.print(Logger.DEBUG,Strings.OPERATION_NOT_SUPPORTED);
				validInput = true;
				this.printLoggerSelection();
				break;
			case 4:
				// illegal TFTP operation option
				this.mUserErrorOption = 4;
				//printIllegalTFTPOperation();
				this.getSubOption(UIStrings.MENU_ERROR_SIMULATOR_ILLEGAL_TFTP_OPERATION, 5);
				if (this.mUserErrorSubOption == 5) {
					// go back to the previous level
					this.mUserErrorSubOption = 0;
					validInput = false;
				}else{
					validInput = true;
				}
				break;
			case 5:
				// unknown transfer ID operation option
				this.mUserErrorOption = 5;
				this.mUserErrorSubOption = 0;
				validInput = true;
				break;
			case 6:
				// File already exists
				logger.print(Logger.DEBUG,Strings.OPERATION_NOT_SUPPORTED);
				validInput = true;
				this.printLoggerSelection();
				break;
			case 7:
				// No such user
				logger.print(Logger.DEBUG,Strings.OPERATION_NOT_SUPPORTED);
				validInput = true;
				this.printLoggerSelection();
				break;
			case 8:
				// No error
				System.out.println(Strings.EXIT_BYE);
				validInput = true;
				this.printLoggerSelection();
				break;
			case 9:
				// Go back to the previous menu
				this.printLoggerSelection();
				validInput = true;
				break;
			default:
				System.out.println(Strings.ERROR_INPUT);
				break;
			}
		}
		return new Tuple<ErrorType, Integer>(
				ErrorType.matchErrorByNumber(this.mUserErrorOption), 
				this.mUserErrorSubOption);
	}
	
	public Logger printLoggerSelection() {
		int optionSelected = 0;
		boolean validInput = false;
		
		while(!validInput){
			printSelectLogLevelMenu();
			
			try {
				optionSelected = Keyboard.getInteger();
			} catch (NumberFormatException e) {
				optionSelected = 0;
			}
			
			switch (optionSelected) {
			case 1:
				logger = Logger.VERBOSE;
				validInput = true;
				break;
			case 2:
				logger = Logger.DEBUG;
				validInput = true;
				break;
			default:
				System.out.println(Strings.ERROR_INPUT);
				break;
			}					
		}
		return logger;
	}
	private static void printSelectLogLevelMenu() {
		System.out.println(UIStrings.MENU_ERROR_SIMULATOR_LOG_LEVEL);
	}
	
	/**
	 * This function get user's sub-option for sub-error menu
	 * @param s - the string you want to prompt user
	 * @param max - the maximum valid input 
	 */
	private void getSubOption(String s, int max) {
		int subOpt;
		boolean validInput = false;
			
		while (!validInput) {
			// print out the message
			System.out.println(s);
			try {
				// get input
				subOpt = Keyboard.getInteger();
			} catch (NumberFormatException e) {
				subOpt = 0;
			}
			for(int i=1; i<=max; i++) {
				if(subOpt == i) {
					// validate the input
					validInput = true;
					this.mUserErrorSubOption = subOpt;
				}
			}
		}
	}
}
