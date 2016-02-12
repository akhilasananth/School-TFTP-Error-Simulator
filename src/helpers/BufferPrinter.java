package helpers;

import java.util.Arrays;

import packet.ErrorPacketBuilder;
import packet.PacketBuilder;
import packet.PacketBuilderFactory;
import packet.ReadPacketBuilder;
import packet.WritePacketBuilder;
import resource.Configurations;
import resource.Strings;
import types.Logger;
import types.RequestType;
/**
 * @author Team 3
 *
 * This is a printing class helper for the TFTP system
 */
public class BufferPrinter {
	
	// this method now only prints if the client/Error simulator/server was initialized
	// with a LogLevel VERBOSE 
	public static void printBuffer(byte[] buffer, String entity, Logger logLevel) {
		StringBuilder strBuilder = new StringBuilder();
		byte[] data = new byte[Configurations.MAX_MESSAGE_SIZE + 2];
		int length = 0;
		if(buffer.length < data.length) {
			length = buffer.length;
		} else {
			length = data.length;
		}
		System.arraycopy(buffer, 0, data, 0, length);
		strBuilder.append(entity + " prints contents of the UDP buffer:\n");
		strBuilder.append(Arrays.toString(data) + "\n");
		strBuilder.append(entity + " prints contents of UDP buffer as string: \n");
		strBuilder.append(new String(data) + "\n");
		logLevel.print(Logger.VERBOSE, strBuilder.toString());
	}
	
	public static void printPacket(PacketBuilder pb,Logger logger, RequestType requestType){
		
		PacketBuilderFactory pbf;
		
		switch (requestType) {
		case ACK:
			logger.print(Logger.VERBOSE, Strings.ACK_PACKET);
			
			break;
					
		case DATA:
			logger.print(Logger.VERBOSE, Strings.DATA_PACKET);
			
			break;
					
		case RRQ:
			logger.print(Logger.VERBOSE, Strings.RRQ);
			logger.print(Logger.VERBOSE, "File Name : " +  ((ReadPacketBuilder)pb).getFilename());
			break;
			
		case WRQ:
			logger.print(Logger.VERBOSE, Strings.WRQ);
			logger.print(Logger.VERBOSE, "File Name : " +  ((WritePacketBuilder)pb).getFilename());
			break;
			
		case ERROR:
			logger.print(Logger.VERBOSE, Strings.ERROR);
			logger.print(Logger.VERBOSE, ((ErrorPacketBuilder)pb).getCustomPackageErrorMessage());
			break;
			
		case NONE:
			logger.print(Logger.VERBOSE, Strings.NONE);
			
			break;

		default:
			logger.print(Logger.FATAL, Strings.INVALID_PACKET_NONE_TYPE);
			break;
		}
		
		logger.print(Logger.VERBOSE, "IP Address : " + pb.getPacket().getAddress());
		logger.print(Logger.VERBOSE, "Port : " + pb.getPacket().getPort());
		
		if(pb.getBlockNumber() >= 0){
			logger.print(Logger.VERBOSE, "Block # : " + pb.getBlockNumber());
		}
		
		logger.print(Logger.VERBOSE, "Packet length : " + pb.getPacketLength());
		logger.print(Logger.VERBOSE, "Raw packet value : " + Arrays.toString(pb.getPacketBuffer()) );
		logger.print(Logger.VERBOSE, "String value : " + bufferToString(pb.getPacketBuffer()) );
		
	}
	
	private static String bufferToString(byte[] buffer) {
		StringBuilder strBuilder = new StringBuilder();
		for(int i = 0; i< buffer.length; i++) {
			char value = (char)buffer[i];
			if (Character.isLetter(value)) {
				strBuilder.append(value);
			}
			else if (Character.isDigit(value)) {
				strBuilder.append(value);
			}
			else {
				strBuilder.append(buffer[i]);
			}
		}
		return strBuilder.toString();
	}
	
	public static String acceptConnectionMessage(String message, String senderAddress) {
		return message + " " + senderAddress + ".";
	}
}
