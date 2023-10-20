package org.freedesktop.dbus.connections.config;

import org.freedesktop.dbus.connections.BusAddress;
import org.freedesktop.dbus.connections.impl.BaseConnectionBuilder;
import org.freedesktop.dbus.connections.transports.AbstractTransport;
import org.freedesktop.dbus.utils.Util;

import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.function.Consumer;

/**
 * Configuration used to setup a transport.
 *
 * @author hypfvieh
 * @since v4.2.0 - 2022-07-21
 */
public final class TransportConfig {

    private SaslConfig                  saslConfig;

    private BusAddress                  busAddress;

    private Consumer<AbstractTransport> preConnectCallback;
    private Consumer<AbstractTransport> afterBindCallback;

    private int                         timeout          = 10000;
    private boolean                     autoConnect      = true;

    /** user to set on socket file if this is a server transport (null to do nothing). */
    private String                      fileOwner;
    /** group to set on socket file if this is a server transport (null to do nothing). */
    private String                      fileGroup;

    private byte                        endianess        = BaseConnectionBuilder.getSystemEndianness();
    private boolean                     registerSelf     = true;

    /**
     * Unix file permissions to set on socket file if this is a server transport (ignored on Windows, does nothing if
     * null)
     */
    private Set<PosixFilePermission>    fileUnixPermissions;

    /**
     * Contains additional configuration where no direct getter/setter is available for.
     */
    private Map<String, Object>         additionalConfig = new LinkedHashMap<>();

    public TransportConfig(BusAddress _address) {
        busAddress = _address;
    }

    public TransportConfig() {
        this(null);
    }

    public BusAddress getBusAddress() {
        return busAddress;
    }

    public void setBusAddress(BusAddress _busAddress) {
        busAddress = Objects.requireNonNull(_busAddress, "BusAddress required");
    }

    public void setListening(boolean _listen) {
        updateBusAddress(_listen);
    }

    public boolean isListening() {
        return busAddress != null && busAddress.isListeningSocket();
    }

    public Consumer<AbstractTransport> getPreConnectCallback() {
        return preConnectCallback;
    }

    public void setPreConnectCallback(Consumer<AbstractTransport> _preConnectCallback) {
        preConnectCallback = _preConnectCallback;
    }

    public Consumer<AbstractTransport> getAfterBindCallback() {
        return afterBindCallback;
    }

    public void setAfterBindCallback(Consumer<AbstractTransport> _afterBindCallback) {
        afterBindCallback = _afterBindCallback;
    }

    public boolean isAutoConnect() {
        return autoConnect;
    }

    public void setAutoConnect(boolean _autoConnect) {
        autoConnect = _autoConnect;
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int _timeout) {
        timeout = _timeout;
    }

    public String getFileOwner() {
        return fileOwner;
    }

    public void setFileOwner(String _fileOwner) {
        fileOwner = _fileOwner;
    }

    public String getFileGroup() {
        return fileGroup;
    }

    public void setFileGroup(String _fileGroup) {
        fileGroup = _fileGroup;
    }

    public Set<PosixFilePermission> getFileUnixPermissions() {
        return fileUnixPermissions;
    }

    public void setFileUnixPermissions(PosixFilePermission... _permissions) {
        if (Util.isWindows()) {
            return;
        }

        if (_permissions == null || _permissions.length < 1) {
            return;
        }

        fileUnixPermissions = new LinkedHashSet<>(Arrays.asList(_permissions));
    }

    public Map<String, Object> getAdditionalConfig() {
        return additionalConfig;
    }

    public void setAdditionalConfig(Map<String, Object> _additionalConfig) {
        additionalConfig = _additionalConfig;
    }

    public SaslConfig getSaslConfig() {
        if (saslConfig == null) {
            saslConfig = new SaslConfig();
        }
        return saslConfig;
    }

    void setSaslConfig(SaslConfig _saslCfg) {
        saslConfig = _saslCfg;
    }

    public byte getEndianess() {
        return endianess;
    }

    public void setEndianess(byte _endianess) {
        endianess = _endianess;
    }

    public boolean isRegisterSelf() {
        return registerSelf;
    }

    public void setRegisterSelf(boolean _registerSelf) {
        registerSelf = _registerSelf;
    }

    /**
     * Toggles the busaddress to be a listening (server) or non listening (client) connection.
     * @param _listening true to be a server connection
     */
    void updateBusAddress(boolean _listening) {
        if (busAddress == null) {
            return;
        }
        if (!busAddress.isListeningSocket() && _listening) { // not a listening address, but should be one
            busAddress.addParameter("listen", "true");
        } else if (busAddress.isListeningSocket() && !_listening) { // listening address, but should not be one
            busAddress.removeParameter("listen");
        }
    }

}
