package edu.ucsd.msjava.scripts;

import edu.ucsd.msjava.msscorer.NewRankScorer;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Writes the ionstat/*params file in a JSON representation
 *
 */
public final class IonstatJsonizer {

    private IonstatJsonizer() {
        throw new AssertionError("Unexpected instantiation!");
    }

    private static void checkArguments(final String[] args) {
        if (args.length < 2) {
            System.err.println("Please provide file path with params file and output dir as argument!");
            System.exit(1);
        }
    }

    private static boolean isEmptyDir(final File file) {
        if ( ! file.isDirectory()) {
            return false;
        }
        final File[] children = file.listFiles();
        return children == null || children.length == 0;
    }


    private static File getIonstatDir(final String[] args) {
        final File ionstatDir = new File(args[0]);
        if ( ! ionstatDir.isDirectory()) {
            System.err.println("Provided argument is not a directory");
            System.exit(2);
        }
        return ionstatDir;
    }

    private static File getOutputDir(final String[] args) {
        final File outputDir = new File(args[1]);
        if ( ! isEmptyDir(outputDir)) {
            System.err.println("Output Dir is not an empty directory. Refusing");
            System.exit(3);
        }
        return outputDir;
    }

    private static Object toJson(final Object obj) {
        if (obj == null) {
            return JSONObject.NULL;
        }
        /* Edge case, non-recursive tyope */
        if (isLeaf(obj)) {
            return obj;
        }

        /*
         * Maps will be transformed to Object
         */
        if (obj instanceof Map) {
            return map((Map) obj);
        }

        if (obj instanceof Object[]) {
            final Object[] items = (Object[]) obj;
            return array(Arrays.asList(items).iterator());
        }
        if (obj instanceof Iterable){
            final Iterable<Object> item = (Iterable<Object>) obj;
            return array(item.iterator());
        }
        // I all other cases, the object is treated as objects with fields
        return object(obj);
    }

    private static boolean isLeaf(final Object o) {
        return  o instanceof  Number || o instanceof Boolean
                || o instanceof String || o instanceof Enum;
    }


    private static JSONObject object(final Object obj) {
        final JSONObject result = new JSONObject();
        final List<Field> nonStaticFields = getNonStaticFields(obj);
        final List<String> clazzes = getClasses(obj);
        try {
            for (final Field field : nonStaticFields) {
                field.setAccessible(true);
                final Object child = field.get(obj);
                result.put(field.getName(), toJson(child));
            }
            result.put("javaClasses", clazzes);
        } catch (final IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return result;
    }

    private static JSONArray array(final Iterator<Object> items) {
        final JSONArray result = new JSONArray();
        while (items.hasNext()) {
            result.put(toJson(items.next()));
        }
        return result;
    }

    private static JSONObject map(final Map<?,?> input) {

        final int size = input.size();
        final List<Object> keys = new ArrayList<>(size);
        final List<Object> values = new ArrayList<>(size);
        for (final Map.Entry<?,?> entry : input.entrySet()) {
            keys.add(entry.getKey());
            values.add(entry.getValue());
        }
        final JSONObject result = new JSONObject();
        result.put("keys", array(keys.iterator()));
        result.put("values", array(values.iterator()));
        return result;
    }

    private static List<Field> getNonStaticFields(final Object o) {

        // Collect non-static fields recursively until Object is encountered
        final List<Field> result = new ArrayList<>();
        Class<?> clazz = o.getClass();
        while (clazz != Object.class) {
            final Field[] fields = clazz.getDeclaredFields();
            for (final Field field: fields) {
                if ( ! Modifier.isStatic(field.getModifiers())) {
                    result.add(field);
                }
            }
            clazz = clazz.getSuperclass();
        }
        return result;
    }

    private static List<String> getClasses(final Object o) {
        final List<String> result = new ArrayList<>();
        Class<?> clazz = o.getClass();
        while (clazz != Object.class) {
            result.add(clazz.getCanonicalName());
            clazz = clazz.getSuperclass();
        }
        return result;
    }

    public static void main(final String[] args) {
        checkArguments(args);
        final File ionstatDir = getIonstatDir(args);
        final File outputDir = getOutputDir(args);

        final File[] paramFiles = ionstatDir.listFiles(file -> file.getPath().endsWith("param"));

        for (final File paramsFile : paramFiles) {

            final NewRankScorer scorer = new NewRankScorer(paramsFile.getAbsolutePath());
            final JSONObject result = object(scorer);

            //  Write
            final String filename = outputDir.toPath().resolve(paramsFile.getName() + ".json").toString();
            System.out.println("Writing to " + filename);
            try (final Writer out = new FileWriter(filename)) {
                result.write(out);
            } catch (final IOException e) {
                System.err.println("Error writing file!");
                System.exit(4);
            }
        }
    }
}
