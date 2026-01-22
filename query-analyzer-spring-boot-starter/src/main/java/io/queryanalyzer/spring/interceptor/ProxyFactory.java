package io.queryanalyzer.spring.interceptor;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;


public final class ProxyFactory {

    private ProxyFactory() {
    }


    public static <T> T createProxy(Object target, Class<T> interfaceType, InvocationHandler handler) {
        return interfaceType.cast(
            Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[]{interfaceType},
                handler
            )
        );
    }


    public static Object createProxy(Object target, Class<?>[] interfaces, InvocationHandler handler) {
        return Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            interfaces,
            handler
        );
    }


    public static boolean isProxy(Object object) {
        return Proxy.isProxyClass(object.getClass());
    }
}
