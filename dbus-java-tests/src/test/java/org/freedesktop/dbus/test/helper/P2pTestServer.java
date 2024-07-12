package org.freedesktop.dbus.test.helper;

import org.freedesktop.dbus.DBusPath;
import org.freedesktop.dbus.connections.impl.DirectConnection;
import org.freedesktop.dbus.connections.impl.DirectConnectionBuilder;
import org.freedesktop.dbus.connections.transports.TransportBuilder;
import org.freedesktop.dbus.test.helper.interfaces.SampleRemoteInterface;
import org.freedesktop.dbus.test.helper.structs.IntStruct;
import org.freedesktop.dbus.test.helper.structs.SampleStruct3;
import org.freedesktop.dbus.test.helper.structs.SampleStruct4;
import org.freedesktop.dbus.types.UInt16;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class P2pTestServer implements SampleRemoteInterface {

    private final transient Logger logger = LoggerFactory.getLogger(getClass());

    @Override
    public int[][] teststructstruct(SampleStruct3 _in) {
        List<List<Integer>> lli = _in.getInnerListOfLists();
        int[][] out = new int[lli.size()][];
        for (int j = 0; j < out.length; j++) {
            out[j] = new int[lli.get(j).size()];
            for (int k = 0; k < out[j].length; k++) {
                out[j][k] = lli.get(j).get(k);
            }
        }
        return out;
    }

    @Override
    public int[][] testListstruct(SampleStruct4 _in) {
        List<IntStruct> list = _in.getInnerListOfLists();
        int size = list.size();
        int[][] retVal = new int[size][];
        for (int i = 0; i < size; i++) {
            IntStruct elem = list.get(i);
            retVal[i] = new int[] {elem.getValue1(), elem.getValue2()};
        }
        return retVal;
    }

    @Override
    public String getNameAndThrow() {
        return getName();
    }

    @Override
    public String getName() {
        logger.debug("getName called");
        return "Peer2Peer Server";
    }

    @Override
    public <T> int frobnicate(List<Long> _n, Map<String, Map<UInt16, Short>> _m, T _v) {
        return 3;
    }

    @Override
    public void throwme() throws SampleException {
        logger.debug("throwme called");
        throw new SampleException("BOO");
    }

    @Override
    public void waitawhile() {
        // nothing to do
    }

    @Override
    public int overload() {
        return 1;
    }

    @Override
    public void sig(Type[] _s) {
    }

    @Override
    public void newpathtest(DBusPath _p) {
    }

    @Override
    public void reg13291(byte[] _as, byte[] _bs) {
    }

    @Override
    public DBusPath pathrv(DBusPath _a) {
        return _a;
    }

    @Override
    public List<DBusPath> pathlistrv(List<DBusPath> _a) {
        return _a;
    }

    @Override
    public Map<DBusPath, DBusPath> pathmaprv(Map<DBusPath, DBusPath> _a) {
        return _a;
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public String getObjectPath() {
        return null;
    }

    @Override
    public float testfloat(float[] _f) {
        logger.debug("got float: " + Arrays.toString(_f));
        return _f[0];
    }

    public static void main(String[] _args) throws Exception {
        String address = TransportBuilder.createDynamicSession(TransportBuilder.getRegisteredBusTypes().get(0), false);
        PrintWriter w = new PrintWriter(new FileOutputStream("address"));
        w.println(address);
        w.flush();
        w.close();
        try (DirectConnection dc = DirectConnectionBuilder.forAddress(address + ",listen=true").build()) {
            LoggerFactory.getLogger(P2pTestServer.class).debug("Connected");
            dc.exportObject("/Test", new P2pTestServer());
        }
    }

    @Override
    public void thisShouldBeIgnored() {
        logger.error("You should never see this message!");
    }
}
