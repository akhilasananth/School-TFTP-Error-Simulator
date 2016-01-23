package helpers;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;

import resource.*;

/**
 * @author Team 3
 * 
 */
public class FileStorageService {
	
	private String mFilePath = "";
	private String mFileName = "";
	private long mBytesProcessed = 0;
	private String mDefaultStorageFolder = "";
	
	// File utility classes
	RandomAccessFile mFile = null;
	FileChannel mFileChannel = null;

	/**
	 *  This file encapsulates all disk IO operations that is required to 
	 *	read and write files. This classes should be created and destroyed 
	 *	each time the client class needs to operate on one file
	 *
	 * @param fileName - given to initialize this class for use on one file
	 * @throws FileNotFoundException
	 */
	public FileStorageService(String fileNameOrFilePath) throws FileNotFoundException {
		initializeFileServiceStorageLocation();
		initializeNewFileChannel(fileNameOrFilePath);
	}
	
	/**
	 * This function checks if the default folder to save TFTP files exists, if 
	 * not, creates one.
	 */
	private void initializeFileServiceStorageLocation() {
		this.mDefaultStorageFolder = Paths.get(Configurations.ROOT_FILE_DIRECTORY).toString();
		File storageDirectory = new File(this.mDefaultStorageFolder);
		if(!storageDirectory.exists()) {
			if(!storageDirectory.mkdir()) {
				// Flag error for File IO
				System.out.println(Strings.DIRECTORY_MAKE_ERROR);
			}
		}
	}
	
	/**
	 * Initializer for this class. It takes care of creating a default directory in system home
	 * if not made, also opens the file stream channels to perform non-blocking IO towards the 
	 * context file. This function will support handling multiple files only if a previous file
	 * operation has fully completed without error.
	 * 
	 * Two operation types:
	 * 	+ Given Full Path
	 * 		This class will overwrite or create the full valid file name. Client class uses this
	 * 		option to open a channel to the file they want to READ. 
	 * 		The client class should NOT give a full path if they want to WRITE because this 
	 * 		operation should write to the default Configurations.ROOT_FILE_DIRECTORY
	 * 	+ Given File Name
	 * 		This class will only search for the file name inside the Configurations.ROOT_FILE_DIRECTORY
	 * 		folder. If the file exists, it will read it, or overwrite it. 
	 * 		The client classes using this service should only give file name if they want to 
	 * 		operate on the default directory. 
	 * 
	 * @param fileName - passed in through the constructor
	 * @throws FileNotFoundException
	 */
	public void initializeNewFileChannel(String filePathOrFileName) throws FileNotFoundException {
		if(checkFileNameExists(filePathOrFileName)) {
			this.mFileName = Paths.get(filePathOrFileName).getFileName().toString();
			if(this.mFileName == "") {
				// No filename in the path!
				throw new FileNotFoundException();
			}
			this.mFilePath = Paths.get(filePathOrFileName).toString();
		} else {
			// So its not a file path, maybe its a file name, so we try it out
			this.mFileName = filePathOrFileName;
			this.mFilePath = Paths.get(this.mDefaultStorageFolder, this.mFileName).toString();
		}
		this.mBytesProcessed = 0;
		// Open or create a our file name path and create a channel for us to access the file on
		this.mFile = new RandomAccessFile(this.mFilePath, "rw");
		this.mFileChannel = this.mFile.getChannel();
	}
	
	/**
	 * This function will save the byte buffer given by the TFTPPacket message segment and write
	 * each block into disk. It remembers where the last segment left off and will return false
	 * when the operation is done. It will return true if it thinks there is more buffer to write.
	 * In such case, the server is meant to be getting a fileBuffer lengthed zero to terminate.
	 * 
	 * @param fileBuffer - 512 bytes of file content sent over in the TFTPPacket
	 * @return boolean - if the file has been fully saved or not
	 */
	public boolean saveFileByteBufferToDisk(byte[] fileBuffer) {
		if(fileBuffer == null) {
			return false;
		}
		int bytesWritten = 0;
		// Try to write the bytes to disk by wrapping byte[] into a ByteBuffer
		try {
			bytesWritten = this.mFileChannel.write(ByteBuffer.wrap(fileBuffer), this.mBytesProcessed);
		} catch (IOException e) {
			System.out.println(Strings.FILE_WRITE_ERROR + " " + this.mFileName);
			e.printStackTrace();
			return false;
		}
		// Increment processed, next round, continue where we left off
		this.mBytesProcessed += bytesWritten;
		
		// Check if we received a length zero
		if(fileBuffer.length == 0 || bytesWritten < Configurations.MAX_BUFFER) {
			System.out.println(Strings.FILE_WRITE_COMPLETE);
			try {
				this.mFileChannel.close();
				this.mFile.close();
			} catch (IOException e) {
				System.out.println(Strings.FILE_CHANNEL_CLOSE_ERROR);
				e.printStackTrace();
			}
			return false;
		}
		return true;
	}
	
