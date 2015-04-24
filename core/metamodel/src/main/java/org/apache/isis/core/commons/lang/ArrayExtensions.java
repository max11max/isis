/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.apache.isis.core.commons.lang;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.google.common.collect.Lists;

import org.apache.isis.core.commons.exceptions.IsisException;

public final class ArrayExtensions {

    private ArrayExtensions() {
    }

    static Object[] convertPrimitiveToObjectArray(final Object extendee, final Class<?> arrayType) {
        Object[] convertedArray;
        try {
            final Class<?> wrapperClass = ClassExtensions.asWrapped(arrayType);
            final Constructor<?> constructor = wrapperClass.getConstructor(new Class[] { String.class });
            final int len = Array.getLength(extendee);
            convertedArray = (Object[]) Array.newInstance(wrapperClass, len);
            for (int i = 0; i < len; i++) {
                convertedArray[i] = constructor.newInstance(new Object[] { Array.get(extendee, i).toString() });
            }
        } catch (final NoSuchMethodException e) {
            throw new IsisException(e);
        } catch (final ArrayIndexOutOfBoundsException e) {
            throw new IsisException(e);
        } catch (final IllegalArgumentException e) {
            throw new IsisException(e);
        } catch (final InstantiationException e) {
            throw new IsisException(e);
        } catch (final IllegalAccessException e) {
            throw new IsisException(e);
        } catch (final InvocationTargetException e) {
            throw new IsisException(e);
        }
        return convertedArray;
    }

    public static Object[] asCharToCharacterArray(final Object extendee) {
        final char[] original = (char[]) extendee;
        final int len = original.length;
        final Character[] converted = new Character[len];
        for (int i = 0; i < converted.length; i++) {
            converted[i] = Character.valueOf(original[i]);
        }
        return converted;
    }

    public static <T> T[] combine(final T[]... arrays) {
        final List<T> combinedList = Lists.newArrayList();
        for (final T[] array : arrays) {
            for (final T t : array) {
                combinedList.add(t);
            }
        }
        return combinedList.toArray(arrays[0]); // using 1st element of arrays
                                                // to specify the type
    }

    public static String[] append(final String[] extendee, final String... moreArgs) {
        final ArrayList<String> argList = new ArrayList<String>();
        argList.addAll(Arrays.asList(extendee));
        argList.addAll(Arrays.asList(moreArgs));
        return argList.toArray(new String[] {});
    }

    public static List<String> mergeToList(final String[] extendee, final String[] array2) {
        final List<String> prefixes = new ArrayList<String>();
        ArrayExtensions.addNoDuplicates(extendee, prefixes);
        ArrayExtensions.addNoDuplicates(array2, prefixes);
        return prefixes;
    }

    static void addNoDuplicates(final String[] array, final List<String> list) {
        for (int i = 0; i < array.length; i++) {
            if (!list.contains(array[i])) {
                list.add(array[i]);
            }
        }
    }

    public static List<Object> asListFlattened(final Object[] objectArray) {
        final List<Object> list = new ArrayList<Object>();
        for (final Object element : objectArray) {
            if (Collection.class.isAssignableFrom(element.getClass())) {
                @SuppressWarnings("rawtypes")
                final Collection collection = (Collection) element;
                list.addAll(asListFlattened(collection.toArray()));
            } else {
                list.add(element);
            }
        }
        return list;
    }

    public static <T> T coalesce(final T... strings) {
        for (final T str : strings) {
            if (str != null) {
                return str;
            }
        }
        return null;
    }

    public static String commaSeparatedClassNames(final List<Object> objects) {
        final StringBuilder buf = new StringBuilder();
        int i = 0;
        for (final Object object : objects) {
            if (i++ > 0) {
                buf.append(',');
            }
            buf.append(object.getClass().getName());
        }
        return buf.toString();
    }

    public static String asSemicolonDelimitedStr(final List<String> list) {
        final StringBuffer buf = new StringBuffer();
        for (final String message : list) {
            if (list.size() > 1) {
                buf.append("; ");
            }
            buf.append(message);
        }
        return buf.toString();
    }

    public static List<Class<?>> toClasses(final List<Object> objectList) {
        final List<Class<?>> classList = new ArrayList<Class<?>>();
        for (final Object service : objectList) {
            classList.add(service.getClass());
        }
        return classList;
    }


}
