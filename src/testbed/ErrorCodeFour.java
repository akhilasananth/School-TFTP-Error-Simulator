package testbed;

import java.net.*;

import helpers.Conversion;
import packet.AckPacketBuilder;
import packet.PacketBuilder;
import packet.DataPacketBuilder;
import types.*;

public class ErrorCodeFour extends ErrorCodeSimulator {
	private int mSubcode;
	private DatagramPacket mSendPacket;
	private RequestType rt = super.receivePacketBuilder.getRequestType();
	private boolean readWriteCheck = (rt == RequestType.RRQ || rt == RequestType.WRQ);
	private byte[] readWriteBuffer = super.receivePacketBuilder.getDataBuffer();
	private int packetCount = 0;

	public ErrorCodeFour(DatagramPacket receivePacket, int subcode) {
		super(receivePacket);
		this.packetCount++;
		this.mSubcode = subcode;
	}

	public DatagramPacket errorPacketCreator() {
		this.mSendPacket = null;
		// MISSING PACKET TOO LARGE > 516 bytes
		switch (this.mSubcode) {

		case 1: // Change filename
			if (readWriteCheck) {
				// Settings and invalid file name
				super.receivePacketBuilder.setFilename("*:?.txt");
				this.mSendPacket = super.receivePacketBuilder.buildPacket();
			}
			break;
		case 2: // Change mode
			if (readWriteCheck) {
				super.receivePacketBuilder.setMode(switchMode());
				this.mSendPacket = super.receivePacketBuilder.buildPacket();
			}
			break;
		case 3: // Change number of 0s in the header
			if (readWriteCheck) {
				super.receivePacketBuilder.setDataBuffer(addZerosToBuffer());
				super.receivePacketBuilder.getPacket().setData(super.receivePacketBuilder.getDataBuffer());
				this.mSendPacket = super.receivePacketBuilder.getPacket();
			}
		case 4: // Change block number
			if (rt == RequestType.ACK || rt == RequestType.DATA) {
				// The following build packet will automatically increment block number by 1, which
				// effectively mismatches the block number
				this.mSendPacket = ((DataPacketBuilder)receivePacketBuilder).buildPacket(receivePacketBuilder.getDataBuffer());
			} else {
				this.mSendPacket = super.receivePacketBuilder.getPacket();
			}
			break;
		case 5:// Change header request type
			this.changeHeader(super.receivePacketBuilder);
			break;
		case 6:// Change packet size
			this.invalidPacketSize(super.receivePacketBuilder);
			break;
		case 7: // changing header of first packet received from client
			if (this.packetCount == 1) {
				this.changeHeader(super.receivePacketBuilder);
			}
			break;

		default:
			// TODO: default action for error creator
			break;
		}
		return this.mSendPacket;
	}

	/**
	 * This function takes in a datagram packet and change the header on the
	 * datagram packet to invalid
	 * 
	 * @param inPacket
	 */
	private void changeHeader(PacketBuilder inPacket) {
		byte[] header = inPacket.getRequestType().getHeaderByteArray();
		byte[] data = inPacket.getDataBuffer();
		DatagramPacket packet = inPacket.getPacket();
		switch (header[1]) {
		case 1:
			// read request with hearder[0,1]
			data[1] = 4;
			this.mSendPacket = new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort());
			break;
		case 2:
			// write request with header [0,2]
			data[1] = 3;
			this.mSendPacket = new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort());
			break;
		case 3:
			// data datagram packet with header [0,3]
			data[1] = 4;
			this.mSendPacket = new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort());
			break;
		case 4:
			// ack packet with [0,4]
			data[1] = 1;
			this.mSendPacket = new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort());
			break;
		case 5:
			// error packet
			// should not do anything
			break;
		default:
			// TODO:
			break;
		}
	}

	/**
	 * This function double the size of passed in datagram packet to an invalid
	 * size
	 * 
	 * @param inPacket
	 */
	private void invalidPacketSize(PacketBuilder inPacket) {
		byte[] data = inPacket.getDataBuffer();
		this.mSendPacket = new DatagramPacket(data, data.length * 2);
	}

	private ModeType switchMode() {
		return ModeType.INVALID;
	}

	private byte[] addZerosToBuffer() {
		if (readWriteCheck) {
			readWriteBuffer[readWriteBuffer.length - 1] = 65;
		}
		return (readWriteBuffer);

	}

}
