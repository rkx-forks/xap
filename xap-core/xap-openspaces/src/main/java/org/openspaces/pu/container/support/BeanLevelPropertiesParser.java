/*
 * Copyright (c) 2008-2016, GigaSpaces Technologies, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.openspaces.pu.container.support;

import com.gigaspaces.internal.io.BootIOUtils;
import org.openspaces.core.properties.BeanLevelProperties;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * A {@link org.openspaces.core.properties.BeanLevelProperties} parser that parses -properties
 * parameter(s) and transforms it into bean level properties. The format of the command is
 * <code>-properties [beanName] [properties]</code>.
 *
 * <p>By deafult, will also try and load META-INF/spring/pu.properties and put it in the context
 * level properties.
 *
 * <p> [beanName] is optional, if not used, the properties will set the {@link
 * org.openspaces.core.properties.BeanLevelProperties#setContextProperties(java.util.Properties)}.
 * If used, will inject properties only to the bean registered under the provided beanName within
 * the Spring context (see {@link org.openspaces.core.properties.BeanLevelProperties#setBeanProperties(String,
 * java.util.Properties)}). The [properties] can either start with <code>embed://</code> which mean
 * they will be provided within the command line (for example: <code>embed://propName1=propVal1;propName2=propVal2</code>)
 * or they can follow Spring {@link org.springframework.core.io.Resource} lookup based on URL syntax
 * or Spring extended <code>classpath</code> prefix (see {@link org.springframework.core.io.DefaultResourceLoader}).
 *
 * @author kimchy
 */
public abstract class BeanLevelPropertiesParser {
    final public static String KEY_PROPERTY = "property";
    final public static String KEY_PROPERTIES = "properties";
    final public static String EMBEDDED_PROPERTIES_PREFIX = "embed://";

    final public static String DEFAULT_CONTEXT_PROPERTIES_LOCATION = "META-INF/spring/pu.properties";

    public static BeanLevelProperties parse(CommandLineParser.Parameter[] params) throws IllegalArgumentException {
        return parse(new BeanLevelProperties(), params);
    }

    public static BeanLevelProperties parse(BeanLevelProperties beanLevelProperties, CommandLineParser.Parameter[] params) throws IllegalArgumentException {
        // try and load pu.properties
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(DEFAULT_CONTEXT_PROPERTIES_LOCATION);
        if (is != null) {
            try {
                beanLevelProperties.getContextProperties().load(is);
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to load [" + DEFAULT_CONTEXT_PROPERTIES_LOCATION + "]", e);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }

        for (CommandLineParser.Parameter param : params) {
            if (!param.getName().equalsIgnoreCase(KEY_PROPERTIES) && !param.getName().equalsIgnoreCase(KEY_PROPERTY)) {
                continue;
            }

            if (param.getArguments().length == 1) {
                loadProperties(param.getName(), param.getArguments()[0], beanLevelProperties.getContextProperties());
            } else if (param.getArguments().length == 2) {
                loadProperties(param.getName(), param.getArguments()[1], beanLevelProperties.getBeanProperties(param.getArguments()[0]));
            } else {
                throw new IllegalArgumentException("-" + param.getName() + " can accept only one or two values, not more and not less");
            }
        }
        return beanLevelProperties;
    }

    private static void loadProperties(String name, String value, Properties properties) {
        if (name.equalsIgnoreCase(KEY_PROPERTY)) {
            addProperty(properties, value);
        } else if (value.startsWith(EMBEDDED_PROPERTIES_PREFIX)) {
            StringTokenizer tokenizer = new StringTokenizer(value.substring(EMBEDDED_PROPERTIES_PREFIX.length()), ";");
            while (tokenizer.hasMoreTokens()) {
                addProperty(properties, tokenizer.nextToken());
            }
        } else {
            Resource resource = new DefaultResourceLoader() {
                // override the default load from the classpath to load from the file system
                @Override
                protected Resource getResourceByPath(String path) {
                    return new FileSystemResource(path);
                }
            }.getResource(value);
            try {
                properties.putAll(loadProperties(resource));
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to load resource [" + value + "] " + e.getMessage(), e);
            }

        }
    }

    public static Properties loadProperties(Resource resource) throws IOException {
        try (InputStream stream = resource.getInputStream()) {
            return BootIOUtils.loadProperties(stream, resource.getFilename());
        }
    }

    private static void addProperty(Properties properties, String s) {
        int equalsIndex = s.indexOf('=');
        if (equalsIndex == -1) {
            properties.put(s, "");
        } else {
            properties.put(s.substring(0, equalsIndex), s.substring(equalsIndex + 1));
        }
    }
}
