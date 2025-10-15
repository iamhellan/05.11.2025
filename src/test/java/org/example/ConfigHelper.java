package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class ConfigHelper {
    private static final Properties props = new Properties();

    static {
        try (InputStream input = ConfigHelper.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (input == null) {
                throw new RuntimeException("Файл config.properties не найден в classpath!");
            }
            props.load(input);
        } catch (IOException e) {
            throw new RuntimeException("Не удалось загрузить config.properties", e);
        }
    }

    public static String get(String key) {
        String value = props.getProperty(key);
        if (value == null) {
            throw new RuntimeException("Ключ '" + key + "' не найден в config.properties!");
        }
        return value;
    }
}
