package extraction;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ExtractionTest {
    @Test
    public void parsePatchString() throws Exception {
        String patchString = "--- a/src/java/voldemort/VoldemortClientShell.java\n" +
                "+++ b/src/java/voldemort/VoldemortClientShell.java\n" +
                "@@ -75,7 +75,6 @@ public class VoldemortClientShell {\n" +
                "                     client.put(tightenNumericTypes(jsonReader.read()),\n" +
                "                                tightenNumericTypes(jsonReader.read()));\n" +
                "                 } else if(line.toLowerCase().startsWith(\"get\")) {\n" +
                "-                    logger.info (\"get called:\");\n" +
                "                     JsonReader jsonReader = new JsonReader(new StringReader(line.substring(\"get\".length())));\n" +
                "                     printVersioned(client.get(tightenNumericTypes(jsonReader.read())));\n" +
                "                 } else if(line.toLowerCase().startsWith(\"delete\")) {";
        String compare = "@@ -75,7 +75,6 @@ public class VoldemortClientShell {\n" +
                "                     client.put(tightenNumericTypes(jsonReader.read()),\n" +
                "                                tightenNumericTypes(jsonReader.read()));\n" +
                "                 } else if(line.toLowerCase().startsWith(\"get\")) {\n" +
                "-                    logger.info (\"get called:\");\n" +
                "                     JsonReader jsonReader = new JsonReader(new StringReader(line.substring(\"get\".length())));\n" +
                "                     printVersioned(client.get(tightenNumericTypes(jsonReader.read())));\n" +
                "                 } else if(line.toLowerCase().startsWith(\"delete\")) {";
        List<String> list = Extraction.parsePatchString(patchString,-1,-1,-1);
        Assert.assertEquals(list.size(),1);
        Assert.assertEquals(list.get(0),compare);
    }

    @Test
    public void parseHunkRangeTest() throws Exception {
        String hunkString = "@@ -340,14 +356,15 @@ public class StorageService extends AbstractService {\n" +
                "                     }\n" +
                " \n" +
                "                 }\n" +
                "+            }\n" +
                "             if(voldemortConfig.isServerRoutingEnabled() && !isSlop) {\n" +
                "-                this.storeRepository.removeRoutedStore(engineName);\n" +
                "+                this.storeRepository.removeRoutedStore(storeName);\n" +
                "                 for(Node node: metadata.getCluster().getNodes())\n" +
                "                     this.storeRepository.removeNodeStore(storeName, node.getId());\n" +
                "             }\n" +
                "         }\n" +
                " \n" +
                "-        storeRepository.removeStorageEngine(engineName);\n" +
                "+        storeRepository.removeStorageEngine(storeName);\n" +
                "         if(!isView)\n" +
                "             engine.truncate();\n" +
                "         engine.close();";
        int[] ranges = Extraction.parseHunkRange(hunkString);
        Assert.assertEquals(ranges[0],340);
        Assert.assertEquals(ranges[1],14);
        Assert.assertEquals(ranges[2],356);
        Assert.assertEquals(ranges[3],15);
    }

    @Test
    public void getHunksCache() throws Exception {
        ExtractionMeta extractionMeta = new ExtractionMeta("MyVoldemort",501,800);
    }

}