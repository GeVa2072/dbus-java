package com.github.hypfvieh.dbus.examples.networkmanager;

import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.types.UInt32;
import org.freedesktop.dbus.types.Variant;
import org.freedesktop.networkmanager.Settings;
import org.freedesktop.networkmanager.settings.Connection;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sample code which updates a connection's IPv4 method with the Update() method.
 *
 * This uses the new NM 1.0 setting properties. Configuration settings are
 * described at https://networkmanager.dev/docs/api/latest/ref-settings.html
 *
 * @author mattdibi
 */
public final class NetworkManagerExample3 {

    private NetworkManagerExample3() {}

    public static void main(String[] _args) {
        if (_args.length < 2) {
            System.out.println("Usage: <uuid> <auto|static> [address prefix gateway]");
            System.exit(1);
        }

        String method = _args[1];
        if ("static".equals(method) && _args.length < 4) {
            System.out.println("Usage: <uuid> <static> address prefix [gateway]");
            System.exit(1);
        }

        // Convert method to NM method
        if ("static".equals(method)) {
            method = "manual";
        }

        try (DBusConnection dbusConn = DBusConnectionBuilder.forSystemBus().build()) {

            Settings settings = dbusConn.getRemoteObject("org.freedesktop.NetworkManager",
                    "/org/freedesktop/NetworkManager/Settings", Settings.class);

            for (DBusInterface connectionIf : settings.ListConnections()) {
                Connection connection = dbusConn.getRemoteObject("org.freedesktop.NetworkManager",
                        connectionIf.getObjectPath(), Connection.class);

                Map<String, Map<String, Variant<?>>> connectionSettings = connection.GetSettings();

                // Look for the requested connection UUID
                if (!connectionSettings.get("connection").get("uuid").getValue().toString().equals(_args[0])) {
                    continue;
                }

                // Deep copy
                Map<String, Variant<?>> connectionMap = new HashMap<>();
                for (String key : connectionSettings.get("connection").keySet()) {
                    connectionMap.put(key, connectionSettings.get("connection").get(key));
                }
                Map<String, Variant<?>> ipv4Map = new HashMap<>();
                for (String key : connectionSettings.get("ipv4").keySet()) {
                    ipv4Map.put(key, connectionSettings.get("ipv4").get(key));
                }

                // Clear existing address info
                ipv4Map.remove("addresses");
                ipv4Map.remove("address-data");
                ipv4Map.remove("gateway");

                // Set the method and change properties
                ipv4Map.put("method", new Variant<>(method));

                if ("manual".equals(method)) {
                    // Add the static IP address, prefix and (optional) gateway
                    Map<String, Variant<?>> address = new HashMap<>();
                    address.put("address", new Variant<>(_args[2]));
                    address.put("prefix", new Variant<>(new UInt32(_args[3])));

                    List<Map<String, Variant<?>>> addressData = Arrays.asList(address);
                    ipv4Map.put("address-data", new Variant<>(addressData, "aa{sv}"));

                    if (_args.length == 5) {
                        ipv4Map.put("gateway", new Variant<>(_args[4]));
                    }
                }

                // Build output data
                Map<String, Map<String, Variant<?>>> newConnectionSettings = new HashMap<>();
                newConnectionSettings.put("ipv4", ipv4Map);
                newConnectionSettings.put("connection", connectionMap);

                // Update & save connection settings
                connection.Update(newConnectionSettings);
                connection.Save();
            }

        } catch (IOException | DBusException _ex) {
            LoggerFactory.getLogger(NetworkManagerExample3.class).error("Error", _ex);
        }
    }
}
