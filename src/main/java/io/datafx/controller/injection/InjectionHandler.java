/**
 * Copyright (c) 2011, 2013, Jonathan Giles, Johan Vos, Hendrik Ebbers All rights reserved. Copyright (c)
 * 2017, Kirill Romanov All rights reserved.
 * <p>
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided
 * that the following conditions are met: * Redistributions of source code must retain the above copyright
 * notice, this list of conditions and the following disclaimer. * Redistributions in binary form must
 * reproduce the above copyright notice, this list of conditions and the following disclaimer in the
 * documentation and/or other materials provided with the distribution. * Neither the name of DataFX, the
 * website javafxdata.org, nor the names of its contributors may be used to endorse or promote products
 * derived from this software without specific prior written permission.
 * <p>
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A
 * PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL <COPYRIGHT HOLDER> BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR
 * OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
 * DAMAGE.
 */
package io.datafx.controller.injection;

import io.datafx.controller.context.AbstractContext;
import io.datafx.controller.context.ApplicationContext;
import io.datafx.controller.context.ViewContext;
import io.datafx.controller.injection.provider.ContextProvider;
import io.datafx.core.DataFXUtils;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;


public class InjectionHandler<U> {

    private final static DependendContext DEPENDEND_CONTEXT = new DependendContext();
    private ViewContext<U> viewContext;

    public InjectionHandler(ViewContext<U> viewContext) {
        this.viewContext = viewContext;
    }

    public <T> T getInstance(final Class<T> propertyClass) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        final AbstractContext context = getContextForClass(propertyClass);
        T instance = context.getRegisteredObject(propertyClass);
        if (instance == null) {
            instance = createNewInstance(propertyClass);
            context.register(instance);
            injectAllSupportedFields(instance);
            callPostConstructMethods(instance);
        }

        return instance;
    }

    private <T> void callPostConstructMethods(T bean) {
        Class<T> cls = (Class<T>) bean.getClass();
        for (Method method : DataFXUtils.getInheritedDeclaredMethods(cls)) {
            if (method.isAnnotationPresent(PostConstruct.class)) {
                DataFXUtils.callPrivileged(method, bean);
            }
        }
    }

    private <T> void injectAllSupportedFields(T bean) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        Class<T> cls = (Class<T>) bean.getClass();
        for (final Field field : DataFXUtils.getInheritedDeclaredFields(cls)) {
            if (field.isAnnotationPresent(Inject.class)) {
                Object value = ApplicationContext.getInstance().getRegisteredObject(field.getType());
                if (value == null) {
                    value = getInstance(field.getType());
                }

                DataFXUtils.setPrivileged(field, bean, value);
            }
        }
    }

    private <T> T createNewInstance(Class<T> cls) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        Constructor<?> annotatedConstructor = Arrays.stream(cls.getDeclaredConstructors())
            .filter(constructor -> constructor.isAnnotationPresent(Inject.class))
            .filter(constructor -> constructor.getParameterCount() > 0)
            .sorted((c1, c2) -> {
                int res = Integer.compare(c1.getModifiers(), c2.getModifiers());
                return res != 0 ? res : Integer.compare(c2.getParameterCount(), c1.getParameterCount());
            })
            .findFirst().orElse(null);

        if (annotatedConstructor == null) {
            return cls.newInstance();
        }

        List<Object> args = new ArrayList<>();
        for (Class<?> parameterClass : annotatedConstructor.getParameterTypes()) {
            Object value = ApplicationContext.getInstance().getRegisteredObject(parameterClass);
            if (value == null) {
                value = getInstance(parameterClass);
            }
            args.add(value);
        }

        boolean wasAccessible = annotatedConstructor.isAccessible();
        try {
            annotatedConstructor.setAccessible(true);
            return (T) annotatedConstructor.newInstance(args.toArray());
        } finally {
            annotatedConstructor.setAccessible(wasAccessible);
        }
    }

    private AbstractContext getContextForClass(Class<?> cls) {
        ServiceLoader<ContextProvider> contextProvidersLoader = ServiceLoader.load(ContextProvider.class);
        Iterator<ContextProvider> iterator = contextProvidersLoader.iterator();

        while (iterator.hasNext()) {
            ContextProvider provider = iterator.next();
            if (cls.isAnnotationPresent(provider.supportedAnnotation())) {
                return provider.getContext(viewContext);
            }
        }

        return DEPENDEND_CONTEXT;
    }
}
