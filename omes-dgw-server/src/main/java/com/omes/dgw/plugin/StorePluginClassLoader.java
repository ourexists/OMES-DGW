package com.omes.dgw.plugin;

import org.pf4j.ClassLoadingStrategy;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;
import java.io.IOException;
import java.net.*;
import java.util.Collections;
import java.util.Enumeration;

/** Resource clients must not retain JarURLConnection's JVM-wide cached JarFile after unload. */
final class StorePluginClassLoader extends PluginClassLoader {
    StorePluginClassLoader(PluginManager manager, PluginDescriptor descriptor, ClassLoader parent) {
        super(manager, descriptor, parent, ClassLoadingStrategy.APD);
    }

    @Override public URL findResource(String name) {
        return uncached(super.findResource(name));
    }

    @Override public Enumeration<URL> findResources(String name) throws IOException {
        return Collections.enumeration(Collections.list(super.findResources(name)).stream().map(StorePluginClassLoader::uncached).toList());
    }

    private static URL uncached(URL original) {
        if (original == null || !"jar".equals(original.getProtocol())) return original;
        try {
            return new URL(null, original.toExternalForm(), new URLStreamHandler() {
                @Override protected URLConnection openConnection(URL ignored) throws IOException {
                    URLConnection connection = original.openConnection();
                    connection.setUseCaches(false);
                    return connection;
                }
            });
        } catch (MalformedURLException ex) { throw new IllegalArgumentException(ex); }
    }
}
