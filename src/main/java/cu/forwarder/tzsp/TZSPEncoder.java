package cu.forwarder.tzsp;

import java.nio.ByteBuffer;

public class TZSPEncoder {

    private TZSPEncoder() {}

    public static byte[] encode(byte[] ethernetFrame) {
        if (ethernetFrame == null) throw new IllegalArgumentException("ethernetFrame == null");
        int headerLen = 4;
        int tlvEndLen = 1;
        ByteBuffer buf = ByteBuffer.allocate(headerLen + tlvEndLen + ethernetFrame.length);

        // header
        buf.put((byte) 0x01); // version
        buf.put((byte) 0x01); // type: received packet
        buf.put((byte) 0x01); // encapsulation: ethernet
        buf.put((byte) 0x00); // flags/reserved

        // TLV end
        buf.put((byte) 0x00);

        // payload
        buf.put(ethernetFrame);

        return buf.array();
    }
}
