package io.queryanalyzer.spring.interceptor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class ProxyFactoryTest {

    @Test
    void shouldCreateProxyForSingleInterface() {

        TestService original = new TestServiceImpl();
        InvocationHandler handler = new CountingHandler(original);


        TestService proxy = ProxyFactory.createProxy(original, TestService.class, handler);


        assertThat(proxy).isNotNull();
        assertThat(ProxyFactory.isProxy(proxy)).isTrue();
        assertThat(proxy.getMessage()).isEqualTo("Hello");
    }

    @Test
    void shouldInvokeHandlerOnMethodCall() {

        TestService original = new TestServiceImpl();
        CountingHandler handler = new CountingHandler(original);
        TestService proxy = ProxyFactory.createProxy(original, TestService.class, handler);


        proxy.getMessage();
        proxy.getMessage();


        assertThat(handler.getInvocationCount()).isEqualTo(2);
    }

    @Test
    void shouldCreateProxyForMultipleInterfaces() {

        MultiImpl original = new MultiImpl();
        InvocationHandler handler = (proxy, method, args) -> method.invoke(original, args);


        Object proxy = ProxyFactory.createProxy(
            original,
            new Class<?>[]{TestService.class, AnotherService.class},
            handler
        );


        assertThat(proxy).isInstanceOf(TestService.class);
        assertThat(proxy).isInstanceOf(AnotherService.class);
    }

    // Test interfaces and implementations
    interface TestService {
        String getMessage();
    }

    interface AnotherService {
        int getNumber();
    }

    static class TestServiceImpl implements TestService {
        @Override
        public String getMessage() {
            return "Hello";
        }
    }

    static class MultiImpl implements TestService, AnotherService {
        @Override
        public String getMessage() {
            return "Multi";
        }

        @Override
        public int getNumber() {
            return 42;
        }
    }

    static class CountingHandler implements InvocationHandler {
        private final Object target;
        private int invocationCount = 0;

        CountingHandler(Object target) {
            this.target = target;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            invocationCount++;
            return method.invoke(target, args);
        }

        public int getInvocationCount() {
            return invocationCount;
        }
    }
}
