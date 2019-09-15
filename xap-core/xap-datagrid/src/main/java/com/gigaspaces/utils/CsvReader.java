package com.gigaspaces.utils;

import com.gigaspaces.document.SpaceDocument;
import com.gigaspaces.internal.metadata.SpacePropertyInfo;
import com.gigaspaces.internal.metadata.SpaceTypeInfo;
import com.gigaspaces.internal.metadata.SpaceTypeInfoRepository;
import com.gigaspaces.metadata.SpacePropertyDescriptor;
import com.gigaspaces.metadata.SpaceTypeDescriptor;
import com.gigaspaces.metadata.SpaceTypeDescriptorBuilder;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author Niv Ingberg
 * @since 14.5
 */
public class CsvReader {

    private final Charset charset;
    private final String valuesSeparator;
    private final String metadataSeparator;
    private final Map<String, Parser> parsers;
    private final BiFunction<String, Integer, Optional<String[]>> invalidLineParser;

    public CsvReader() {
        this(new Builder());
    }

    private CsvReader(Builder builder) {
        this.charset = builder.charset;
        this.valuesSeparator = builder.valuesSeparator;
        this.metadataSeparator = builder.metadataSeparator;
        this.parsers = builder.parsers;
        this.invalidLineParser = builder.invalidLineParser != null ? builder.invalidLineParser : this::defaultInvalidLineProcessor;
    }

