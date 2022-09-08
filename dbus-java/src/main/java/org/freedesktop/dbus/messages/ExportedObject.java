package org.freedesktop.dbus.messages;

import java.lang.annotation.Annotation;
import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

import org.freedesktop.dbus.Marshalling;
import org.freedesktop.dbus.MethodTuple;
import org.freedesktop.dbus.StrongReference;
import org.freedesktop.dbus.Tuple;
import org.freedesktop.dbus.TypeRef;
import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.annotations.DBusMemberName;
import org.freedesktop.dbus.annotations.DBusProperties;
import org.freedesktop.dbus.annotations.DBusProperty;
import org.freedesktop.dbus.connections.AbstractConnection;
import org.freedesktop.dbus.exceptions.DBusException;
import org.freedesktop.dbus.exceptions.DBusExecutionException;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Introspectable;
import org.freedesktop.dbus.interfaces.Peer;
import org.freedesktop.dbus.utils.DBusNamingUtil;

public class ExportedObject {
    private Map<MethodTuple, Method> methods = new HashMap<>();
    private Reference<DBusInterface> object;
    private String                   introspectionData;

    public ExportedObject(DBusInterface _object, boolean _weakreferences) throws DBusException {
        if (_weakreferences) {
            this.object = new WeakReference<>(_object);
        } else {
            this.object = new StrongReference<>(_object);
        }
        Set<Class<?>> implementedInterfaces = getDBusInterfaces(_object.getClass());
        implementedInterfaces.add(Introspectable.class);
        implementedInterfaces.add(Peer.class);

        this.introspectionData = generateIntrospectionXml(implementedInterfaces);
    }

