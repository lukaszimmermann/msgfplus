package edu.ucsd.msjava.scripts;

import edu.ucsd.msjava.msgf.Histogram;
import edu.ucsd.msjava.msgf.Tolerance;
import edu.ucsd.msjava.msscorer.NewRankScorer;
import edu.ucsd.msjava.msscorer.NewScorerFactory;
import edu.ucsd.msjava.msscorer.Partition;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.lang.reflect.Field;
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

    private static JSONObject object(final Object... args) {
        final JSONObject result = new JSONObject();
        for (int i = 0; i < args.length - 1; i+=2) {
            result.put(args[i].toString(), args[i+1]);
        }
        return result;
    }

    private static JSONObject toObj(final NewScorerFactory.SpecDataType dataType) {
        return object(
                "method", dataType.getActivationMethod().toString(),
                "instType", dataType.getInstrumentType().toString(),
                "enzyme", object(
                        "name", dataType.getEnzyme().getName()
                ),
                "protocol", object(
                        "name", dataType.getProtocol().getName(),
                        "description", dataType.getProtocol().getDescription()
                )
        );
    }

    private static <T> T getMember(Object input, String field, Class<T> expectedClass) {
        try {
            Field f = input.getClass().getDeclaredField(field);
            f.setAccessible(true);
            return expectedClass.cast(f.get(input));
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static JSONObject toObj(final Tolerance tolerance) {
        return object(
                "value", tolerance.getValue(),
                "unit", tolerance.getUnit()
        );
    }

    private static JSONObject toObj(final Histogram<Integer> chargeHist) {
        JSONObject result = new JSONObject();
        for (int i = chargeHist.minKey(); i <= chargeHist.maxKey(); ++i) {
            result.put(String.valueOf(i), chargeHist.get(i));
        }
        return result;
    }

    private static JSONArray toArr(final Set<Partition> partitionSet) {
        JSONArray result = new JSONArray();
        for (final Partition partition: partitionSet) {
            result.put(object(
               "charge", partition.getCharge(),
               "parentMass", partition.getParentMass(),
                    "segIndex", partition.getSegNum()
            ));
        }
        return result;
    }


    private static Object toJson(final Object obj) {
        if (obj == null) {
            return JSONObject.NULL;
        }
        /* Edge case, non-recursive tyope */
        if (isLeaf(obj)) {
            return obj;
        }
        System.out.println(obj.getClass());

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
        final Field[] fields = obj.getClass().getDeclaredFields();

        try {
            for (final Field field : fields) {
                field.setAccessible(true);
                final Object child = field.get(obj);
                result.put(field.getName(), toJson(child));
            }
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


//    private static Object objectToJSON(final Object obj){
//
//        if (obj instanceof Object[]) {

//
//        // General object, recurse over fields
//        final Field[] fields = obj.getClass().getDeclaredFields();
//        for (final Field field : fields) {
//            field.setAccessible(true);
//            Object o = field.get(obj)
//
//        }
//
//    }




    public static void main(final String[] args) {
        checkArguments(args);
        final File ionstatDir = getIonstatDir(args);
        final File outputDir = getOutputDir(args);

        final File[] paramFiles = ionstatDir.listFiles(file -> file.getPath().endsWith("param"));

        for (final File paramsFile : paramFiles) {
            final NewRankScorer scorer = new NewRankScorer(paramsFile.getAbsolutePath());

            final JSONObject result = object(scorer);

            // Write missing attributes
            // TODO
//
//            // Build individual objects and attibutes
//            final JSONObject dataType = toObj(scorer.getSpecDataType());
//            final JSONObject mme = toObj(scorer.getMME());
//            final boolean applyDeconvolution = scorer.applyDeconvolution();
//            final double deconvolutionErrorTolerance = scorer.deconvolutionErrorTolerance();
//            final JSONObject chargeHist = toObj(getMember(scorer, "chargeHist", Histogram.class));
//            final JSONArray partitionSet = toArr(scorer.getParitionSet());
//
            //  Write
            final String filename = outputDir.toPath().resolve(paramsFile.getName() + ".json").toString();
            System.out.println("Writing to " + filename);
            try (final Writer out = new FileWriter(filename)) {

                result.write(out);

//                object(
//                        "dataType", dataType,
//                        "mme", mme,
//                        "applyDeconvolution", applyDeconvolution,
//                        "deconvolutionErrorTolerance", deconvolutionErrorTolerance,
//                        "chargeHist", chargeHist,
//                        "partitionSet", partitionSet
//                ).write(out);

            } catch (final IOException e) {
                System.err.println("Error writing file!");
                System.exit(4);
            }
        }
    }
}

//
//this.method = method;
//        this.instType = instType;
//        this.enzyme = enzyme;
//        this.protocol = protocol;