	/**
	 * This function fills an array of bytes with 512 bytes of file content. The function remember the
	 * last position it left off so it may resume from that index when called again. The function 
	 * will return false, where there is no more file to be read and true when there is still more.
	 * Make sure this function ends before re-using this class
	 * 
	 * @param inByteBufferToFile - an initialized empty byte array sized 512 bytes
	 * @return boolean - if there is or is not any more file content to buffer 
	 */
	public boolean getFileByteBufferFromDisk(byte[] inByteBufferToFile) {
		if(inByteBufferToFile == null) {
			return false;
		}
		
		ByteBuffer fileBuffer = ByteBuffer.allocate(Configurations.MAX_BUFFER);
		int bytesRead = 0;
		try {
			bytesRead = this.mFileChannel.read(fileBuffer, this.mBytesProcessed);
		} catch (IOException e) {
			System.out.println(Strings.FILE_READ_ERROR + " " + this.mFileName);
			e.printStackTrace();
			return false;
		}
		
		if(fileBuffer.position() < fileBuffer.capacity()) {
			// We know we reached end of file as buffer was not 512 bytes
			fileBuffer.compact();
		}
		// Increment the total number number of bytes processed
		this.mBytesProcessed += bytesRead;
		// Fill the input parameter
		inByteBufferToFile = fileBuffer.array();
		
		// We determine if we reached the end of the file
		if(bytesRead == 0 || bytesRead < Configurations.MAX_BUFFER) {
			// We found the end of the file, or something bad happened where we cannot
			// read anymore of the file
			System.out.println(Strings.FILE_WRITE_COMPLETE);
			try {
				this.mFileChannel.close();
				this.mFile.close();
			} catch (IOException e) {
				System.out.println(Strings.FILE_CHANNEL_CLOSE_ERROR);
				e.printStackTrace();
			}
			return false;
		}
		return true;
	}
	
	/**
	 * Static method that can be used to check if a file exists within the TFTP system 
	 * 
	 * @param fileName 
	 * @return boolean - if the file exists and is not a directory
	 */
	public static boolean checkFileNameExists(String filePathName) {
		String filePath = Paths.get(filePathName).toString();
		File fileToCheck = new File(filePath);
		return fileToCheck.exists() && !fileToCheck.isDirectory();
	}
	
	/**
	 * This function should be called if you want to use the same instance of this class in
	 * the event that any error occurred during writing or reading of a file. 
	 * This function will close the current broken channels and the client class must call
	 * initializeNewFileChannel(String) to reset the file channels
	 */
	public void finishedTransferingFile() {
		try {
			if(this.mFileChannel.isOpen()) {
				this.mFileChannel.close();
			}
			this.mFile.close();		
			this.mFile = null;
			this.mFileChannel = null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}

/*

Thought I may as well include my tests in this submission


*/
//public static void main(String args[]) {
//	int pos = 0;
//	try {
//		FileStorageService s = new FileStorageService("bluebase.PNG");
//		RandomAccessFile f = new RandomAccessFile("c:\\Users\\CZL\\Pictures\\bluebase.PNG", "r");
//		System.out.println((int)f.length());
//		byte[] b = new byte[(int)f.length()];
//		f.read(b);
//		int len = (int)f.length();
//		pos = 0;
//		while(len >= 0) {
//			int z = 512;
//			if(len < 512) {
//				z = len;
//			}
//			byte[] a = new byte[z+1];
//			System.arraycopy(b, pos, a, 0, z);
//			s.saveFileByteBufferToDisk(a);
//			len -=  512;
//			pos += z;
//			
//		}
//
//		
//	} catch (IOException e) {
//		System.out.println(pos);
//		e.printStackTrace();
//	}
//	try {
//		FileStorageService t = new FileStorageService("bluebase.PNG");
//		pos = 0;
//		while(true) {
//			byte[] bytes = new byte[512];
//			if(t.getFileByteBufferFromDisk(bytes)) {
//				pos++;
//				System.out.println(pos);
//			}else {
//				break;
//			}
//		}
//
//	} catch (FileNotFoundException e) {
//		// TODO Auto-generated catch block
//		e.printStackTrace();
//	}
//	
//	
//}
