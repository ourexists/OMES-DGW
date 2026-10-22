package com.omes.dgw.protocol.s7;

import com.github.s7connector.api.DaveArea;
import com.omes.dgw.device.enums.PointDataType;
import com.omes.dgw.protocol.s7.S7AddressHelper.S7Address;
import com.omes.dgw.protocol.s7.S7AddressHelper.S7ReadSpan;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class S7AddressHelperTest {

    @Test
    void parsesOmesAddresses() {
        S7Address vByte = S7AddressHelper.parse("VB100");
        assertEquals(DaveArea.DB, vByte.area());
        assertEquals(1, vByte.areaNumber());
        assertEquals(100, vByte.offset());
        assertEquals('B', vByte.suffix());

        S7Address vBit = S7AddressHelper.parse("V100.3");
        assertTrue(vBit.bit());
        assertEquals(3, vBit.bitNumber());

        S7Address input = S7AddressHelper.parse("IW0");
        assertEquals(DaveArea.INPUTS, input.area());
        assertEquals('W', input.suffix());

        S7Address flagBit = S7AddressHelper.parse("M0.0");
        assertEquals(DaveArea.FLAGS, flagBit.area());
        assertEquals(0, flagBit.bitNumber());

        S7Address db = S7AddressHelper.parse("DB12.DBD20");
        assertEquals(12, db.areaNumber());
        assertEquals(20, db.offset());
        assertEquals(4, S7AddressHelper.byteCount(db, PointDataType.REAL, null));
        assertEquals(8, S7AddressHelper.byteCount(db, PointDataType.LREAL, null));

        S7Address dbBit = S7AddressHelper.parse("DB1.DBX10.2");
        assertEquals(10, dbBit.offset());
        assertEquals(2, dbBit.bitNumber());

        assertThrows(IllegalArgumentException.class, () -> S7AddressHelper.parse("D100"));
    }

    @Test
    void splitsReadChunksOnByteSpan() {
        S7Address near = S7AddressHelper.parse("DB1.DBW0");
        S7Address far = S7AddressHelper.parse("DB1.DBW300");
        List<List<S7ReadSpan>> chunks = S7AddressHelper.splitChunks(List.of(
                new S7ReadSpan("a", near, 2),
                new S7ReadSpan("b", far, 2)
        ), S7AddressHelper.MAX_READ_BYTES);
        assertEquals(2, chunks.size());
    }

    @Test
    void decodesBigEndianAndMergesBits() {
        assertEquals(10, S7NumericCodec.decode(new byte[]{0x00, 0x0A}, S7AddressHelper.parse("VW0"), null));
        assertEquals(-1, S7NumericCodec.decode(new byte[]{(byte) 0xFF, (byte) 0xFF},
                S7AddressHelper.parse("DB1.DBW0"), point(PointDataType.INT)));
        assertEquals(1.0d, S7NumericCodec.decode(new byte[]{0x3F, (byte) 0x80, 0x00, 0x00},
                S7AddressHelper.parse("DB1.DBD0"), point(PointDataType.REAL)));
        assertEquals(1, S7NumericCodec.decode(new byte[]{0x04}, S7AddressHelper.parse("M0.2"), null));
        assertEquals((byte) 0x08, S7NumericCodec.mergeBit((byte) 0x00, 3, true));
        assertEquals((byte) 0x00, S7NumericCodec.mergeBit((byte) 0x08, 3, false));
    }

    private static com.omes.dgw.device.entity.DevicePoint point(PointDataType dataType) {
        com.omes.dgw.device.entity.DevicePoint point = new com.omes.dgw.device.entity.DevicePoint();
        point.setDataType(dataType);
        return point;
    }
}