    /**
     * Generates the introspection data xml for annotations
     *
     * @param c input interface/method/signal
     * @return xml with annotation definition
     */
    protected String generateAnnotationsXml(AnnotatedElement c) {
        StringBuilder ans = new StringBuilder();
        for (Annotation a : c.getDeclaredAnnotations()) {

            if (!a.annotationType().isAnnotationPresent(DBusInterfaceName.class)) {
                // skip all interfaces not compatible with
                // DBusInterface (mother of all DBus
                // related interfaces)
                continue;
            }
            Class<? extends Annotation> t = a.annotationType();
            String value = "";
            try {
                Method m = t.getMethod("value");
                if (m != null) {
                    value = m.invoke(a).toString();
                }
            } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException _ex) {
                // ignore
            }

            String name = DBusNamingUtil.getAnnotationName(t);
            ans.append("  <annotation name=\"").append(name)
                    .append("\" value=\"").append(value).append("\" />\n");
        }
        return ans.toString();
    }

    /**
     * Generates the introspection data for the single property.
     *
     * @param property input property annotation
     * @return xml with property definition
     * @throws DBusException in case of unknown data types
     */
    protected String generatePropertyXml(DBusProperty property) throws DBusException {
        Class<?> propertyTypeClass = property.type();
        String propertyTypeString;
        if (TypeRef.class.isAssignableFrom(propertyTypeClass)) {
            Type actualType = Arrays.stream(propertyTypeClass.getGenericInterfaces())
                    .filter(t -> t instanceof ParameterizedType)
                    .map(t -> (ParameterizedType) t)
                    .filter(t -> TypeRef.class.equals(t.getRawType()))
                    .map(t -> t.getActualTypeArguments()[0]) // TypeRef has one generic argument
                    .findFirst()
                    .orElseThrow(() ->
                            new DBusException("Could not read TypeRef type for property '" + property.name() + "'")
                    );
            propertyTypeString = Marshalling.getDBusType(new Type[]{actualType});
        } else if (List.class.equals(propertyTypeClass)) {
            // default non generic list types
            propertyTypeString = "av";
        } else if (Map.class.equals(propertyTypeClass)) {
            // default non generic map type
            propertyTypeString = "a{vv}";
        } else {
            propertyTypeString = Marshalling.getDBusType(new Type[]{propertyTypeClass});
        }

        String access = property.access().getAccessName();
        return "<property name=\"" + property.name() + "\" type=\"" + propertyTypeString + "\" access=\"" + access + "\" />";
    }

    /**
     * Generates the introspection data for the input interface properties.
     *
     * @param c input interface
     * @return xml with property definitions
     * @throws DBusException in case of unknown data types
     */
    protected String generatePropertiesXml(Class<?> c) throws DBusException {
        StringBuilder xml = new StringBuilder();
        DBusProperties properties = c.getAnnotation(DBusProperties.class);
        if (properties != null) {
            for (DBusProperty property : properties.value()) {
                xml.append("  ").append(generatePropertyXml(property)).append("\n");
            }
        }
        DBusProperty property = c.getAnnotation(DBusProperty.class);
        if (property != null) {
            xml.append("  ").append(generatePropertyXml(property)).append("\n");
        }
        return xml.toString();
    }

    /**
     * Generates the introspection data for the input interface methods
     *
     * @param c input interface
     * @return xml with method definitions
     *
     * @throws DBusException if marshalling fails
     */
    protected String generateMethodsXml(Class<?> c) throws DBusException {
        StringBuilder sb = new StringBuilder();
        for (Method meth : c.getDeclaredMethods()) {
            if (!Modifier.isPublic(meth.getModifiers())) {
                continue;
            }
            String ms = "";
            String methodName = DBusNamingUtil.getMethodName(meth);
            if (methodName.length() > AbstractConnection.MAX_NAME_LENGTH) {
                throw new DBusException(
                        "Introspected method name exceeds 255 characters. Cannot export objects with method "
                                + methodName);
            }
            sb.append("  <method name=\"").append(methodName).append("\" >\n");
            sb.append(generateAnnotationsXml(meth));
            for (Class<?> ex : meth.getExceptionTypes()) {
                if (DBusExecutionException.class.isAssignableFrom(ex)) {
                    sb.append("   <annotation name=\"org.freedesktop.DBus.Method.Error\" value=\"")
                            .append(AbstractConnection.DOLLAR_PATTERN.matcher(ex.getName()).replaceAll("."))
                            .append("\" />\n");
                }
            }
            for (Type pt : meth.getGenericParameterTypes()) {
                for (String s : Marshalling.getDBusType(pt)) {
                    sb.append("   <arg type=\"").append(s).append("\" direction=\"in\"/>\n");
                    ms += s;
                }
            }
            if (!Void.TYPE.equals(meth.getGenericReturnType())) {
                if (Tuple.class.isAssignableFrom(meth.getReturnType())) {
                    ParameterizedType tc = (ParameterizedType) meth.getGenericReturnType();
                    Type[] ts = tc.getActualTypeArguments();

                    for (Type t : ts) {
                        if (t != null) {
                            for (String s : Marshalling.getDBusType(t)) {
                                sb.append("   <arg type=\"").append(s).append("\" direction=\"out\"/>\n");
                            }
                        }
                    }
                } else if (Object[].class.equals(meth.getGenericReturnType())) {
                    throw new DBusException("Return type of Object[] cannot be introspected properly");
                } else {
                    for (String s : Marshalling.getDBusType(meth.getGenericReturnType())) {
                        sb.append("   <arg type=\"").append(s).append("\" direction=\"out\"/>\n");
                    }
                }
            }
            sb.append("  </method>\n");
            methods.putIfAbsent(new MethodTuple(methodName, ms), meth);
        }

        return sb.toString();
    }

    /**
     * Generates the introspection data for the input interface signals
     *
     * @param c input interface
     * @return xml with signal definitions
     * @throws DBusException in case of invalid signal name / data types
     */
    protected String generateSignalsXml(Class<?> c) throws DBusException {
        StringBuilder sb = new StringBuilder();
        for (Class<?> sig : c.getDeclaredClasses()) {
            if (DBusSignal.class.isAssignableFrom(sig)) {
                String signalName = DBusNamingUtil.getSignalName(sig);
                if (sig.isAnnotationPresent(DBusMemberName.class)) {
                    DBusSignal.addSignalMap(sig.getSimpleName(), signalName);
                }
                if (signalName.length() > AbstractConnection.MAX_NAME_LENGTH) {
                    throw new DBusException(
                            "Introspected signal name exceeds 255 characters. Cannot export objects with signals of type "
                                    + signalName);
                }
                sb.append("  <signal name=\"").append(signalName).append("\">\n");
                Constructor<?> con = sig.getConstructors()[0];
                Type[] ts = con.getGenericParameterTypes();
                for (int j = 1; j < ts.length; j++) {
                    for (String s : Marshalling.getDBusType(ts[j])) {
                        sb.append("   <arg type=\"").append(s).append("\" direction=\"out\" />\n");
                    }
                }
                sb.append(generateAnnotationsXml(sig));
                sb.append("  </signal>\n");
            }
        }
        return sb.toString();
    }

    /**
     * Get all valid DBus interfaces which are implemented in a given class.
     * The search is performed without recursion taking into account object inheritance.
     * A valid DBus interface must directly extend the {@link DBusInterface}.
     *
     * @param inputClazz input object class
     * @return set of DBus interfaces implements in the input class
     */
    protected Set<Class<?>> getDBusInterfaces(Class<?> inputClazz) {
        Objects.requireNonNull(inputClazz, "inputClazz must not be null");
        Set<Class<?>> result = new LinkedHashSet<>();

        // set of already checked classes/interfaces - used to avoid loops/redundant reflection calls
        Set<Class<?>> checked = new LinkedHashSet<>();
        // queue with classes/interfaces to check
        Queue<Class<?>> toCheck = new LinkedList<>();
        toCheck.add(inputClazz);

        while (!toCheck.isEmpty()) {
            Class<?> clazz = toCheck.poll();
            checked.add(clazz); // avoid checking this class in the next loops

            // if it's class and it has super class, queue to check it later
            Class<?> superClass = clazz.getSuperclass();
            if (superClass != null && DBusInterface.class.isAssignableFrom(superClass)) {
                toCheck.add(superClass);
            }

            List<Class<?>> interfaces = Arrays.asList(clazz.getInterfaces());
            if (clazz.isInterface() && interfaces.contains(DBusInterface.class)) {
                // clazz is interface and directly extends the DBusInterface
                result.add(clazz);
            }

            // iterate over the sub-interfaces and select the ones that extend DBusInterface
            // this is required especially for nested interfaces
            interfaces.stream()
                    .filter(DBusInterface.class::isAssignableFrom)
                    .filter(i -> i != DBusInterface.class)
                    .filter(i -> !checked.contains(i))
                    .forEach(toCheck::add);
        }

        return result;
    }

    private String generateIntrospectionXml(Set<Class<?>> interfaces) throws DBusException {
        StringBuilder sb = new StringBuilder();
        for (Class<?> iface : interfaces) {
            String ifaceName = DBusNamingUtil.getInterfaceName(iface);
            // don't let people export things which don't have a valid D-Bus interface name
            if (ifaceName.equals(iface.getSimpleName())) {
                throw new DBusException("DBusInterfaces cannot be declared outside a package");
            }
            if (ifaceName.length() > AbstractConnection.MAX_NAME_LENGTH) {
                throw new DBusException(
                        "Introspected interface name exceeds 255 characters. Cannot export objects of type "
                                + ifaceName);
            }

            // add mapping between class FQCN and name used in annotation (if present)
            if (iface.isAnnotationPresent(DBusInterfaceName.class)) {
                DBusSignal.addInterfaceMap(iface.getName(), ifaceName);
            }

            sb.append(" <interface name=\"").append(ifaceName).append("\">\n");
            sb.append(generateAnnotationsXml(iface));
            sb.append(generateMethodsXml(iface));
            sb.append(generatePropertiesXml(iface));
            sb.append(generateSignalsXml(iface));
            sb.append(" </interface>\n");
        }

        return sb.toString();
    }

    public Map<MethodTuple, Method> getMethods() {
        return methods;
    }

    public Reference<DBusInterface> getObject() {
        return object;
    }

    public String getIntrospectiondata() {
        return introspectionData;
    }

}