    private static String unquote(String s) {
        if (s.startsWith("\"") && s.endsWith("\""))
            s = s.substring(1, s.length() - 1);
        return s;
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T> Stream<T> read(Path path, Class<T> type) throws IOException {
        return new PojoProcessor<>(type).stream(path);
    }

    public <T> Stream<T> read(Stream<String> lines, Class<T> type) throws IOException {
        return new PojoProcessor<>(type).stream(lines);
    }

    public Stream<SpaceDocument> read(Path path, String typeName) throws IOException {
        return read(path, new SpaceTypeDescriptorBuilder(typeName).create());
    }

    public Stream<SpaceDocument> read(Path path, SpaceTypeDescriptor typeDescriptor) throws IOException {
        return new DocumentProcessor(typeDescriptor).stream(path);
    }

    public SpaceTypeDescriptorBuilder readSchema(Path path, String typeName) throws IOException {
        return new DocumentProcessor(new SpaceTypeDescriptorBuilder("dummy").create())
                .readSchema(path, typeName);
    }

    protected Parser getParser(String typeName) {
        Parser parser = parsers.get(typeName);
        if (parser == null)
            throw new IllegalArgumentException("No parser for type " + typeName);
        return parser;
    }

    private String[] split(String s) {
        return s.split(valuesSeparator, -1);
    }

    private Optional<String[]> defaultInvalidLineProcessor(String line, Integer expectedTokens) {
        String[] values = split(line);
        throw new IllegalStateException(String.format("Inconsistent values: expected %s, actual %s", expectedTokens, values.length));
    }

    private static class Parser {
        private final Class<?> type;
        private final Function<String, Object> parser;

        private Parser(Class<?> type, Function<String, Object> parser) {
            this.type = type;
            this.parser = parser;
        }
    }

    public static class Builder {
        private final Map<String, Parser> parsers = initDefaultParsers();
        private Charset charset = StandardCharsets.UTF_8;
        private String valuesSeparator = ",";
        private String metadataSeparator = ":";
        private BiFunction<String, Integer, Optional<String[]>> invalidLineParser;

        private static Map<String, Parser> initDefaultParsers() {
            Map<String, Parser> result = new HashMap<>();

            result.put(String.class.getName(), new Parser(String.class, s -> s));
            result.put(boolean.class.getName(), new Parser(boolean.class, Boolean::parseBoolean));
            result.put(Boolean.class.getName(), new Parser(Boolean.class, Boolean::parseBoolean));
            result.put(byte.class.getName(), new Parser(byte.class, Byte::parseByte));
            result.put(Byte.class.getName(), new Parser(Byte.class, Byte::parseByte));
            result.put(short.class.getName(), new Parser(short.class, Short::parseShort));
            result.put(Short.class.getName(), new Parser(Short.class, Short::parseShort));
            result.put(int.class.getName(), new Parser(int.class, Integer::parseInt));
            result.put(Integer.class.getName(), new Parser(Integer.class, Integer::parseInt));
            result.put(long.class.getName(), new Parser(long.class, Long::parseLong));
            result.put(Long.class.getName(), new Parser(Long.class, Long::parseLong));
            result.put(float.class.getName(), new Parser(float.class, Float::parseFloat));
            result.put(Float.class.getName(), new Parser(Float.class, Float::parseFloat));
            result.put(double.class.getName(), new Parser(double.class, Double::parseDouble));
            result.put(Double.class.getName(), new Parser(Double.class, Double::parseDouble));
            result.put(char.class.getName(), new Parser(char.class, s -> s.charAt(0)));
            result.put(Character.class.getName(), new Parser(Character.class, s -> s.charAt(0)));
            result.put(java.time.LocalDate.class.getName(), new Parser(java.time.LocalDate.class, java.time.LocalDate::parse));
            result.put(java.time.LocalTime.class.getName(), new Parser(java.time.LocalTime.class, java.time.LocalTime::parse));
            result.put(java.time.LocalDateTime.class.getName(), new Parser(java.time.LocalDateTime.class, java.time.LocalDateTime::parse));
            result.put("string", result.get(String.class.getName()));
            result.put("date", result.get(java.time.LocalDate.class.getName()));
            result.put("time", result.get(java.time.LocalTime.class.getName()));
            result.put("datetime", result.get(java.time.LocalDateTime.class.getName()));

            return result;
        }

        public Builder charset(Charset charset) {
            this.charset = charset;
            return this;
        }

        public Builder valuesSeparator(String valuesSeparator) {
            this.valuesSeparator = valuesSeparator;
            return this;
        }

        public Builder metadataSeparator(String metadataSeparator) {
            this.metadataSeparator = metadataSeparator;
            return this;
        }

        public Builder skipInvalidLines() {
            this.invalidLineParser = (s, i) -> Optional.empty();
            return this;
        }

        public Builder invalidLineParser(BiFunction<String, Integer, Optional<String[]>> invalidLineParser) {
            this.invalidLineParser = invalidLineParser;
            return this;
        }

        public Builder addParser(String typeName, Class<?> type, Function<String, Object> parser) {
            parsers.put(typeName, new Parser(type, parser));
            return this;
        }

        public CsvReader build() {
            return new CsvReader(this);
        }
    }

    private abstract class Processor<T> {
        protected List<Column> columns;

        public Stream<T> stream(Path path) throws IOException {
            Stream<String> lines = Files.lines(path, charset);
            return stream(lines);
        }

        public Stream<T> stream(Stream<String> lines) {
            return lines.map(this::parseLine)
                    .filter(Objects::nonNull)
                    .map(this::toEntry);
        }

        public SpaceTypeDescriptorBuilder readSchema(Path path, String typeName) throws IOException {
            try (Stream<T> stream = stream(path)) {
                stream.findFirst();
            }
            SpaceTypeDescriptorBuilder typeDescriptorBuilder = new SpaceTypeDescriptorBuilder(typeName);
            for (Column column : columns)
                typeDescriptorBuilder.addFixedProperty(column.name, column.parser.type);
            return typeDescriptorBuilder;
        }

        private String[] parseLine(String line) {
            String[] values = split(line);
            if (columns == null) {
                columns = Arrays.stream(values).map(this::toColumn).collect(Collectors.toList());
                return null;
            } else if (columns.size() == values.length)
                return values;
            else {
                return invalidLineParser.apply(line, columns.size()).orElse(null);
            }
        }

        private Column toColumn(String s) {
            String[] tokens = unquote(s).split(metadataSeparator);
            return initColumn(tokens[0], tokens.length != 1 ? tokens[1] : null);
        }

        abstract Column initColumn(String name, String typeName);

        abstract T toEntry(String[] values);
    }

    private class DocumentProcessor extends Processor<SpaceDocument> {
        private final SpaceTypeDescriptor typeDescriptor;

        private DocumentProcessor(SpaceTypeDescriptor typeDescriptor) {
            this.typeDescriptor = typeDescriptor;
        }

        @Override
        protected Column initColumn(String name, String typeName) {
            if (typeName == null) {
                SpacePropertyDescriptor property = typeDescriptor.getFixedProperty(name);
                if (property == null) {
                    throw new IllegalStateException("No metadata for property " + name);
                }
                typeName = property.getTypeName();
            }
            return new Column(name, typeName, -1);
        }

        SpaceDocument toEntry(String[] values) {
            SpaceDocument result = new SpaceDocument(typeDescriptor.getTypeName());
            for (int i = 0; i < values.length; i++) {
                if (!values[i].isEmpty()) {
                    Column column = columns.get(i);
                    result.setProperty(column.name, column.parser.parser.apply(values[i]));
                }
            }
            return result;
        }
    }

    private class PojoProcessor<T> extends Processor<T> {
        private final SpaceTypeInfo typeInfo;

        private PojoProcessor(Class<T> type) {
            this.typeInfo = SpaceTypeInfoRepository.getTypeInfo(type);
        }

        @Override
        protected Column initColumn(String name, String typeName) {
            SpacePropertyInfo property = typeInfo.getProperty(name);
            if (property == null)
                throw new IllegalArgumentException("No such property: " + name);
            if (typeName == null)
                typeName = property.getTypeName();
            return new Column(property.getName(), typeName, typeInfo.indexOf(property));
        }

        T toEntry(String[] values) {
            Object[] allValues = new Object[typeInfo.getNumOfSpaceProperties()];
            for (int i = 0; i < values.length; i++) {
                if (!values[i].isEmpty()) {
                    Column column = columns.get(i);
                    allValues[column.pos] = column.parser.parser.apply(values[i]);
                }
            }
            Object result = typeInfo.createInstance();
            typeInfo.setSpacePropertiesValues(result, allValues);
            return (T) result;
        }
    }

    private class Column {
        private final String name;
        private final Parser parser;
        private final int pos;

        private Column(String name, String typeName, int pos) {
            this.name = name;
            this.parser = getParser(typeName);
            this.pos = pos;
        }
    }
}
