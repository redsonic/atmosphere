package org.atmosphere.di;

import javax.servlet.ServletContext;
import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the reference of an object being able to return the current ServletContext of the web application
 *
 * @author Mathieu Carbou
 * @since 0.7
 */
public final class ServletContextHolder {

    private static AtomicReference<WeakReference<ServletContextProvider>> PROVIDER = new AtomicReference<WeakReference<ServletContextProvider>>();

    private ServletContextHolder() {
    }

    public static void register(ServletContextProvider provider) {
        PROVIDER.set(new WeakReference<ServletContextProvider>(provider));
    }

    public static ServletContext getServletContext() {
        WeakReference<ServletContextProvider> ref = PROVIDER.get();
        if (ref != null) {
            ServletContextProvider provider = ref.get();
            if (provider != null) {
                return provider.getServletContext();
            }
        }
        throw new IllegalStateException("No " + ServletContextProvider.class.getSimpleName() + " found.");
    }
}
