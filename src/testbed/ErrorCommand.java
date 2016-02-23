package testbed;

import java.util.HashMap;
import resource.Configurations;

public class ErrorCommand {
	
	private HashMap<String, Integer> mCommandMap;	
	
	public ErrorCommand() {
		this.mCommandMap = new HashMap<>();
	}
	
	public void addMainErrorFamily(int value) {
		this.mCommandMap.put(Configurations.MAIN_ERROR, value);
	}
	
	public int getMainErrorFamily() { 
		return this.mCommandMap.get(Configurations.MAIN_ERROR);
	}
	
	public void addSubErrorFromFamily(int value) {
		this.mCommandMap.put(Configurations.SUB_ERROR, value);
	}
	
	public int getSubErrorFromFamily() {
		return this.mCommandMap.get(Configurations.SUB_ERROR);
	}
	
	/**
	 * Sets the transmission error occurrences through a file transmission.
	 * When the transmission has exhausted the value set, no tranmission errors
	 * will occur there on after.
	 * 
	 * @param value - number of tranmission errors to spawn
	 */
	public void setTransmissionErrorOccurrences(int value) {
		this.mCommandMap.put(Configurations.TE_NUM_PACKETS, value);
	}
	
	public int getTransmissionErrorOccurences() {
		return this.mCommandMap.get(Configurations.TE_NUM_PACKETS);
	}
	
	/**
	 * Sets the frequency of creating transmission errors where the value
	 * 1 represents the amount of good packets to send before an error 
	 * transmission packet is sent
	 * 
	 * @param value - frequency of errors of spawn
	 */
	public void setTransmissionErrorFrequency(int value) {
		this.mCommandMap.put(Configurations.TE_FREQ_ERROR, value);
	}
	
	public int getTransmissionErrorFrequency() {
		return this.mCommandMap.get(Configurations.TE_FREQ_ERROR);
	}
}
