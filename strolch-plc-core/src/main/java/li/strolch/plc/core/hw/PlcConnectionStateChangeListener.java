package li.strolch.plc.core.hw;

public interface PlcConnectionStateChangeListener {

	void notifyStateChange(PlcConnection connection);
}
